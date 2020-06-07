package com.github.fwi.yapool.listener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.fwi.yapool.BoundPool;
import com.github.fwi.yapool.PoolEvent;
import static com.github.fwi.yapool.listener.PoolResourcePerformance.toDate;

/**
 * Tracks the maximum amount of waiting threads and the maximum pool size for a BoundPool.
 * @author fwiers
 *
 */
public class MaxUsageTracker extends PoolListener {

	protected Map<Integer, Long> maxUsed;
	protected Map<Integer, Long> maxWaiting;
	protected long startTime;
	protected BoundPool<?> pool;
	
	public MaxUsageTracker(BoundPool<?> pool) {
		super();
		this.pool = pool;
		addWantEvent(PoolEvent.ACQUIRING, PoolEvent.CREATED);
		maxUsed = new ConcurrentHashMap<Integer, Long>();
		maxWaiting = new ConcurrentHashMap<Integer, Long>();
		startTime = System.currentTimeMillis();
	}
	
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		/*
		 * To avoid synchronizing, just store everything in a concurrent hashmap.
		 */
		if (poolEvent.getAction() == PoolEvent.ACQUIRING) {
			maxWaiting.put(poolEvent.getPool().getWaitingSize(), poolEvent.getTimeStamp());
		} else if (poolEvent.getAction() == PoolEvent.CREATED) {
			maxUsed.put(pool.getSize(), poolEvent.getTimeStamp());
		}
	}
	
	public void reset() {
		startTime = System.currentTimeMillis();
		maxUsed.clear();
		maxWaiting.clear();
	}
	
	protected int getMaxIndex(boolean used) {
		
		int index = -1;
		Set<Integer> usedKeys = (used ? maxUsed.keySet() : maxWaiting.keySet());
		for (int max : usedKeys) {
			if (max > index) index = max;
		}
		return (index == -1 ? 0 : index);
	}
	
	protected long getMaxTimestamp(boolean used, int size) {
		
		Long time = (used ? maxUsed.get(size) : maxWaiting.get(size));
		return (time == null ? startTime : time);
	}
	
	public int getMaxSize() {
		return getMaxIndex(true);
	}
	
	public long getMaxSizeTimestamp(int size) {
		return getMaxTimestamp(true, size);
	}
	
	public int getMaxWaiting() {
		return getMaxIndex(false);
	}
	
	public long getMaxWaitingTimestamp(int size) {
		return getMaxTimestamp(false, size);
	}
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		sb.append("Maximum usage statistics for pool ").append(pool.getPoolName());
		sb.append("\nStart-time  : ").append(toDate(startTime));
		int maxSize = getMaxSize();
		sb.append("\nMax. size   : ").append(maxSize).append(" at ").append(toDate(getMaxSizeTimestamp(maxSize)));
		int maxWaiting = getMaxWaiting();
		sb.append("\nMax. waiting: ").append(maxWaiting).append(" at ").append(toDate(getMaxWaitingTimestamp(maxWaiting)));
		return sb.toString();
	}

}
