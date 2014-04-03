package nl.fw.yapool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link BoundPool} that can actively remove idled and expired resources from the pool (see {@link #prune()}).
 * To prune this pool regularly, register this pool using {@link PoolPruner#add(PrunedPool)}
 * (an instance of {@link PoolPruner} is available via {@link PoolPruner#getInstance()}). 
 * <br>Various actions can be taken when a resource idled/expired, see the various set-methods of this class.
 * For additional debugging, consider the use of the {@link nl.fw.yapool.listener.LeaserAcquiredTrace} listener.
 * @author fwiers
 *
 * @param <T>
 */
public class PrunedPool<T> extends BoundPool<T> {

	/** 1000 milliseconds (1 second) */
	public static final long DEFAULT_PRUNE_INTERVAL = 1000L;
	/** 60 000 milliseconds (1 minute) */
	public static final long DEFAULT_MAX_IDLE_TIME = 60000L;
	/** 120 000 milliseconds (2 minutes) */
	public static final long DEFAULT_MAX_LEASE_TIME = 120000L;

	private PruneTask pruneTask;
	private AtomicLong pruneIntervalMs = new AtomicLong(DEFAULT_PRUNE_INTERVAL);
	private AtomicLong maxIdleTimeMs = new AtomicLong(DEFAULT_MAX_IDLE_TIME);
	private AtomicLong maxLeaseTimeMs = new AtomicLong(DEFAULT_MAX_LEASE_TIME);
	protected AtomicLong idledCount = new AtomicLong();
	protected AtomicLong expiredCount = new AtomicLong();
	protected AtomicLong invalidCount = new AtomicLong();

	protected ConcurrentHashMap<T, Long> idleTimeStart = new ConcurrentHashMap<T, Long>();
	protected ConcurrentHashMap<T, Long> leaseTimeEnd = new ConcurrentHashMap<T, Long>();
	protected ConcurrentHashMap<T, Thread> leasers = new ConcurrentHashMap<T, Thread>();
	
	private volatile boolean logLeaseExpiredTrace;
	private volatile boolean logLeaseExpiredTraceAsWarn;
	private volatile boolean logLeaseExpiredTraceAsError;
	private volatile boolean interruptLeaser;
	private volatile boolean destroyOnExpiredLease;
		
	public void open(int amount) {
		super.open(amount);
		idledCount.set(0);
		expiredCount.set(0);
		invalidCount.set(0);
		if (pruneTask != null) {
			pruneTask.start();
		}
	}
	
	/**
	 * Calls {@link #acquire(long, long)} with {@link #getMaxLeaseTimeMs()}.
	 */
	@Override
	public T acquire(long acquireTimeOutMs) {
		return acquire(acquireTimeOutMs, getMaxLeaseTimeMs());
	}

	/**
	 * Calls {@link BoundPool#acquire(long)} but also validates the returned resource.
	 * If validation fails, the resource is evicted from the pool 
	 * and {@link BoundPool#acquire(long)} is called again, if there is any acquire-time left. 
	 * @param maxLeasedTimeMs if 0, lease time never exprires.
	 */
	public T acquire(long acquireTimeOutMs, long maxLeasedTimeMs) {
		
		long timeout = acquireTimeOutMs;
		long tend = System.currentTimeMillis() + timeout;
		T t = null;
		do {
			t = super.acquire(timeout); // will throw NoSuchElementException when none is available within timeout.
			idleTimeStart.remove(t);
			if (!isValid(t)) {
				invalidCount.incrementAndGet();
				fireEvent(PoolEvent.INVALID, t);
				removeLeased(t, true, false);
				t = null;
				timeout = tend - System.currentTimeMillis();
			}
		} while (t == null);
		if (logLeaseExpiredTrace) {
			leasers.put(t, Thread.currentThread());
		}
		leaseTimeEnd.put(t, (maxLeasedTimeMs == 0L ? 0L : System.currentTimeMillis() + maxLeasedTimeMs));
		return t;
	}
	
	/** Call factory isValid method within a try-catch block. */
	protected boolean isValid(T t) {
		
		boolean valid = false;
		try {
			valid = getFactory().isValid(t);
		} catch (Exception e) {
			log.error("Pool factory " + getFactory().getClass().getSimpleName() + " for pool " + getPoolName() + " failed to validate resource " + t, e);
		}
		return valid;
	}
	
	@Override
	protected void addIdle(T t) {
	
		if (t != null) {
			idleTimeStart.put(t, System.currentTimeMillis());
			super.addIdle(t);
		}
	}

	@Override
	public T release(T t) {
		
		T released = super.release(t);
		if (released != null) {
			leaseTimeEnd.remove(t);
			leasers.remove(t);
			idleTimeStart.put(t, System.currentTimeMillis());
		}
		return released;
	}
	
	@Override
	public boolean evictLeased(T t, boolean destroy) {
		
		boolean evicted = false;
		if (super.evictLeased(t, destroy)) {
			evicted = true;
			// If the resource was not destroyed, there is a chance of memory leakage.
			// Therefor, remove all references to the resource now.
			if (!destroy) {
				removeReferences(t);
			}
		}
		return evicted;
	}

	@Override
	protected void destroy(T t) {
		
		removeReferences(t);
		super.destroy(t);
	}
	
	/**
	 * Removes all references to the resource in maps that keep track of the resource.
	 * @param t The resource to remove from prune administration.
	 */
	protected void removeReferences(T t) {
		
		idleTimeStart.remove(t);
		leaseTimeEnd.remove(t);
		leasers.remove(t);
	}

	@Override
	public void close() {
		
		if (pruneTask != null) {
			pruneTask.stop();
		}
		super.close();
	}

	/**
	 * Removes resources from the pool that idled for {@link #getMaxIdleTimeMs()} 
	 * or are leased for {@link #getMaxLeaseTimeMs()}.
	 */
	public void prune() {
		
		if (log.isTraceEnabled()) {
			log.trace("Pruning pool " + getPoolName() + " (max. idle: " + getMaxIdleTimeMs() + ", max. lease: " + getMaxLeaseTimeMs() + ")");
		}
		try {
			checkIdleTime();
			if (checkLeaseTime() > 0) {
				// If the factory cannot create resources (e.g. database unavailable), no resources are evicted (eventually).
				// In this case, do not ensure minimum size because that will just show a lot of error messages.
				ensureMinSize();
			}
		} catch (Exception e) {
			log.error("Pruning pool " + getPoolName() + " failed.", e);
		}
		if (log.isTraceEnabled()) {
			log.trace("Finished pruning pool " + getPoolName() + ".");
		}
	}
	
	/**
	 * Removes resources from the pool that idled for {@link #getMaxIdleTimeMs()},
	 * but only if pool size is larger than minimum pool size.
	 * @return amount of an idle resources removed
	 */
	protected int checkIdleTime() {
		
		if (getMaxIdleTimeMs() == 0L || getSize() <= getMinSize()) {
			return 0;
		}
		long now = System.currentTimeMillis();
		T t = null;
		boolean done = false;
		int removedCount = 0;
		while (!done && (t = idleQueue.peekLast()) != null) {
			done = true;
			Long idleStart = idleTimeStart.get(t);
			if (idleStart != null 
					&& now - idleStart > getMaxIdleTimeMs()
					&& getSize() > getMinSize()) {
				t = removeIdle(true);
				if (t != null) {
					removedCount++;
					idledCount.incrementAndGet();
					done = false;
				}
			}
		}
		return removedCount;
	}

	/**
	 * Removes resources from the pool that are leased for {@link #getMaxLeaseTimeMs()}.
	 * A leaser may be interrupted (see {@link #isInterruptLeaser()})
	 * and a stack trace may be logged (see {@link PrunedPool#isLogLeaseExpiredTrace()}).
	 * <br>The removed resource is destroyed if {@link #isDestroyOnExpiredLease()} is true.
	 * @return amount of evicted resources
	 */
	protected int checkLeaseTime() {
		
		long now = System.currentTimeMillis();
		int evictedCount = 0;
		for (T t : leaseTimeEnd.keySet()) {
			Long leaseEnd = leaseTimeEnd.get(t);
			if (leaseEnd == null || leaseEnd == 0L || now <= leaseEnd) {
				continue;
			}
			Thread user = leasers.get(t);
			// if user is interrupted, first get stack trace from user and log it.
			if (isInterruptLeaser()) {
				logExpiredTrace(t, user);
				if (user != null) {
					user.interrupt();
				}
			}
			T evicted = removeLeased(t, destroyOnExpiredLease, true);
			if (evicted != null) {
				evictedCount++;
				// if leaser was interrupted, stack trace logging was already done.
				expiredCount.incrementAndGet();
				if (!isInterruptLeaser()) {
					logExpiredTrace(evicted, user);
				}
			}
		}
		return evictedCount;
	}
	
	/**
	 * Try to create new idle resources until minimum size is reached.
	 * Any errors are catched and logged.
	 * <br>The pool pruner uses this method when it detects leases have expired. 
	 * <br>This method may be called by the application using the pool when, for example,
	 * the application detects a database has restarted.
	 * In this case all invalid connections are automatically removed by the pool,
	 * but new connections are only created as needed (i.e. pool remains below minimum size for a while).
	 */
	public void ensureMinSize() {
		
		try {
			while (getSize() < getMinSize()) {
				T t = create();
				if (t == null) {
					// Should not happen, but to be safe.
					break;
				}
				addIdle(t);
				if (log.isTraceEnabled()) {
					log.trace("Added new resource to pool " + getPoolName() + " to ensure minimum size (" + getMinSize() + "/" + getSize() + ")");
				}
			}
		} catch (Exception e) {
			log.error("Failed to grow pool " + getPoolName() + " to minimum size " + getMinSize() + " (current size is " + getSize() + ")", e);
		}
	}
	
	protected void logExpiredTrace(T t, Thread user) {
		
		if (!isLogLeaseExpiredTrace()) return;
		StringBuilder sb = new StringBuilder("Evicting resource from pool " + getPoolName() + " after lease time has expired");
		if (user != null && isInterruptLeaser()) {
			sb.append(", resource leaser will be interrupted.");
		} else {
			sb.append(".");
		}
		sb.append("\nResource: ").append(t.toString());
		if (user == null) {
			sb.append("\nResource leaser is unknown.");
		} else {
			sb.append("\nStack trace from leaser: ").append(user.toString());
			StackTraceElement[] tstack = user.getStackTrace();
			if (tstack == null || tstack.length == 0) {
				sb.append("\nNo stack trace from leaser available.");
			} else {
				for (StackTraceElement st : tstack) {
					sb.append("\n\t").append(st.toString());
				}
			}
		}
		if (logLeaseExpiredTraceAsError) {
			log.error(sb.toString());
		} else if (logLeaseExpiredTraceAsWarn) {
			log.warn(sb.toString());
		} else {
			log.info(sb.toString());
		}
	}

	/* *** bean methods *** */

	public long getPruneIntervalMs() {
		return pruneIntervalMs.get();
	}

	/**
	 * Prune interval in milliseconds at witch prune tasks are scheduled.
	 * @param pruneIntervalMs if less than zero, the pool will NOT be registered with the PoolPruner.
	 */
	public void setPruneIntervalMs(long pruneIntervalMs) {
		this.pruneIntervalMs.set(pruneIntervalMs);
	}
	
	public void setPruneTask(PruneTask pruneTask) {
		
		if (this.pruneTask != null) {
			this.pruneTask.stop();
		}
		this.pruneTask = pruneTask;
	}
	
	public PruneTask getPruneTask() {
		return pruneTask;
	}

	public long getMaxIdleTimeMs() {
		return maxIdleTimeMs.get();
	}

	/**
	 * A resource that has been idle for too long, is removed from the pool.
	 * @param maxIdleTimeMs if 0, idle time never expires.
	 */
	public void setMaxIdleTimeMs(long maxIdleTimeMs) {
		if (maxIdleTimeMs >= 0L) {
			this.maxIdleTimeMs.set(maxIdleTimeMs);
		}
	}

	public long getMaxLeaseTimeMs() {
		return maxLeaseTimeMs.get();
	}

	/**
	 * A resource that has been leased for too long, is removed from the pool.
	 * @param maxLeaseTimeMs if 0, lease time never expires.
	 */
	public void setMaxLeaseTimeMs(long maxLeaseTimeMs) {
		if (maxLeaseTimeMs >= 0L) {
			this.maxLeaseTimeMs.set(maxLeaseTimeMs);
		}
	}
	
	public boolean isLogLeaseExpiredTrace() {
		return logLeaseExpiredTrace;
	}

	public void setLogLeaseExpiredTrace(boolean logLeaseExpiredTrace) {
		this.logLeaseExpiredTrace = logLeaseExpiredTrace;
	}

	public boolean isLogLeaseExpiredTraceAsWarn() {
		return logLeaseExpiredTraceAsWarn;
	}

	public void setLogLeaseExpiredTraceAsWarn(boolean logLeaseExpiredTraceAsWarn) {
		this.logLeaseExpiredTraceAsWarn = logLeaseExpiredTraceAsWarn;
		if (logLeaseExpiredTraceAsWarn) setLogLeaseExpiredTrace(true);
	}

	public boolean isLogLeaseExpiredTraceAsError() {
		return logLeaseExpiredTraceAsError;
	}

	public void setLogLeaseExpiredTraceAsError(boolean logLeaseExpiredTraceAsError) {
		this.logLeaseExpiredTraceAsError = logLeaseExpiredTraceAsError;
		if (logLeaseExpiredTraceAsError) setLogLeaseExpiredTrace(true);
	}

	public boolean isInterruptLeaser() {
		return interruptLeaser;
	}

	/**
	 * If set to true, the thread that leased a resource for too long is interrupted.
	 */
	public void setInterruptLeaser(boolean interruptLeaser) {
		this.interruptLeaser = interruptLeaser;
		if (interruptLeaser) {
			setLogLeaseExpiredTraceAsError(true);
		}
	}

	public boolean isDestroyOnExpiredLease() {
		return destroyOnExpiredLease;
	}

	/**
	 * If set to true, a resource that is removed from the pool after it was leased for too long,
	 * will be destroyed by the resource-factory.
	 * <br>Default set to false because this can also destroy a a resource that is still in use
	 * (an expired lease indicates something is wrong, but the resource could still be in use).
	 * And also because expired resources that are eventually released to the pool,
	 * are always closed by the pool factory. 
	 * <br>Set to true if all resources really need to be destroyed/closed by the factory
	 * in a timely manner, and expired leases are frequent.
	 */
	public void setDestroyOnExpiredLease(boolean destroyOnExpiredLease) {
		this.destroyOnExpiredLease = destroyOnExpiredLease;
	}
	
	public long getIdledCount() {
		return idledCount.get();
	}
	
	public long getExpiredCount() {
		return expiredCount.get();
	}

	public long getInvalidCount() {
		return invalidCount.get();
	}

}
