package nl.fw.yapool;

import java.util.NoSuchElementException;

/**
 * Main interface for all pool implementations.
 * @author FWiers
 *
 * @param <T> The type of resource in the pool.
 */
public interface IPool<T> {

	/** The factory for creating, validating and destroying pooled resources. */
	void setFactory(IPoolFactory<T> f);

	/** The factory for creating, validating and destroying pooled resources. */
	IPoolFactory<T> getFactory();
	
	/** 
	 * Acquires a resource from the pool within the maximum acquire time (default 0), 
	 * unless the pool is closed (throws an {@link IllegalStateException}). 
	 * @return A resource for usage. This resource should be {@link #release(Object)}d after usage.
	 * <br><b>Throws</b> a {@link RuntimeException} when no resource could be created.
	 * <br><b>Throws</b> a {@link NoSuchElementException} when no resource is available within the maximum acquire time.
	 */
	T acquire();
	
	/**
	 * Same as {@link #acquire()} but uses the given acquireTimeOutMs as maximum acquire time.
	 * @param acquireTimeOutMs acquire time in milliseconds. Any value smaller than 1 means "do not wait at all".
	 */
	T acquire(long acquireTimeOutMs);
	
	/** Closes the pool. */
	void close();
	
	/**
	 * Puts the resource back in the pool so that it can be re-used.
	 * @param r
	 * @return null if the resource was not put back in the pool.
	 */
	T release(T r);
	
	/**
	 * @return A pool event handler.
	 */
	IPoolEvents getEvents();

	/**
	 * Sets the pool-event handler.
	 * By default, all pools have a pool-event handler {@link PoolEvents} already set.
	 * This method can be called even when the pool is open (i.e. in use).
	 * 
	 */
	void setEvents(IPoolEvents pe);
}
