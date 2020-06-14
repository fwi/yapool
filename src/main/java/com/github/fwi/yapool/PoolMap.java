package com.github.fwi.yapool;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "pool-of-pools" a.k.a. pools-squared.
 * <br>This type of pools is typically used when one type of resource is used in different configurations.
 * An example of this is connections to different servers where each connection to a particular server
 * is stored in a different pool. This prevents overloading one server with too many connections.
 * Note that for HTTP-type connections, existing solutions already exist and should be used
 * within their respective framework (e.g. <a href="https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html">
 * Apache HTTPClient</a> and <a href="https://square.github.io/okhttp/connections/">OkHttp</a>).
 * <p>
 * A pool-key is used to identify the pool to use for a pool-resource.
 * The pool-key <b>MUST</b> have proper {@link Object#hashCode()} and {@link Object#equals(Object)} methods implemented.
 * <br>The pool-key serves a dual purpose:
 * <br>  - it is used to identify the pool with properly configured resources.
 * <br>  - it is used as configuration for a new pool for the resources.
 * <br>For the latter, an {@link IPoolMapFactory} is required.
 * 
 * @param <T> The type of pool resource.
 * @param <K> The type of pool key.
 */
public class PoolMap<T, K> {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final ConcurrentHashMap<K, PrunedPool<T>> poolsMap = new ConcurrentHashMap<>();
	protected final ReentrantLock poolCreateLock = new ReentrantLock();
	protected final Set<PrunedPool<T>> poolsInUse =  Collections.newSetFromMap(new ConcurrentHashMap<>());
	protected final ReentrantReadWriteLock poolUseLock = new ReentrantReadWriteLock();
	protected final IPoolMapFactory<T, K> poolsFactory;
	protected final PoolPruner poolsPruner;
	protected volatile boolean closed;

	public PoolMap(IPoolMapFactory<T, K> poolsFactory) {
		this(poolsFactory, PoolPruner.getInstance());
	}

	public PoolMap(IPoolMapFactory<T, K> poolsFactory, PoolPruner poolPruner) {
		super();
		this.poolsFactory = poolsFactory;
		this.poolsPruner = poolPruner;
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
		
		ensurePoolNotClosed();
		PrunedPool<T> pool = null;
		poolUseLock.readLock().lock();
		try {
			pool = poolsMap.get(poolKey);
			if (pool != null) {
				poolsInUse.add(pool);
			}
		} finally {
			poolUseLock.readLock().unlock();
		}
		if (pool == null) {
			pool = createPool(poolKey);
		}
		T t = null;
		try {
			if (acquireTimeOutMs < 0L) {
				acquireTimeOutMs = pool.getMaxAcquireTimeMs();
			}
			if (maxLeasedTimeMs < 0L) {
				maxLeasedTimeMs = pool.getMaxLeaseTimeMs();
			}
			t = pool.acquire(acquireTimeOutMs, maxLeasedTimeMs);
		} finally {
			poolsInUse.remove(pool);
		}
		return t;
	}
	
	/**
	 * Throws an {@link IllegalStateException} when pool is closed.
	 */
	protected void ensurePoolNotClosed() {
		
		if (isClosed()) {
			throw new IllegalStateException("Pools-map is closed.");
		}
	}
	
	protected PrunedPool<T> createPool(K poolKey) {
		
		PrunedPool<T> pool = null;
		poolCreateLock.lock();
		try {
			// recheck closed status in case PoolMap is closed while an "acquire" is still happening.
			ensurePoolNotClosed();
			pool = poolsMap.get(poolKey);
			if (pool == null) {
				pool = poolsFactory.create(poolKey, poolsPruner);
				pool.getEvents().addPoolListener(new PoolMapListener<T, K>(this, pool, poolKey));
				poolsMap.put(poolKey, pool);
				poolsInUse.add(pool);
			}
		} finally {
			poolCreateLock.unlock();
		}
		return pool;
	}
	
	public void release(K poolKey, T t) {
		
		PrunedPool<T> pool = poolsMap.get(poolKey);
		if (pool == null) {
			log.debug("No pool available to release pool resource for key {}", poolKey);
			poolsFactory.destroy(poolKey, t);
		} else {
			pool.release(t);
		}
	}

	public void onExpired(PoolEvent poolEvent, PrunedPool<T> pool, K poolKey) {
		
		// fast check without lock to see if pool should be closed.
		if (isUsed(pool)) {
			return;
		}
		boolean removed = false;
		poolUseLock.writeLock().lock();
		try {
			// re-check after lock to ensure pool is not being used..
			if (isUsed(pool)) {
				return;
			}
			removed = (poolsMap.remove(poolKey) != null);
			pool.close();
		} finally {
			poolUseLock.writeLock().unlock();
		}
		if (removed) {
			log.debug("Removed pool {} for key {}.", pool.getPoolName(), poolKey);
		} else {
			log.warn("Closed pool {} for key {} that was not part of this pool-map.", pool.getPoolName(), poolKey);
		}
	}
	
	protected boolean isUsed(PrunedPool<T> pool) {
		return (!pool.isClosed() && (!pool.isEmpty() || poolsInUse.contains(pool)));
	}
	
	/**
	 * Close this pools-map and all related pools.
	 */
	public void close() {
		
		if (isClosed()) return;
		closed = true;
		Set<PrunedPool<T>> pools = null;
		poolCreateLock.lock();
		try {
			pools = poolsMap.values().stream().collect(Collectors.toSet());
			// do NOT clear poolsMap in case resources are released after this PoolMap is closed.
		} finally {
			poolCreateLock.unlock();
		}
		pools.forEach(p -> p.close());
	}

	public IPoolMapFactory<T, K> getFactory() {
		return poolsFactory;
	}
	
	/**
	 * This is an expensive method, do not call this in a loop.
	 * @return number of pools.
	 */
	public int getSize() {
		return poolsMap.size();
	}
	
	/**
	 * This is an expensive method, do not call this in a loop.
	 * @return all keys related to pools.
	 */
	public Set<K> getPoolKeys() {
		return poolsMap.keySet().stream().collect(Collectors.toSet());
	}

	public boolean isClosed() {
		return closed;
	}
	
}
