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
	protected void destroy(T t) {
		
		idleTimeStart.remove(t);
		leaseTimeEnd.remove(t);
		leasers.remove(t);
		super.destroy(t);
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
			checkLeaseTime();
		} catch (Exception e) {
			log.error("Pruning pool " + getPoolName() + " failed.", e);
		}
		if (log.isTraceEnabled()) {
			log.trace("Finished pruning pool " + getPoolName() + ".");
		}
	}
	
	/**
	 * Removes resources from the pool that idled for {@link #getMaxIdleTimeMs()}.
	 */
	protected void checkIdleTime() {
		
		if (getMaxIdleTimeMs() == 0L) return;
		if (getSize() <= getMinSize()) return;
		long now = System.currentTimeMillis();
		T t = null;
		boolean done = false;
		while (!done && (t = idleQueue.peekLast()) != null) {
			done = true;
			Long idleStart = idleTimeStart.get(t);
			if (idleStart != null && now - idleStart > getMaxIdleTimeMs()) {
				t = removeIdle(true);
				if (t != null) {
					idledCount.incrementAndGet();
					done = false;
				}
			}
		}
	}

	/**
	 * Removes resources from the pool that are leased for {@link #getMaxLeaseTimeMs()}.
	 * A leaser may be interrupted (see {@link #isInterruptLeaser()})
	 * and a stack trace may be logged (see {@link PrunedPool#isLogLeaseExpiredTrace()}).
	 */
	protected void checkLeaseTime() {
		
		long now = System.currentTimeMillis();
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
				// if leaser was interrupted, stack trace logging was already done.
				expiredCount.incrementAndGet();
				if (!isInterruptLeaser()) {
					logExpiredTrace(evicted, user);
				}
			}
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

	public void setPruneIntervalMs(long pruneIntervalMs) {
		if (pruneIntervalMs >= 0L) {
			this.pruneIntervalMs.set(pruneIntervalMs);
		}
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

	public void setInterruptLeaser(boolean interruptLeaser) {
		this.interruptLeaser = interruptLeaser;
		if (interruptLeaser) {
			setLogLeaseExpiredTraceAsError(true);
		}
	}

	public boolean isDestroyOnExpiredLease() {
		return destroyOnExpiredLease;
	}

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
