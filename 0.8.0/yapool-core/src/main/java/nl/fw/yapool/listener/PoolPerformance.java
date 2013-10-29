package nl.fw.yapool.listener;

import static nl.fw.yapool.PoolEvent.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import nl.fw.yapool.Pool;
import nl.fw.yapool.PoolEvent;

public class PoolPerformance extends PoolListener {

	public AtomicLong acquireCount = new AtomicLong();
	public AtomicLong acquireFailed = new AtomicLong();

	public AtomicLong created = new AtomicLong();
	public AtomicLong destroyed = new AtomicLong();

	Object resource;
	PoolResourcePerformance poolStats = new PoolResourcePerformance(null, System.currentTimeMillis());
	
	ConcurrentHashMap<Object, PoolResourcePerformance> m = new ConcurrentHashMap<Object, PoolResourcePerformance>();
	ConcurrentHashMap<Thread, Long> acquire = new ConcurrentHashMap<Thread, Long>();
	
	private Pool<?> pool;
	
	public PoolPerformance(Pool<?> pool) {
		this.pool = pool;
	}
	
	public void clear() {
		m.clear();
	}
	
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		final Object resource = poolEvent.getResource();
		final long t = poolEvent.getTimeStamp();
		PoolResourcePerformance prs = (resource  == null ? poolStats : m.get(poolEvent.getResource()));
		if (prs == null) {
			m.put(poolEvent.getResource(), prs = new PoolResourcePerformance(resource, t));
		}
		if (poolEvent.getAction() == ACQUIRING) {
			acquire.put(Thread.currentThread(), t);
		} else if (poolEvent.getAction() == ACQUIRED) {
			Long start = acquire.remove(Thread.currentThread());
			if (start == null) return;
			if (resource == null) {
				acquireFailed.incrementAndGet();
			} else {
				acquireCount.incrementAndGet();
				prs.setAcquired(t, t - start);
			}
		} else if (poolEvent.getAction() == RELEASING) {
			if (pool.isClosed()) return; 
			prs.setReleased(t, resource);
		} else if (poolEvent.getAction() == CREATED) {
			created.incrementAndGet();
			// most work already done by contructor of PoolResourceStats, see above
		} else if (poolEvent.getAction() == DESTROYING) {
			prs.destroyed = t;
			destroyed.incrementAndGet();
		} else if (poolEvent.getAction() == INVALID) {
			prs.invalidated = true;
		} else if (poolEvent.getAction() == OPENED) {
			// NO-OP use created field
		} else if (poolEvent.getAction() == CLOSED) {
			prs.destroyed = t;
		}
	}
	
	public String toString() {

		ArrayList<PoolResourcePerformance> l = new ArrayList<PoolResourcePerformance>();
		for (PoolResourcePerformance prr : m.values()) {
			l.add(prr);
		}
		Collections.sort(l);
		StringBuilder sb = new StringBuilder();
		sb.append("Pool statistics for ").append(pool.getPoolName());
		sb.append('\n').append(poolStats);
		sb.append("\nResources created: ").append(created.get()).append(", destroyed: ").append(destroyed.get());
		if (acquireFailed.get() > 0L) {
			sb.append("\nFailed acquires: ").append(acquireFailed.get());
		}
		if (acquire.size() > 0) {
			sb.append("\nAcquires stuck: ").append(acquire.size());
		}
		long leaseTotal = 0L;
		for (PoolResourcePerformance prr : l) {
			sb.append('\n').append(prr);
			leaseTotal += prr.leased.getCount();
		}
		if (acquireCount.get() != leaseTotal) {
			sb.append("\nTotal acquired: " + acquireCount.get() + ", leased: " + leaseTotal);
		}
		return sb.toString();
	}

}
