package com.github.fwi.yapool.listener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fwi.yapool.BoundPool;
import com.github.fwi.yapool.PoolEvent;

/**
 * A listener that logs the thread's stack-trace of the leaser at the time the resource was acquired, when the resource is expired. 
 * {@link com.github.fwi.yapool.PrunedPool} can log the thread's stack-trace of the leaser at the time the resource has expired,
 * but that may be "too late" to determine where things went wrong (e.g. when some code path does not release a resource).
 * This listener can be used to get information where in the code the lease was acquired
 * (which may be "too early", but better than nothing).  
 */
public class LeaserAcquiredTrace extends PoolListener {

	protected Logger log = LoggerFactory.getLogger(this.getClass());
	
	protected ConcurrentHashMap<Object, StackTraceElement[]> traces = new ConcurrentHashMap<Object, StackTraceElement[]>(); 
	protected ConcurrentHashMap<Object, String> threads = new ConcurrentHashMap<Object, String>(); 
	protected BoundPool<?> pool;
	protected boolean logAsDebug;
	protected boolean logAsWarn;
	
	public LeaserAcquiredTrace(BoundPool<?> pool) {
		super();
		this.pool = pool;
		addWantEvent(PoolEvent.ACQUIRED, PoolEvent.LEASE_EXPIRED, PoolEvent.DESTROYING, PoolEvent.CLOSED);
	}
	
	/**
	 * By default, this listener logs information at INFO level.
	 */
	public void setLogAsDebug(boolean debug) {
		logAsDebug = debug;
	}
	
	/**
	 * By default, this listener logs information at INFO level.
	 */
	public void setLogAsWarn(boolean warn) {
		logAsWarn = warn;
	}

	
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		if (poolEvent.getResource() == null) {
			if (poolEvent.getAction() == PoolEvent.CLOSED) {
				traces.clear();
				threads.clear();
			}
		} else if (poolEvent.getAction() == PoolEvent.ACQUIRED) {
			Thread t = Thread.currentThread();
			threads.put(poolEvent.getResource(), t.toString());
			traces.put(poolEvent.getResource(), t.getStackTrace());
		} else if (poolEvent.getAction() == PoolEvent.LEASE_EXPIRED) {
			log(getLeaseTraceInfo(poolEvent.getResource(), traces.get(poolEvent.getResource())));
		} else if (poolEvent.getAction() == PoolEvent.DESTROYING) {
			traces.remove(poolEvent.getResource());
			threads.remove(poolEvent.getResource());
		}
	}
	
	protected String getLeaseTraceInfo(Object resource, StackTraceElement[] stackTraceElements) {
		
		if (stackTraceElements == null) {
			return pool.getPoolName() + " No stack trace from leaser available for resource: " + resource;
		}
		// This looks a lot like PrunedPool.logExpiredTrace
		StringBuilder sb = new StringBuilder(pool.getPoolName() + " Stack trace from leaser " + threads.get(resource) + " when resource was acquired.");
		sb.append('\n').append("Resource: ").append(resource.toString());
		sb.append('\n').append("Stack trace:");
		for (final StackTraceElement st : stackTraceElements) {
			sb.append('\n').append('\t').append(st.getClassName())
			.append("(").append(st.getMethodName())
			.append(":").append(st.getLineNumber()).append(")");
		}
		return sb.toString();
	}
	
	protected void log(String msg) {
		
		if (logAsWarn) {
			log.warn(msg);
		} else if (logAsDebug) {
			if (log.isDebugEnabled()) {
				log.debug(msg);
			}
		} else {
			log.info(msg);
		}
	}
	
	/**
	 * Amount of stack traces from leasers currently available.
	 */
	public int getSize() {
		return traces.size();
	}

	/**
	 * Provides an overview of all stack traces currently monitored.
	 */
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		Set<Object> resources = traces.keySet();
		int amount = 0;
		for (Object r : resources) {
			StackTraceElement[] ste = traces.get(r);
			if (ste != null) {
				sb.append(getLeaseTraceInfo(r, ste));
			}
			amount++;
		}
		return super.toString() + " containing " + amount + " start time leaser stack traces.\n" + sb.toString();
	}

}
