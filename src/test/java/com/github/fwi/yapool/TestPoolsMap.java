package com.github.fwi.yapool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPoolsMap {

	protected static Logger log = LoggerFactory.getLogger(TestPoolsMap.class);

	@Test
	public void testPoolsMap() {
		
		PoolsMapFactory factory = new PoolsMapFactory();
		PoolsMap<Long, String> pools = new PoolsMap<>(factory);
		pools.setCleanIntervalMs(1L);
		// Both poolsmap and poolpruner create (and close) a scheduled task executor as needed.
		// But since both need one, it is a little bit more efficient to set one task executor
		// that is used by both. 
		ScheduledThreadPoolExecutor stp = createScheduledExectuor();
		pools.setExecutor(stp);
		PoolPruner.getInstance().setExecutor(stp);
		PoolPruner.getInstance().setShutdownExecutor(false); // set to true true if other tests were executed.
		pools.open();
		Long r1_1 = null;
		try {
			r1_1 = pools.acquire("1000");
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
		} finally {
			pools.close();
			stp.shutdown();
			PoolPruner.getInstance().setExecutor(null);
		}
		try {
			assertFalse("PoolPruner must have stopped after all pools are closed.", PoolPruner.getInstance().isRunning());
		} catch (AssertionError e) {
			PoolPruner.getInstance().stop();
			PoolPruner.getInstance().setExecutor(null);
			throw e;
		}
		// Pools are closed so this resource gets evicted
		// but pool should still be available to close this resource (no errors).
		pools.release("1000", r1_1);
	}
	
	ScheduledThreadPoolExecutor createScheduledExectuor() {
		
		ScheduledThreadPoolExecutor stp = new ScheduledThreadPoolExecutor(1);
		stp.setRemoveOnCancelPolicy(true);
		return stp;
	}
	
	@Test
	public void testPoolMapRemoval() {
		
		PoolsMapFactory factory = new PoolsMapFactory(2L, 2L);
		PoolsMap<Long, String> pools = new PoolsMap<>(factory);
		pools.setCleanIntervalMs(10L);
		// let poolsmap and poolpruner manage their own scheduled task executor.
		pools.open();
		try {
			final int testSize = 10;
			List<Long> resources = IntStream.range(0, testSize).mapToObj(i -> pools.acquire(Integer.toString(i * 100))).collect(Collectors.toList());
			sleep(100L);
			// all resources are claimed, none should be removed.
			assertEquals(testSize, pools.getSize());
			// release half of the resources
			final int releaseHalf = testSize / 2;
			IntStream.range(0, releaseHalf).forEach(i -> pools.release(Integer.toString(i * 100), resources.get(i)));
			sleep(100L); // it takes a while for all scheduled tasks to kick in.
			log.debug("Expecting half of the pools to be removed.");
			assertEquals(testSize - releaseHalf, pools.getSize());
			IntStream.range(releaseHalf, testSize).forEach(i -> pools.release(Integer.toString(i * 100), resources.get(i)));
			sleep(100L);
			assertTrue(pools.isEmpty());
		} finally {
			pools.close();
		}
		try {
			assertFalse("PoolPruner must have stopped after all pools-map is closed.", PoolPruner.getInstance().isRunning());
		} catch (AssertionError e) {
			PoolPruner.getInstance().stop();
			throw e;
		}
	}
	
	static void sleep(long sleepTime) {
		try {
			Thread.sleep(sleepTime);
		} catch (Exception e) {
			log.error("Sleep interrupted.");
		}
	}

	static class PoolsMapFactory implements IPoolsMapFactory<Long, String> {

		protected Logger log = LoggerFactory.getLogger(getClass());
		
		long idleTimeOut, pruneInterval;

		public PoolsMapFactory() {
			this(PrunedPool.DEFAULT_MAX_IDLE_TIME, PrunedPool.DEFAULT_PRUNE_INTERVAL);
		}

		public PoolsMapFactory(long idleTimeOut, long pruneInterval) {
			this.idleTimeOut = idleTimeOut;
			this.pruneInterval = pruneInterval;
		}

		@Override
		public PrunedPool<Long> create(String poolKey, PoolPruner poolPruner) {
			
			PrunedPool<Long> p = new PrunedPool<>();
			p.setPoolName(poolKey);
			p.setFactory(new PoolFactory(poolKey));
			p.setFair(true);
			p.setMaxIdleTimeMs(idleTimeOut);
			p.setPruneIntervalMs(pruneInterval);
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

		static final AtomicLong factoryCounter = new AtomicLong();
		
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
			return startAt + factoryCounter.incrementAndGet();
		}
		
	}
}
