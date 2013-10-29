package nl.fw.yapool;

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
	boolean isValid(T resource);
	
	/**
	 * Destroys the resource.
	 */
	void destroy(T resource);

}
