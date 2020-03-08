package com.github.fwi.yapool.listener;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PoolResourcePerformance implements Comparable<PoolResourcePerformance> {

	public static SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss:SSS");
	
	long created;
	
	public TimeDeltaStats acquired = new TimeDeltaStats("Acquired");
	public TimeDeltaStats leased = new TimeDeltaStats("Leased  ");
	public TimeDeltaStats idle = new TimeDeltaStats("Idled   ");
	
	public long releaseFailed;

	public long acquireCount;
	public long releaseCount;
	/** Set to 1 when resource was invalidated. */
	public boolean invalidated;
	/** Set to time-stamp when resource was destroyed. */
	public long destroyed;
	
	private long leaseStart;
	private long idleStart;
	
	/** Set to resource.class.name:resource.toString() by contructor, or {@link #POOL_STATS} */
	final Object resource;
	
	public PoolResourcePerformance(Object resource, long t) {
		super();
		this.resource = resource;
		created = t;
		leaseStart =  t;
	}

	public void setAcquired(long t, long delta) {
		
		acquireCount++;
		leaseStart = t;
		acquired.add(delta);
		if (idleStart != 0L) {
			idle.add(t - idleStart);
			//System.out.print(t - idleStart == 0L ? "idle delta 0 at count " + idle.getCount() + "\n" : "");
		}
		
		//System.out.println("t: " + t + " i: " + idleStart);

	}

	public void setReleased(long t, Object resource) {

		releaseCount++;
		idleStart = t;
		
		if (leaseStart != 0L) {
			leased.add(t - leaseStart);
		}
		//long oldMin = leaseTimeMin;
		//if (oldMin != leaseTimeMin) System.out.println("Min lease: " + leaseTimeMin);
	}
	
	/** Compares on {@link #created} */
	@Override
	public int compareTo(PoolResourcePerformance o) {
		// Do not use Long.compare(l, l2) --> it does not exist in Java 6.
		return Long.valueOf(created).compareTo(Long.valueOf(o.created)); 
	}

	public static String toDate(long t) {
		return (t == 0L ? "0" : df.format(new Date(t)));
	}
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		if (resource == null) {
			sb.append("Pool started: ").append(toDate(created)).append(", stopped: ").append(toDate(destroyed));
		} else {
			sb.append("Resource statistics for ").append(resource.getClass().getName() + ":" + resource.toString());
			sb.append("\nResource created: ").append(toDate(created))
			.append(", destroyed: ").append(toDate(destroyed)).append('\n');
			sb.append(acquired.toString()).append('\n');
			sb.append(leased.toString()).append('\n');
			sb.append(idle.toString());
			if (invalidated) {
				sb.append("\nResource was invalid.");
			}
		}
		return sb.toString();
	}

}
