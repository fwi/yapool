package com.github.fwi.yapool;

/**
 * A factory for creating resources.
 * @author FWiers
 *
 * @param <T> Type of resource.
 */
public interface IPoolFactory<T> {

	/**
	 * Creates a resource.
	 * @return The created resource, never null.
	 * @throws RuntimeException when resource creation fails.
	 */
	T create();
	
	/**
	 * Ensures a resource is still valid.
	 * @return true if resource if valid, false if resource is invaliad
	 */
	default boolean isValid(T resource) {
		return true;
	}
	
	/**
	 * Destroys the resource.
	 */
	default void destroy(T resource) {
		// NO-OP
	}

}
