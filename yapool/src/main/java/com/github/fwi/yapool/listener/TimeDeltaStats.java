package com.github.fwi.yapool.listener;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class TimeDeltaStats implements Serializable {

	private static final long serialVersionUID = -1855078416154479616L;

	/**
	 * Access must be synchronized, somehow TestBoundPool.singletonPool()
	 * can throw a ConcurrentModificationException
	 */
	private final List<Long> deltas = new LinkedList<Long>();
	private String name;

	public TimeDeltaStats() {
		super();
		this.name = getClass().getSimpleName();
	}
	public TimeDeltaStats(String name) {
		super();
		this.name = name;
	}
	
	public void setName(String name) { this.name = name; }
	public String getName() { return name; }
	
	public void add(long delta) {
		
		synchronized (deltas) {
			deltas.add(delta);
		}
	}
	
	public long getMin() {
		
		long min = Long.MAX_VALUE;
		synchronized (deltas) {
			for (Long d : deltas) {
				min = Math.min(min, d);
			}
		}
		return (min == Long.MAX_VALUE ? 0 : min);
	}
	
	public long getMax() {
		
		long max = 0L;
		synchronized (deltas) {
			for (Long d : deltas) {
				max = Math.max(max, d);
			}
		}
		return max;
	}
	
	public int getCount() {
		return deltas.size();
	}
	
	public long getAvg() {
		
		if (getCount() < 1) return 0L;
		long total = 0L;
		synchronized (deltas) {
			for (Long d : deltas) {
				total += d;
			}
		}
		return total / getCount();
	}
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder(name);
		sb.append(" - min / avg / max: ").append(getCount())
		.append(" - ").append(getMin()).append(" / ").append(getAvg()).append(" / ").append(getMax());
		return sb.toString();
	}
}
