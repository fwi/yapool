package com.github.fwi.yapool;

/**
 * A factory for pools for a {@link PoolsMap}.
 * <br>Note that the pool-key is the only "given" with which a pool can be created,
 * as such the pool-key needs to contain enough information to create a properly configured pool.
 *
 * @param <T> The type of pool-resource.
 * @param <K> The type of pool-key.
 * 
 * TODO: create base class for this interface?
 * 
 */
public interface IPoolsMapFactory<T, K> {

	/**
	 * Creates a pruned pool.
	 * Creating a pool should be fast, returned pool should be empty and must be opened.
	 * The pool must have a prune-interval and max. idle timeout greater than 0.
	 * <br>If a pool cannot be created, a {@link RuntimeException} must be thrown.
	 */
	PrunedPool<T> create(K poolKey, PoolPruner poolPruner);

	/**
	 * Destroys a pool-resource for which the pool no longer exists
	 * (this happens when a resource is returned from a process that got stuck and pool was closed in the meantime).
	 * This is part of "normal" operations and not an error.
	 */
	void destroy(K poolKey, T t);
}
