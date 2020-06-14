package com.github.fwi.yapool;

/**
 * A factory for pools for a {@link PoolMap}.
 * <br>Note that the pool-key is the only "given" with which a pool can be created,
 * as such the pool-key needs to contain enough information to create a properly configured pool.
 *
 * @param <T> The type of pool-resource.
 * @param <K> The type of pool-key.
 * 
 * TODO: create base class for this interface.
 * 
 */
public interface IPoolMapFactory<T, K> {

	/**
	 * Creates a pruned pool.
	 * TODO: document requirements for returned pool, see also base-class (pruneInterval, minIdle, maxLease).
	 */
	PrunedPool<T> create(K poolKey, PoolPruner poolPruner);

	/**
	 * Destroys a pool-resource for which the pool no longer exists.
	 */
	void destroy(K poolKey, T t);
}
