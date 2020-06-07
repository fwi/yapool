package com.github.fwi.yapool.object;

import com.github.fwi.yapool.PoolPruner;
import com.github.fwi.yapool.PrunedPool;

/**
 * A pruned pool of objects.
 * @author FWiers
 *
 */
public class ObjectPool<T> extends PrunedPool<T> {
	
	/**
	 * Sets an object factory with the given object-class set.
	 * <br>{@link #setMaxLeaseTimeMs(long)} is set to 0 so that objects can be leased forever.
	 * <br>{@link #setMaxSize(int)} is set to 65536.
	 */
	public ObjectPool(Class<T> objectClass) {
		super();
		ObjectFactory<T> f = new ObjectFactory<>();
		f.setObjectClass(objectClass);
		setFactory(f);
		setMaxLeaseTimeMs(0L);
		setMaxSize(65536);
	}
	
	@Override
	public ObjectFactory<T> getFactory() {
		return (ObjectFactory<T>) super.getFactory();
	}

	/**
	 * Registers this pool with the {@link PoolPruner}
	 * so that idle objects are removed from the pool.
	 */
	@Override
	public void open(int amount) {
		super.open(amount);
		PoolPruner.getInstance().add(this);
	}

	@Override
	public T acquire() {
		return (T) super.acquire();
	}
	
	@Override
	public T acquire(long acquireTimeOutMs) {
		return (T) super.acquire(acquireTimeOutMs);
	}

	@Override
	public T acquire(long acquireTimeOutMs, long maxLeasedTimeMs) {
		return (T) super.acquire(acquireTimeOutMs, maxLeasedTimeMs);
	}
	
}
