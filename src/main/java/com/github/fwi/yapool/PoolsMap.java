package com.github.fwi.yapool;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "pool-of-pools" a.k.a. pools-squared.
 * <br>A PoolMap <b>must</b> be {@link #open()}ed before it can be used (and {@link #close()}d when no longer needed).
 * <br>This type of pool is typically used when one type of resource is used in different configurations.
 * An example of this is connections to different servers where each connection to a particular server
 * is stored in a different pool. This prevents overloading one server with too many connections.
 * <br>Note that for HTTP-type connections, existing solutions already exist and should be used
 * within their respective framework (e.g. <a href="https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html">
 * Apache HTTPClient</a> and <a href="https://square.github.io/okhttp/connections/">OkHttp</a>).
 * A similar case exists for database connections, e.g. use <a href="https://github.com/brettwooldridge/HikariCP">HikariCP</a>.
 * <p>
 * A pool-key is used to identify the pool to use for a pool-resource.
 * The pool-key <b>MUST</b> have proper {@link Object#hashCode()} and {@link Object#equals(Object)} methods implemented.
 * <br>The pool-key serves a dual purpose:
 * <br>  - it is used to identify the pool with properly configured resources.
 * <br>  - it is used as configuration for a new pool for the resources.
 * <br>For the latter, an {@link IPoolsMapFactory} is required.
 * 
 * @param <T> The type of pool resource.
 * @param <K> The type of pool key.
 */
public class PoolsMap<T, K> {
	
	/** 30 000 milliseconds (half a minute) */
	public static final long DEFAULT_CLEAN_INTERVAL = 30_000L;

	private AtomicLong cleanIntervalMs = new AtomicLong(DEFAULT_CLEAN_INTERVAL);

	private final Logger log = LoggerFactory.getLogger(getClass());

	private String poolsName = getClass().getSimpleName() + "[" + hashCode() + "]";

	protected final ConcurrentHashMap<K, PoolsMapPool<T>> poolsMap = new ConcurrentHashMap<>();
	protected final ReentrantLock poolCreateLock = new ReentrantLock();
	protected final IPoolsMapFactory<T, K> poolsFactory;
	protected final PoolPruner poolsPruner;
    private volatile boolean opened;
	protected volatile boolean closed;
    private ScheduledExecutorService executor;
    private boolean shutdownExecutor;
    private PoolsMapCleanTask cleanTask;

	/*
	 * Implementation notes.
	 * The difficult part is in removing pools from the poolMap that are empty and no longer used.
	 * Determining if a pool is empty is trivial, but determining if a pool is used is harder.
	 * For pool-usage detection, each pool is related to a read/write lock.
	 * Whenever a pool is newly created or used in acquiring a resource, a read-lock is set.
	 * The clean-method will try to acquire a write-lock for a pool 
	 * and this will always fail when a read-lock is in use.
	 * In this manner, removing a pool that is used is prevented
	 * and only truly unused pools are cleaned (removed) from the poolMap.
	 * After acquiring a read-lock in the acquire-method, a check is performed to see if the pool is closed.
	 * If this is the case, the clean-method just removed the pool from the poolMap
	 * and a new pool (for the poolKey) should be created.  
	 */
    
	/**
	 * Calls {@link #PoolsMap(IPoolsMapFactory, PoolPruner)} with the default {@link PoolPruner#getInstance()}.
	 */
    public PoolsMap(IPoolsMapFactory<T, K> poolsFactory) {
		this(poolsFactory, PoolPruner.getInstance());
	}

	/**
	 * @param poolsFactory The factory to create new pools (required).
	 * @param poolPruner The pruner of pools (required).
	 */
	public PoolsMap(IPoolsMapFactory<T, K> poolsFactory, PoolPruner poolPruner) {
		super();
		this.poolsFactory = poolsFactory;
		this.poolsPruner = poolPruner;
	}
	
	/**
	 * Open this pools-map for usage. Creates a {@link ScheduledThreadPoolExecutor}
	 * if no {@link #setExecutor(ScheduledExecutorService)} was set.
	 * @return this instance.
	 */
	public synchronized PoolsMap<T, K> open() {

		if (isOpened()) {
			return this;
		}
		if (getExecutor() == null) {
			// opened must be false for these methods to work
			ScheduledThreadPoolExecutor stp = new ScheduledThreadPoolExecutor(1);
			stp.setRemoveOnCancelPolicy(true);
			setExecutor(stp);
			setShutdownExecutor(true);
			log.trace("[{}] Created scheduled executor.", getPoolsName());
		}
		opened = true;
		closed = false;
		poolsMap.clear();
		cleanTask = new PoolsMapCleanTask(this);
		cleanTask.schedule();
		log.debug("[{}] Pools map opened.", getPoolsName());
		return this;
	}

	/** 
	 * Acquires a resource from the pool within the maximum acquire time (default 0), 
	 * unless the pool is closed (throws an {@link IllegalStateException}). 
	 * @return A resource for usage. This resource should be released after usage (see {@link #release(Object, Object)}).
	 * <br><b>Throws</b> a {@link RuntimeException} when no resource could be created.
	 * <br><b>Throws</b> a {@link NoSuchElementException} when no resource is available within the maximum acquire time.
	 */
	public T acquire(K poolKey) {
		return acquire(poolKey, -1L);
	}

	public T acquire(K poolKey, long acquireTimeOutMs) {
		return acquire(poolKey, acquireTimeOutMs, -1L);
	}

	public T acquire(K poolKey, long acquireTimeOutMs, long maxLeasedTimeMs) {
		
		ensureNotClosed();
		PoolsMapPool<T> lockPool = null;
		while (lockPool == null) {
			lockPool = poolsMap.get(poolKey);
			if (lockPool == null) {
				lockPool = createPool(poolKey);
				// read-lock is already obtained by createPool.
				log.debug("[{}] Created pool for key {}", getPoolsName(), poolKey);
			} else {
				lockPool.getUseLock().readLock().lock();
				if (lockPool.getPool().isClosed()) {
					try {
						ensureNotClosed();
						if (poolsMap.containsValue(lockPool)) {
							// This should not happen, clean-method should have removed this.
							log.error("[{}] Programming error: closed pool in pools-map.", getPoolsName());
							poolsMap.remove(lockPool);
						}
					} finally {
						lockPool.getUseLock().readLock().unlock();
					}
					lockPool = null;
				}
			}
		} // while lockpool null
		T t = null;
		try {
			if (acquireTimeOutMs < 0L) {
				acquireTimeOutMs = lockPool.getPool().getMaxAcquireTimeMs();
			}
			if (maxLeasedTimeMs < 0L) {
				maxLeasedTimeMs = lockPool.getPool().getMaxLeaseTimeMs();
			}
			t = lockPool.getPool().acquire(acquireTimeOutMs, maxLeasedTimeMs);
		} finally {
			lockPool.getUseLock().readLock().unlock();
		}
		return t;
	}
	
	/**
	 * Throws an {@link IllegalStateException} when pool is closed.
	 */
	protected void ensureNotClosed() {
		
		if (isClosed()) {
			throw new IllegalStateException("Pools-map is closed.");
		}
	}
	
	protected PoolsMapPool<T> createPool(K poolKey) {
		
		PoolsMapPool<T> lockPool = null;
		// lock is required here to prevent creating two pools for the same pool-key.
		poolCreateLock.lock();
		try {
			// recheck closed status in case PoolMap is closed while an "acquire" is still happening.
			ensureNotClosed();
			lockPool = poolsMap.get(poolKey);
			if (lockPool == null) {
				PrunedPool<T> pool = poolsFactory.create(poolKey, poolsPruner);
				if (pool == null) {
					throw new RuntimeException("Pool factory failed to create a pool for key " + poolKey);
				}
				lockPool = new PoolsMapPool<>(pool);
				// first get the read-lock
				lockPool.getUseLock().readLock().lock();
				// then add to the pools-map so that removal thread does not remove the newly created pool. 
				poolsMap.put(poolKey, lockPool);
			} else {
				// pool was recently created by another thread that also had the poolCreateLock.
				lockPool.getUseLock().readLock().lock();
			}
		} finally {
			poolCreateLock.unlock();
		}
		return lockPool;
	}
	
	public void release(K poolKey, T t) {
		
		PoolsMapPool<T> lockPool = poolsMap.get(poolKey);
		if (lockPool == null) {
			log.debug("[{}] No pool available to release pool resource for key {}", getPoolsName(), poolKey);
			poolsFactory.destroy(poolKey, t);
		} else {
			lockPool.getPool().release(t);
		}
	}
	
	/**
	 * Removes empty and unused pools from this pools-map.
	 * Called by @[link PoolMapCleanTask}.
	 */
	public void clean() {

		if (isClosed()) {
			return;
		}
		log.trace("[{}] Cleaning pools", getPoolsName());
		Iterator<Entry<K, PoolsMapPool<T>>> it = poolsMap.entrySet().iterator();
		// The iterator can handle changes to the underlying map.
		// The iterator supports element removal from underlying map. 
		while (it.hasNext()) {
			Entry<K, PoolsMapPool<T>> lockPoolEntry = it.next();
			if (lockPoolEntry.getValue().getPool().isEmpty()) { // pool is candidate for removal
				PoolsMapPool<T> lockPool = lockPoolEntry.getValue();
				WriteLock lock = lockPool.getUseLock().writeLock();
				if (lock.tryLock()) {
					// pool is not used, no read-lock exist.
					// newly created empty pools always have a read-lock.
					if (lockPool.getPool().isEmpty()) {
						// remove pool-entry from map
						it.remove();
						// do not forget to close the pool so that pool-pruner is kept up to date.
						lockPool.getPool().close();
						log.debug("[{}] Removed pool for key {}", getPoolsName(), lockPoolEntry.getKey());
					}
					lock.unlock();
				}
			}
		}
	}

	/**
	 * Close this pools-map and all related pools.
	 */
	public synchronized void close() {
		
		if (isClosed() || !isOpened()) {
			return;
		}
		closed = true;
		cleanTask.stop();
		Set<PoolsMapPool<T>> pools = null;
		poolCreateLock.lock();
		try {
        	if (getExecutor() != null && isShutdownExecutor()) {
        		getExecutor().shutdown();
               	log.debug("[{}] Pools map executor stopped.", getPoolsName());
        		setExecutor(null);
        	}
        	opened = false;
			pools = poolsMap.values().stream().collect(Collectors.toSet());
			// do NOT clear poolsMap in case resources are released after this PoolMap is closed.
		} finally {
			poolCreateLock.unlock();
		}
		pools.forEach(p -> p.getPool().close());
		log.debug("[{}] Pools map closed.", getPoolsName());
		opened = false;
	}
	
	/* *** bean methods *** */

	public IPoolsMapFactory<T, K> getFactory() {
		return poolsFactory;
	}
	
	/**
	 * This is an expensive method, do not call this in a loop.
	 * @return number of pools.
	 */
	public int getSize() {
		return poolsMap.size();
	}

	public boolean isEmpty() {
		return getSize() < 1;
	}

	/**
	 * This is an expensive method, do not call this in a loop.
	 * @return all keys related to pools.
	 */
	public Set<K> getPoolKeys() {
		return poolsMap.keySet().stream().collect(Collectors.toSet());
	}

	public boolean isOpened() {
		return opened;
	}

	public boolean isClosed() {
		return closed;
	}

	/**
	 * Short description of the pools-map.
	 * Default set to class-name[hashCode].
	 */
	public String getPoolsName() {
		return poolsName;
	}

	public void setPoolsName(String name) {
		
		if (name != null && !name.isEmpty()) {
			this.poolsName = name;
		}
	}

	/**
	 * Returns {@link #getPoolsName()}
	 */
	@Override
	public String toString() {
		return getPoolsName();
	}

	public long getCleanIntervalMs() {
		return cleanIntervalMs.get();
	}

	/**
	 * Clean interval in milliseconds at witch empty pools will be removed.
	 * @param cleanIntervalMs Any value lower than 1 will be ignored.
	 */
	public void setCleanIntervalMs(long cleanIntervalMs) {
		
		if (cleanIntervalMs > 0L) {
			this.cleanIntervalMs.set(cleanIntervalMs);
		}
	}

    /**
     * Sets the executor to use for cleaning abandoned pools.
     * See also {@link #getExecutor()}.
     * Must be set before this pools-map is opened, cannot be updated after pools-map was opened.
     * <br><b>NOTE</b> The default executor from the {@link PoolPruner} cannot be used here
     * ({@link PoolPruner} destroys the executor when there are no more pools to prune,
     * and creates a new one when the first pool to prune is added).
     * For efficiency, manually set a {@link ScheduledExecutorService} here and with the {@link PoolPruner},
     * the manually created and set executor can be closed when both this class is closed 
     * and {@link PoolPruner} has nothing more to do.
     */
    public void setExecutor(ScheduledExecutorService executor) { 
    	if (!isOpened()) {
    		this.executor = executor;
    	}
    }

    /** 
     * The executor used to schedule the cleanup task.
     * If none is set, a default {@link ScheduledThreadPoolExecutor} is set when the pools-map is opened
     * and {@link #setShutdownExecutor(boolean)} is set to true.
     */
    public ScheduledExecutorService getExecutor() { 
    	return executor; 
    }

    /** 
     * If true, shuts down the executor when this pools-map is closed.
     * Default true if no executor was (explicitly) set via {@link #setExecutor(ScheduledExecutorService)}. 
     */ 
	public boolean isShutdownExecutor() {
		return shutdownExecutor;
	}

	/**
	 * Shutdown executor when this pools-map is closed, or not.
	 * See also {@link #isShutdownExecutor()}.
     * Must be set before this pools-map is opened, cannot be updated after pruner has started.
	 */
	public void setShutdownExecutor(boolean shutdownExecutor) {
    	if (!isOpened()) {
    		this.shutdownExecutor = shutdownExecutor;
    	}
	}

}
