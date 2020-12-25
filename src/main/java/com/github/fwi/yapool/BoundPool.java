package com.github.fwi.yapool;

import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A LIFO pool that respects a minimum and maximum size.
 * This pool must be {@link #open()}ed before it can be used. 
 * The pool keeps track of what resources are part of the pool
 * and provides methods for removing idle resources 
 * or evicting resources that are leased.
 * The pool can be flushed which removes all idle resources and evicts all leased resources. 
 * @author Fred
 */
public class BoundPool<T> extends Pool<T> {
	
	//private static final long serialVersionUID = -6944749838095746860L;

	/** A concurrent set created via a concurrent hashmap. */
	private Set<T> leased = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
	
	private AtomicInteger leasedSize = new AtomicInteger();
	/** 
	 * The amount of resources (to be) in the pool. 
	 * Used to prevent going over the maximum amount of resources allowed in the pool
	 * when deciding to create a new resource.
	 */
	private AtomicInteger resCount = new AtomicInteger();
	
	private volatile boolean syncCreation;
	private volatile int minSize = 0;
	private volatile int maxSize = 4;
	private volatile boolean opened;
	
	private final Semaphore createLock = new Semaphore(1, true);

	/** Opens the pool with {@link #getMinSize()} resources. */ 
	public void open() {
		open(minSize);
	}
	
	/** Opens the pool with the given amount of resources. */ 
	public void open(int amount) {
		
		if (isClosed()) {
			throw new IllegalStateException(getPoolName() + " pool is closed.");
		}
		if (getFactory() == null) {
			throw new IllegalStateException(getPoolName() + " pool factory is required.");
		}
		createdCount.set(0);
		int toCreate = (amount > maxSize ? maxSize : amount < minSize ? minSize : amount);
		try {
			for (int i = 0; i < toCreate; i++) {
				addIdle(create());
			}
		} catch (Exception e) {
			log.error("Could not create " + toCreate + " resource(s) while opening pool " + getPoolName() + ", created " + getIdleSize() + " resource(s).", e);
		}
		opened = true;
		fireEvent(PoolEvent.OPENED);
	}
	
	@Override
	public void close() {
		
		super.close();
		flush();
	}
	
	/**
	 * Creates a resource (in a synchronized manner if {@link #isSyncCreation()} is true), but only if pool is not full.
	 * @return null or a new resource 
	 */
	@Override 
	protected T create() {

		return create(false, true);
	}
	
	/**
	 * Creates a resource (in a synchronized manner if {@link #isSyncCreation()} is true), but only if pool is not full.
	 * @param inLeasedState if true, registers the created resource as leased
	 * @param rethrowRuntimeException if true, any factory-create RuntimeExcepions are re-thrown
	 * @return null or a new (leased) resource.
	 */
	protected T create(boolean inLeasedState, boolean rethrowRuntimeException) {

		T t = null;
		// Use local boolean in case syncCreation value changes halfway this method.
		boolean useLock = syncCreation;
		try {
			if (useLock) {
				createLock.acquire();
			}
			// check if pool is not already full
			int size = resCount.incrementAndGet();
			if (size > getMaxSize()) {
				// finally block will decrease resCount
				return null;
			}
			t = getFactory().create();
			if (t == null) {
				throwFactoryCreateFailed();
			}
			if (inLeasedState) {
				leased.add(t);
				leasedSize.incrementAndGet();
			}
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		} catch (RuntimeException e) {
			if (rethrowRuntimeException) {
				throw e;
			} else {
				log.error("Failed to create a new resource for pool " + getPoolName(), e);
			}
		} finally {
			if (t == null) {
				resCount.decrementAndGet();
			}
			if (useLock) {
				createLock.release();
			}
		}
		if (t != null) {
			createdCount.incrementAndGet();
			fireEvent(PoolEvent.CREATED, t);
		}
		return t;
	}

	/**
	 * Acquires a resource in the following manner:
	 * <br> - if pool size is below the minimum, try to create a resource (catch factory-create RuntimeException).
	 * <br> - try to acquire an idle resource if one is available at present
	 * <br> - if pool size is below the maximum, try to create a resource (catch factory-create RuntimeException)
	 * <br> - wait for an idle resource to become available
	 * <br> - if pool size is below the maximum, try to create a resource (throw factory-create RuntimeException)
	 * <br> - throw a {@link NoSuchElementException} when no resource was acquired.
	 * <br>This procedure will let the caller wait when resources can temporarily not be created
	 * (e.g. connection to database is lost for a small period). At the same time, if resources can
	 * be created again within the acquire-time, the caller will not notice that the resources were unavailable
	 * for a small period.
	 */
	@Override
	public T acquire(long acquireTimeOutMs) {
		
		if (isClosed()) {
			throw new IllegalStateException(getPoolName() + " pool is closed.");
		}
		fireEvent(PoolEvent.ACQUIRING);
		T t = null;
		try {
			// See if one is available.
			t = acquireIdle(0L);
			if (t == null) {
				// Check if a resource should be created.
				if (getSize() < getMinSize() || !isFull()) {
					// Create may take a long time if something is wrong, register start time.
					final long createStart = System.currentTimeMillis();
					// Create without throwing error, will try acquire/create again below.
					t = create(true, false);
					if (t == null) {
						// Could not create resource, wait for one to become available.
						// Substract the time it took to try to create a resource.
						final long idleAcquireTime = acquireTimeOutMs - (System.currentTimeMillis() - createStart); 
						t = acquireIdle(idleAcquireTime);
						if (t == null && idleAcquireTime > 0L && !isFull()) {
							// Try to create a resource again - resources might have been evicted while waiting for idle resource.
							// Last time create is tried, throw an error if it fails.
							t = create(true, true);
						}
						// if t is still null, an error will be thrown, see below.
					}
				} else {
					// Cannot create resource, wait for one to become available.
					t = acquireIdle(acquireTimeOutMs);
					if (t == null && !isFull()) {
						// Try to create a resource - resources might have been evicted while waiting for idle resource.
						// Throw an error if create fails.
						t = create(true, true);
					}
				}
				// Tried everything to acquire and/or create a resource within acquire-time.
				if (t == null) {
					throwAcquireTimeOut(acquireTimeOutMs);
				}
			}
			// at this point, t is not null or an error was thrown
		} finally {
			// Acquired event with t==null indicates acquired failed.
			fireEvent(PoolEvent.ACQUIRED, t);
		}
		return t;
	}
	
	@Override
	protected T acquireIdle(long acquireTimeOutMs) {
		
		T t = super.acquireIdle(acquireTimeOutMs);
		if (t != null) {
			leasedSize.incrementAndGet();
			leased.add(t);
		}
		return t;
	}
	
	/**
	 * Puts the resource back in the pool so that it can be re-used.
	 * If the resource was evicted or not leased (i.e. a foreign resource), 
	 * the resource will be destroyed (and not put back into the pool).
	 * @param t The leased resource to put back into the pool.
	 * @return null if the resource was not put back in the pool.
	 */
	@Override
	public T release(T t) {
		
		boolean removed = leased.remove(t); 
		if (removed) {
			super.release(t);
			leasedSize.decrementAndGet();
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Released resource is not part of pool " + getPoolName() + ": " + t);
				// This is common when pool was flushed. 
			}
			// Prevent resource leakage - always destroy resource.
			destroy(t);
		}
		return (removed ? t : null);
	}
	
	/**
	 * Removes the last/longest waiting resource in the idle-queue and destroys it, if any. 
	 * @return the resource removed from the idle-queue or null.
	 */
	protected T removeIdle(boolean fireIdledEvent) {
		
		return removeIdle(null, fireIdledEvent);
	}
	
	/**
	 * Tries to remove the given resource from the idle-queue.
	 * On success, destroys the resource.
	 * @param t the resource to remove from the idle-queue. 
	 * If null, last resource in queue is removed (which is longest in the idle-queue). 
	 * @return null if the resource could not be removed from the idle-queue, else the given resource.
	 */
	protected T removeIdle(T t, boolean fireIdledEvent) {
		
		try {
			if (!idle.tryAcquire(0, TimeUnit.SECONDS)) {
				// idle-queue is empty
				return null;
			}
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
		// we now have a permit to remove a resouce from the idle-queue 
		T removed = null;
		if (t == null) {
			// remove resource longest in queue
			removed = idleQueue.pollLast();
			if (removed == null) {
				// we have a permit but no resource in the queue --> something is very wrong.
				log.error("Pool " + getPoolName() + " out of sync, idle count: " + getIdleSize() + ", idle in queue: " + idleQueue.size());
			}
		} else {
			if (idleQueue.remove(t)) {
				removed = t;
			} else {
				// resource was no longer in the queue (was probably acquired)
				// nothing removed from idle-queue so release the permit we got earlier.
				idle.release();
			}
		}
		if (removed != null) {
			resCount.decrementAndGet();
			if (log.isDebugEnabled()) {
				log.debug("Removed idle resource from pool " + getPoolName() + ": " + removed);
			}
			if (fireIdledEvent) {
				fireEvent(PoolEvent.IDLE_EXPIRED, removed);
			}
			destroy(removed);
		}
		return removed;
	}
	
	/**
	 * Evicts the leased resource from the pool.
	 * Does not fire an {@link PoolEvent#LEASE_EXPIRED} event.
	 * @param destroy if true, the evicted resource is destroyed. If false, 
	 * make sure to destroy/close the resource yourself when appropriate.
	 * Note that an evicted resource that is (eventually) released to the pool, is always detroyed by the pool
	 * (and not put back into the pool).  
	 * @param t The leased resource to evict.
	 * @return true if the resource was found in the leased resources list and evicted.
	 */
	public boolean evictLeased(T t, boolean destroy) {
		return (removeLeased(t, destroy, false) != null);
	}
	
	/**
	 * Evicts the idle resource from the pool and destroys it.
	 * Does not fire an {@link PoolEvent#IDLE_EXPIRED} event.
	 * @param t The idle resource to evict.
	 * @return true if the resource was found in the idle resources list and evicted.
	 */
	public boolean evictIdle(T t) {
		return (removeIdle(t, false) != null);
	}

	/**
	 * Removes a leased resource from the pool.
	 * @param t The leased resource to remove
	 * @param destroyEvicted If true, the removed resource is also destroyed.
	 * @param fireLeaseExpiredEvent If true, a {@link PoolEvent#LEASE_EXPIRED} is fired.
	 * @return null if the leased resource could not be removed from the pool, else t. 
	 */
	protected T removeLeased(T t, boolean destroyEvicted, boolean fireLeaseExpiredEvent) {
		
		boolean removed = leased.remove(t); 
		if (removed) {
			resCount.decrementAndGet();
			leasedSize.decrementAndGet();
			if (log.isDebugEnabled()) {
				log.debug("Evicted resource from pool " + getPoolName() + ": " + t);
			}
			if (fireLeaseExpiredEvent) {
				fireEvent(PoolEvent.LEASE_EXPIRED, t);
			}
			if (destroyEvicted) {
				destroy(t);
			}
		}
		return (removed ? t : null);
	}
	
	/** 
	 * Calls the factory to destroy the resource and fires a destroy-event.
	 * Method is called when resource is removed from pool. 
	 */
	protected void destroy(T t) {
		
		fireEvent(PoolEvent.DESTROYING, t);
		try {
			getFactory().destroy(t);
		} catch (Exception e) {
			log.error(getPoolName() + " pool factory " + getFactory().getClass().getSimpleName() + " failed to destroy resource " + t, e);
		}
	}

	/** 
	 * Removes all idle and leased resources from the pool.
	 * Idle resources are destroyed, leased resource that are evicted are not destroyed
	 * (but will be when they are released to the pool).
	 * @return all resources removed from the pool.
	 */
	public Set<T> flush() {
		
		Set<T> s = evictAll(false);
		s.addAll(drainIdle());
		return s;
	}
	
	protected Set<T> evictAll(boolean destroyEvicted) {
		
		HashSet<T> s = new HashSet<T>();
		for (T t : leased) {
			if (removeLeased(t, destroyEvicted, false) != null) {
				s.add(t);
			}
		}
		return s;
	}
	
	protected Set<T> drainIdle() {
		
		HashSet<T> s = new HashSet<T>();
		T t;
		while ((t = removeIdle(false)) != null) {
			s.add(t);
		}
		return s;
	}
	
	/**
	 * Throws a {@link NoSuchElementException} with message 
	 * "Could not acquire resource from pool poolName within time-out"
	 */
	public void throwAcquireTimeOut(long acquireTimeOutMs) {
		throw new NoSuchElementException("Could not acquire resource from pool " + getPoolName() + " within " + acquireTimeOutMs + " ms.");
	}
	
	/* *** bean methods *** */

	public int getMinSize() { 
		return minSize; 
	}

	public void setMinSize(int minSize) {
		if (minSize >= 0) {
			this.minSize = minSize;
			if (maxSize < minSize) maxSize = minSize;
		}
	}
	
	public int getMaxSize() { 
		return maxSize; 
	}
	
	public void setMaxSize(int maxSize) {
		if (maxSize > 0) {
			this.maxSize = maxSize;
			if (minSize > maxSize) minSize = maxSize;
		}
	}

	public int getLeasedSize() {
		return leasedSize.get();
	}
	
	public boolean isEmpty() {
		return resCount.get() < 1;
	}

	public int getSize() { 
		return Math.min(resCount.get(), getMaxSize()); 
	}

	public boolean isFull() {
		return resCount.get() >= getMaxSize();
	}
	
	public boolean isOpen() { 
		return opened; 
	}  

	public boolean isSyncCreation() {
		return syncCreation;
	}

	/**
	 * If true, new pool resources are created one by one.
	 * If false, multiple pool resources can be created at the same time.
	 * Default is false.
	 */
	public void setSyncCreation(boolean syncCreation) {
		this.syncCreation = syncCreation;
	}
	
}
