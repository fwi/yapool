package com.github.fwi.yapool;

/**
 * A factory for pools for a {@link PoolsMap}.
 * <br>Note that the pool-key is the only "given" with which a pool can be created,
 * as such the pool-key needs to contain enough information to create a properly configured pool.
 * <br>At the same time, the pool-key must function as a proper key and therefor must implement
 * proper {@link Object#hashCode()} and {@link Object#equals(Object)} methods
 * that correctly identify two pool-key objects as the same when they are the same. 
 *
 * @param <T> The type of pool-resource.
 * @param <K> The type of pool-key.
 * 
 */
public interface IPoolsMapFactory<T, K> {

	/**
	 * Creates a pruned pool.
	 * Creating a pool should be fast, returned pool should be empty and must be opened.
	 * The pool must have a prune-interval and max. idle timeout greater than 0.
	 * The pool must have been added to the pool-pruner for pruning.
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
