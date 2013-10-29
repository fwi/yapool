package nl.fw.yapool;

import java.util.EventObject;

/**
 * Event that is fired when a pool performs an action.
 * @author Fred
 *
 */
public class PoolEvent extends EventObject {

	private static final long serialVersionUID = -4317364976211517795L;
	
	public static final String CREATED = "CREATED";
	public static final String DESTROYING = "DESTROYING";

	public static final String ACQUIRING = "ACQUIRING";
	public static final String ACQUIRED = "ACQUIRED";
	public static final String RELEASING = "RELEASING";

	public static final String OPENED = "OPENED";
	public static final String CLOSED = "CLOSED";

	public static final String LEASE_EXPIRED = "LEASE_EXPIRED";
	public static final String IDLE_EXPIRED = "IDLE_EXPIRED";
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
	}
	
	public Pool<?> getPool() {
		return (Pool<?>) getSource();
	}
	
	public Object getResource() {
		return resource;
	}
	
	public String getAction() {
		return action;
	}
	
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
