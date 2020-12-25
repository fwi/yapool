package com.github.fwi.yapool;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Helper class for {@link PoolsMap} - associates an "in use" lock with a pool.
 * @param <T> The type of pool resource.
 */
public class PoolsMapPool<T> {

	private final ReentrantReadWriteLock useLock = new ReentrantReadWriteLock();
	private final PrunedPool<T> pool;
	
	public PoolsMapPool(PrunedPool<T> pool) {
		this.pool = pool;
	}
	
	/**
	 * Lock used to prevent removing (cleaning) the associated empty pool from a {@link PoolsMap}.
	 * <br>A read-lock is used to ensure a resource from the pool can be acquired.
	 * <br>A write-lock is used to ensure the empty pool can be removed from the {@link PoolsMap}.
	 */
	public ReentrantReadWriteLock getUseLock() {
		return useLock;
	}
	
	public PrunedPool<T> getPool() {
		return pool;
	}
}
