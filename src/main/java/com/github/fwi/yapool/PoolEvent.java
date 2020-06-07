package com.github.fwi.yapool;

import java.util.EventObject;

/**
 * Event that is fired when a pool performs an action.
 * @author Fred
 *
 */
public class PoolEvent extends EventObject {

	private static final long serialVersionUID = -4317364976211517795L;
	
	/** Created a new resource. */
	public static final String CREATED = "CREATED";
	/** About to destroy a resource. */
	public static final String DESTROYING = "DESTROYING";

	/** About to acquire a resource from the pool. */
	public static final String ACQUIRING = "ACQUIRING";
	/** Acquired a resource from the pool. */
	public static final String ACQUIRED = "ACQUIRED";
	/** About to release a resource to the pool. */
	public static final String RELEASING = "RELEASING";

	/** Pool is opened. */
	public static final String OPENED = "OPENED";
	/** Pool is closed. */
	public static final String CLOSED = "CLOSED";

	/** Resource removed from the pool because the lease of the resource expired. */
	public static final String LEASE_EXPIRED = "LEASE_EXPIRED";
	/** Resource removed from the pool because it was idle for too long. */
	public static final String IDLE_EXPIRED = "IDLE_EXPIRED";
	/** Resource removed from the pool because it was invalid according to {@link IPoolFactory#isValid(Object)}. */
	public static final String INVALID = "INVALID";

    private transient Object resource;
    private transient String action;
    private transient long timeStamp;

	public PoolEvent(Object source, String action) {
		this(source, action, null);
	}
	public PoolEvent(Object source, String action, Object resource) {
		this(source, action, resource, 0L);
	}
	public PoolEvent(Object source, String action, Object resource, long timeStamp) {
		super(source);
		this.resource = resource;
		this.action = action;
		this.timeStamp = timeStamp;
	}
	
	public Pool<?> getPool() {
		return (Pool<?>) getSource();
	}
	
	/**
	 * The resource to which the action applies.
	 * Can be null if an {@link #ACQUIRED} action failed.
	 */
	public Object getResource() {
		return resource;
	}
	
	/**
	 * One of the static pool-event strings in this class ({@link #OPENED}, {@link #ACQUIRING}, {@link #DESTROYING}, etc.)
	 */
	public String getAction() {
		return action;
	}
	
	/**
	 * If time-stamp was not set during construction,
	 * this method will return the moment this method was first called. 
	 */
	public long getTimeStamp() {

		if (timeStamp == 0L) timeStamp = System.currentTimeMillis();
		return timeStamp;
	}
	
	/** Describes this pool-event by class-name:action:resource. */
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ":" + action + ":"  + (resource == null ? "none" : resource);
	}
}
