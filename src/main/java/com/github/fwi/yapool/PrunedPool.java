package com.github.fwi.yapool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link BoundPool} that can actively remove idled and expired resources from the pool (see {@link #prune()}).
 * To prune this pool regularly, register this pool using {@link PoolPruner#add(PrunedPool)}
 * (an instance of {@link PoolPruner} is available via {@link PoolPruner#getInstance()}). 
 * <br>Various actions can be taken when a resource idled/expired, see the various set-methods of this class.
 * For additional debugging, consider the use of the {@link com.github.fwi.yapool.listener.LeaserAcquiredTrace} listener.
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
	/** 1 800 000 milliseconds (30 minutes) */
	public static final long DEFAULT_MAX_LIFE_TIME = 1800000L;

	private PruneTask pruneTask;
	private AtomicLong pruneIntervalMs = new AtomicLong(DEFAULT_PRUNE_INTERVAL);
	private AtomicLong maxIdleTimeMs = new AtomicLong(DEFAULT_MAX_IDLE_TIME);
	private AtomicLong maxLeaseTimeMs = new AtomicLong(DEFAULT_MAX_LEASE_TIME);
	private AtomicLong maxLifeTimeMs = new AtomicLong(DEFAULT_MAX_LIFE_TIME);
	protected AtomicLong idledCount = new AtomicLong();
	protected AtomicLong expiredCount = new AtomicLong();
	protected AtomicLong invalidCount = new AtomicLong();
	protected AtomicLong lifeEndCount = new AtomicLong();

	protected ConcurrentHashMap<T, Long> lifeTimeEnd = new ConcurrentHashMap<T, Long>();
	protected ConcurrentHashMap<T, Long> idleTimeStart = new ConcurrentHashMap<T, Long>();
	protected ConcurrentHashMap<T, Long> leaseTimeEnd = new ConcurrentHashMap<T, Long>();
	protected ConcurrentHashMap<T, Thread> leasers = new ConcurrentHashMap<T, Thread>();
	
	private volatile boolean logLeaseExpiredTrace;
	private volatile boolean logLeaseExpiredTraceAsWarn;
	private volatile boolean logLeaseExpiredTraceAsError;
	private volatile boolean interruptLeaser;
	private volatile boolean destroyOnExpiredLease;

	@Override
	public void open(int amount) {
		
		if (getMaxLifeTimeMs() > 0L) {
			if (getMaxIdleTimeMs() > getMaxLifeTimeMs()) {
				log.warn(getPoolName() + " Max. life time (" + getMaxLifeTimeMs() + " ms.) must be greater than max. idle time (" 
						+ getMaxIdleTimeMs() + " ms.), setting max. life time to two times max. idle time.");
				setMaxLifeTimeMs(2 * getMaxIdleTimeMs());
			}
			if (getMaxLeaseTimeMs() > getMaxLifeTimeMs()) {
				log.warn(getPoolName() + " Max. life time (" + getMaxLifeTimeMs() + " ms.) must be greater than max. lease time (" 
						+ getMaxLeaseTimeMs() + " ms.), setting max. life time to two times max. lease time.");
				setMaxLifeTimeMs(2 * getMaxLeaseTimeMs());
			}
		}
		super.open(amount);
		idledCount.set(0);
		expiredCount.set(0);
		invalidCount.set(0);
		lifeEndCount.set(0);
		if (pruneTask != null) {
			pruneTask.start();
		}
	}

	@Override 
	protected T create(boolean inLeasedState, boolean rethrowRuntimeException) {
		
		T t = super.create(inLeasedState, rethrowRuntimeException);
		if (t != null && getMaxLifeTimeMs() > 0L) {
			lifeTimeEnd.put(t, System.currentTimeMillis() + getMaxLifeTimeMs());
		}
		return t;
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
	 * @param maxLeasedTimeMs if 0 or less, lease time never exprires.
	 */
	public T acquire(long acquireTimeOutMs, long maxLeasedTimeMs) {
		
		long timeout = acquireTimeOutMs;
		long tend = System.currentTimeMillis() + timeout;
		T t = null;
		do {
			t = super.acquire(timeout); // will throw NoSuchElementException when none is available within timeout.
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
		leaseTimeEnd.put(t, (maxLeasedTimeMs < 1L ? 0L : System.currentTimeMillis() + maxLeasedTimeMs));
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
		lifeTimeEnd.remove(t);
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
	 * or are leased for {@link #getMaxLeaseTimeMs()} or have passed the life time ({@link #getMaxLifeTimeMs()}).
	 */
	public void prune() {
		
		if (log.isTraceEnabled()) {
			log.trace("Pruning pool " + getPoolName() 
					+ " (max. idle: " + getMaxIdleTimeMs() 
					+ ", max. lease: " + getMaxLeaseTimeMs()
					+ ", max. life: " + getMaxLifeTimeMs() + ")");
		}
		try {
			checkIdleTime();
			int removed = checkLeaseTime();
			removed += checkLifeTime();
			if (removed > 0) {
				// If the factory cannot create resources (e.g. database unavailable), no resources are evicted (eventually).
				// In this case, do not ensure minimum size because that will just show a lot of error messages.
				// Only ensure minimum size when prune-task has removed connections.
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
		
		if (getMaxIdleTimeMs() < 1L || getSize() <= getMinSize()) {
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
					// no need for further cleanup, destroy() is called for removed idle resource,
					// which in turn removes all references (calls removeReferences).
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
		int evictedResourcesCount = 0;
		for (T t : leaseTimeEnd.keySet()) {
			Long leaseEnd = leaseTimeEnd.get(t);
			if (leaseEnd == null || leaseEnd < 1L || now <= leaseEnd) {
				continue;
			}
			Thread user = leasers.get(t);
			// if user is interrupted, first get stack trace from user and log it.
			if (isInterruptLeaser()) {
				logExpiredTrace(t, user);
			}
			if (removeLeased(t, isDestroyOnExpiredLease(), true) == null) {
				continue;
			}
			removeReferences(t);
			evictedResourcesCount++;
			expiredCount.incrementAndGet();
			if (isInterruptLeaser()) {
				// if leaser was interrupted, stack trace logging was already done.
				if (user != null) {
					try {
						if (!user.isInterrupted()) {
							user.interrupt();
						}
					} catch (Exception e) {
						log.warn(getPoolName() + " Failed to interrupt thread " + t + " for leasing resource [" + t + "] for too long.");
					}
				}
			} else {
				logExpiredTrace(t, user);
			}
		} // for leaseTimeEnd
		return evictedResourcesCount;
	}
	
	/**
	 * Tries to remove resources from the pool for which life time ended ({@link #getMaxLifeTimeMs()}).
	 * @return amount of removed resources
	 */
	protected int checkLifeTime() {
		
		if (getMaxLifeTimeMs() < 1L) {
			return 0;
		}
		long now = System.currentTimeMillis();
		int evictedResourcesCount = 0;
		for (T t : lifeTimeEnd.keySet()) {
			Long lifeEnd = lifeTimeEnd.get(t);
			if (lifeEnd == null || lifeEnd < 1L || now <= lifeEnd) {
				continue;
			}
			boolean wasLeased = false;
			boolean wasIdle = (removeIdle(t, false) != null);
			if (!wasIdle) {
				wasLeased = (removeLeased(t, false, false) != null);
			}
			if (wasIdle || wasLeased) {
				if (wasLeased) {
					// prevent memory leaks. 
					// if resource was idle, removeReferences is called via destroy().
					removeReferences(t);
				}
				evictedResourcesCount++;
				lifeEndCount.incrementAndGet();
				if (log.isDebugEnabled()) {
					log.debug("Removed " + (wasIdle ? "idle" : "leased") + " resource [" + t + "] from pool " + getPoolName() + " after life time ended.");
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Failed to remove resource [" + t + "] from pool " + getPoolName() + " after life time ended, will retry on next prune cycle.");
				}
			}
		} // for lifeTimeEnd
		return evictedResourcesCount;
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
		String logMsg = sb.toString();
		if (logLeaseExpiredTraceAsError) {
			log.error(logMsg);
		} else if (logLeaseExpiredTraceAsWarn) {
			log.warn(logMsg);
		} else if (isLogLeaseExpiredTrace()) {
			log.info(logMsg);
		} else {
			log.debug(logMsg);
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
	 * @param maxIdleTimeMs if 0 or less, idle time never expires.
	 */
	public void setMaxIdleTimeMs(long maxIdleTimeMs) {
		this.maxIdleTimeMs.set(maxIdleTimeMs);
	}

	public long getMaxLeaseTimeMs() {
		return maxLeaseTimeMs.get();
	}

	/**
	 * A resource that has been leased for too long, is removed from the pool.
	 * @param maxLeaseTimeMs if 0 or less, lease time never expires.
	 */
	public void setMaxLeaseTimeMs(long maxLeaseTimeMs) {
		this.maxLeaseTimeMs.set(maxLeaseTimeMs);
	}
	
	public long getMaxLifeTimeMs() {
		return maxLifeTimeMs.get();
	}

	/**
	 * A resource that has been in use for too long, is removed from the pool.
	 * <br>The main reason for regurarly refreshing all resources is to prevent (subtle) memory leaks. 
	 * @param maxLifeTimeMs if 0 or less, life time never expires.
	 */
	public void setMaxLifeTimeMs(long maxLifeTimeMs) {
		this.maxLifeTimeMs.set(maxLifeTimeMs);
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

	public long getLifeEndCount() {
		return lifeEndCount.get();
	}

}
