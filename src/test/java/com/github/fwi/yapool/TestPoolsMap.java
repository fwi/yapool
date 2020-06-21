package com.github.fwi.yapool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPoolsMap {

	protected Logger log = LoggerFactory.getLogger(getClass());

	@Test
	public void testPoolMap() {
		
		PoolMapFactory factory = new PoolMapFactory();
		PoolsMap<Long, String> pools = new PoolsMap<>(factory);
		pools.setCleanIntervalMs(1L);
		pools.open();
		Long r1_1 = pools.acquire("1000");
		Long r1_2 = pools.acquire("1000");
		Long r2_3 = pools.acquire("2000");
		Long r2_4 = pools.acquire("2000");
		pools.release("2000", r2_4);
		pools.release("2000", r2_3);
		pools.release("1000", r1_2);
		pools.release("2000", pools.acquire("2000"));
		assertEquals("Verify resources are re-used.", 2, pools.poolsMap.get("2000").getPool().getSize());
		// destroy method from factory gets called when pool does not exist for resource
		pools.release("dummy", 0L);
		assertEquals(1L, factory.destroyedCounter.get());
		pools.close();
		assertFalse("PoolPruner must have stopped after all pools are closed.", PoolPruner.getInstance().isRunning());
		// Pools are closed so this resource gets evicted
		// but pool should still be available to close this resource.
		pools.release("1000", r1_1);
		// TODO: test the read/write lock.
	}
	
	static class PoolMapFactory implements IPoolsMapFactory<Long, String> {

		protected Logger log = LoggerFactory.getLogger(getClass());

		@Override
		public PrunedPool<Long> create(String poolKey, PoolPruner poolPruner) {
			
			PrunedPool<Long> p = new PrunedPool<>();
			p.setPoolName(poolKey);
			p.setFactory(new PoolFactory(poolKey));
			p.setFair(true);
			p.setMaxIdleTimeMs(100L);
			// prune task starts when pool is opened.
			poolPruner.add(p);
			// default minSize is 0.
			p.open();
			return p;
		}

		final AtomicLong destroyedCounter = new AtomicLong();
		
		@Override
		public void destroy(String poolKey, Long t) {
			log.debug("Resource released after pool {} closed or no longer exists: {}", poolKey, t);
			destroyedCounter.incrementAndGet();
		}
		
	}
	
	static class PoolFactory implements IPoolFactory<Long> {

		static final AtomicLong facoryCounter = new AtomicLong();
		
		final Long startAt;
		
		public PoolFactory(String startAt) {
			super();
			Long startAtValue;
			try {
				startAtValue = Long.valueOf(startAt);
			} catch (Exception e) {
				startAtValue = 0L;
			}
			this.startAt = startAtValue;
		}
		
		@Override
		public Long create() {
			return startAt + facoryCounter.incrementAndGet();
		}
		
	}
}
