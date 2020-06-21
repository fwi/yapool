package com.github.fwi.yapool;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PoolsMapPool<T> {

	private final ReentrantReadWriteLock useLock = new ReentrantReadWriteLock();
	private final PrunedPool<T> pool;
	
	public PoolsMapPool(PrunedPool<T> pool) {
		this.pool = pool;
	}
	
	public ReentrantReadWriteLock getUseLock() {
		return useLock;
	}
	
	public PrunedPool<T> getPool() {
		return pool;
	}
}
