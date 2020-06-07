package com.github.fwi.yapool;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;

import com.github.fwi.yapool.PoolEvent;
import com.github.fwi.yapool.listener.PoolEventQueue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBasicPool {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * Checks that if 3 thread are using a resource from the pool,
	 * only 3 resources are created.
	 * Checks that events are fired.
	 */
	@Test
	public void basic() {
		
		PoolEventQueue events;
		final BasicPool p = TestUtil.createBasicPool(events = new PoolEventQueue());
		events.register = true;
		// create 3 threads constantly creating and releasing resources from the pool
		PoolRunnerStopper stopper = new PoolRunnerStopper();
		Thread[] t = new Thread[3]; 
		CountDownLatch latch = new CountDownLatch(3);
		for (int i = 0; i < t.length; i++) {
			t[i] = TestUtil.start(new PoolRunner<Long>(p, stopper), latch);
		}
		TestUtil.await(latch);
		// let it run
		TestUtil.sleep(50L);
		//log.debug(events.toString());
		assertEquals("Only 3 resources should have been created, serving 3 threads.", t.length, (int)p.getCreateCount());
		stopper.setStop(true);
		log.debug("Amount of events: " + events.queue.size());
		assertTrue(p.getCreatedCount() > 2);
		assertTrue(events.getCount(PoolEvent.CREATED) > 2);
		assertTrue(events.getCount(PoolEvent.ACQUIRING) > 10);
		assertTrue(events.getCount(PoolEvent.ACQUIRED) > 10);
		assertTrue(events.getCount(PoolEvent.RELEASING) > 10);
		// more event checking is done in TestBoundPool
		//log.debug(events.toString());
	}
	
}
