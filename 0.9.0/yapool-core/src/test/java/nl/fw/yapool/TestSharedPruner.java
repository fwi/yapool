package nl.fw.yapool;

import static org.junit.Assert.*;

import nl.fw.yapool.PoolEvent;
import nl.fw.yapool.PoolPruner;
import nl.fw.yapool.listener.PoolEventLogger;
import nl.fw.yapool.listener.PoolEventQueue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSharedPruner {

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	@Test
	public void removeIdle() {
		
		PoolEventQueue events;
		final PoolPruner pruner = PoolPruner.getInstance();
		PoolEventLogger logger, logger2;
		Pruned p = TestUtil.createPrunedPool(logger = new PoolEventLogger());
		p.getEvents().addPoolListener(events = new PoolEventQueue());
		Pruned p2 = TestUtil.createPrunedPool(logger2 = new PoolEventLogger());
		logger.silent = true;
		logger2.silent = true;
		
		p.setPruneIntervalMs(10L);
		p.setMaxLeaseTimeMs(1000L);
		p.setMaxIdleTimeMs(1L);
		p2.setPruneIntervalMs(10L);
		p2.setMaxLeaseTimeMs(1000L);
		p2.setMaxIdleTimeMs(1L);

		pruner.add(p);
		pruner.add(p2);
		TestUtil.sleep(10L);
		assertTrue(pruner.isRunning());
		p.open();
		p2.open();
		assertEquals(2, pruner.getSize());

		Long[] l = new Long[p.getMaxSize()];
		for (int i = 0; i < p.getMaxSize(); i++) l[i] = p.acquire();
		Long[] l2 = new Long[p2.getMaxSize()];
		for (int i = 0; i < p2.getMaxSize(); i++) l2[i] = p2.acquire();
		for ( Long lo : l) p.release(lo);
		for ( Long lo : l2) p2.release(lo);
		events.register = true;
		TestUtil.sleep(100L);
		events.register = false;
		assertEquals(0, p.getSize());
		assertEquals(0, p2.getSize());
		assertEquals(p.getMaxSize(), events.getCount(PoolEvent.IDLE_EXPIRED));
		assertEquals(p.getMaxSize(), p.getIdledCount());

		p.close();
		TestUtil.sleep(50L);
		assertTrue(pruner.isRunning());
		assertEquals(1, pruner.getSize());
		p2.close();
		TestUtil.sleep(50L);
		assertFalse(pruner.isRunning());
	}
}
