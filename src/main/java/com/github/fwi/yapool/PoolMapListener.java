package com.github.fwi.yapool;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Listens for expired events from pools to determine if a pool is empty and should be closed and removed.
 * Calls {@link PoolMap#onExpired(PoolEvent, PrunedPool, Object)} when event is fired.
 * <p>
 * This only works within the context of {@link PoolMap} usage, e.g. there is no flushing and evicting of pull resources.
 * In that case, listening to events {@link PoolEvent#IDLE_EXPIRED} and {@link PoolEvent#LEASE_EXPIRED}
 * can be used to determine if a pool is no longer used and can be removed.
 *
 * @param <T> Pool resource type.
 * @param <K> Pool key type.
 */
public class PoolMapListener<T, K> implements IPoolListener {
	
	private static final Collection<String> wantedEvents;
	static {
		Set<String> s = new HashSet<>();
		s.add(PoolEvent.IDLE_EXPIRED);
		s.add(PoolEvent.LEASE_EXPIRED);
		wantedEvents = Collections.unmodifiableSet(s);
	}
	
	private final PoolMap<T, K> poolMap;
	private final PrunedPool<T> pool;
	private final K poolKey;
	
	public PoolMapListener(PoolMap<T, K> poolMap, PrunedPool<T> pool, K poolKey) {
		this.poolMap = poolMap;
		this.pool = pool;
		this.poolKey = poolKey;
	}
	
	public PrunedPool<T> getPool() { return pool; }
	public K getPoolKey() { return poolKey; }

	@Override
	public boolean wantAllEventActions() {
		return false;
	}

	@Override
	public Collection<String> getWantEventActions() {
		return wantedEvents;
	}

	@Override
	public boolean wantsEventAction(String poolEventAction) {
		return wantedEvents.contains(poolEventAction);
	}

	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		poolMap.onExpired(poolEvent, getPool(), getPoolKey());
	}

}
