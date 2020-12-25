package com.github.fwi.yapool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fwi.yapool.listener.PoolEventQueue;

public class TestPrunedPool {

	protected Logger log = LoggerFactory.getLogger(getClass());

	@SuppressWarnings("unused")
	@Test
	public void invalidResource() {

		// Check that an invalid resource is handled without the caller noticing.
		PoolEventQueue events;
		Pruned p = TestUtil.createPrunedPool(events = new PoolEventQueue());
		NpeFactory f;
		p.setFactory(f = new NpeFactory());
		events.register = true;
		events.logEvent = true;
		p.open(0);
		NpeFactory.npeForValidate.set(1);
		Long l = p.acquire();
		//log.debug("A: " + l);
		// validation is always called, also for a new resource
		assertEquals(0, NpeFactory.npeForValidate.get());
		assertEquals(Long.valueOf(2L), l);
		p.release(l);
		Long l2;
		NpeFactory.npeForValidate.set(1);
		assertEquals(Long.valueOf(3L), p.acquire());
		assertEquals(0, NpeFactory.npeForValidate.get());
		//log.info(events.toString());
	}

	@Test
	public void startStop() {

		Pruned p = TestUtil.createPrunedPool(null);
		p.open(1);
		SharedTestPruner t = TestUtil.runPruner(p);
		p.close();
		TestUtil.sleep(50);
		assertFalse(t.isRunning());
	}

	@Test
	public void evictTimeout() {

		PoolEventQueue events;
		Pruned p = TestUtil.createPrunedPool(events = new PoolEventQueue());
		p.setMinSize(1);
		StartTraceTest st;
		p.getEvents().addPoolListener(st = new StartTraceTest(p));
		//st.showLog = true;
		p.setPruneIntervalMs(1L);
		p.setMaxLeaseTimeMs(80L);
		//p.setLogLeaseExpiredTraceAsWarn(true);
		p.open(0);
		//events.register = true;
		Long[] l = new Long[p.getMaxSize()];
		for (int i = 0; i < p.getMaxSize(); i++) l[i] = p.acquire();
		//events.queue.clear();
		events.register = true;
		assertEquals(p.getMaxSize(), st.getSize());
		TestUtil.runPruner(p);
		//log.info(p.toStatus());
		TestUtil.sleep(50L);
		// time-out not reached yet
		assertEquals(0, events.getCount(PoolEvent.LEASE_EXPIRED));
		assertEquals(0, st.logAmount);

		//System.out.println(st.toString());

		TestUtil.sleep(50L);
		// max lease time-out reached
		events.register = false;
		assertEquals(p.getMaxSize(), events.getCount(PoolEvent.LEASE_EXPIRED));
		assertEquals(p.getMaxSize(), st.logAmount);
		assertEquals(p.getMaxSize(), p.getExpiredCount());
		//System.out.println(events);
		//log.info(pw.getStats(p).toString());
		assertEquals(0, p.getLeasedSize());

		// minimum size should be respected
		assertEquals(1, events.getCount(PoolEvent.CREATED));
		assertEquals(1, p.getIdleSize());

		// since they have expired, they should not return to pool but they should be destroyed
		p.setMaxLeaseTimeMs(1000L);
		events.queue.clear();
		events.register = true;
		for (int i = 0; i < p.getMaxSize(); i++) p.release(l[i]);
		events.register = false;
		assertEquals(1, p.getIdleSize());
		assertEquals(p.getMaxSize(), events.getCount(PoolEvent.DESTROYING));
		p.close();
		//log.info(p.toStatus());
	}

	@Test
	public void idledAfterOpen() {

		PoolEventQueue events;
		Pruned p  = TestUtil.createPrunedPool(events = new PoolEventQueue());
		p.setMinSize(2);
		p.setMaxSize(4);
		p.setPruneIntervalMs(5L);
		p.setMaxIdleTimeMs(5L);
		p.open(p.getMaxSize());
		events.register = true;
		//events.logEvent = true;
		TestUtil.runPruner(p);
		TestUtil.sleep(20L);
		// 2 resources should have idled, 2 should remain
		assertEquals(2, p.getSize());
		p.close();
	}

	@Test
	public void idled() {

		PoolEventQueue events;
		Pruned p  = TestUtil.createPrunedPool(events = new PoolEventQueue());
		p.setPruneIntervalMs(10L);
		p.setMaxIdleTimeMs(80L);
		p.open(0);
		// acquire all, then release half.
		Long[] l = new Long[p.getMaxSize()];
		for (int i = 0; i < p.getMaxSize(); i++) l[i] = p.acquire();
		int released = p.getMaxSize() / 2;
		for (int i = 0; i < released; i++) {
			p.release(l[i]);
			l[i] = null;
		}
		events.register = true;
		events.logEvent = true;
		TestUtil.runPruner(p);
		TestUtil.sleep(50L);
		// time-out not reached yet
		assertEquals(0, events.getCount(PoolEvent.IDLE_EXPIRED));
		assertEquals(0, p.getIdledCount());

		TestUtil.sleep(50L);
		// max idle time-out reached
		events.register = false;
		assertEquals(released, events.getCount(PoolEvent.IDLE_EXPIRED));
		assertEquals(released, p.getIdledCount());
		assertEquals(released, events.getCount(PoolEvent.DESTROYING));

		int inpool = p.getMaxSize() - released;
		assertEquals(inpool, p.getLeasedSize());
		assertEquals(0, p.getIdleSize());
		// Check that objects in pool are the ones we did not release.
		p.setMaxIdleTimeMs(1000L);
		for (int i = released; i < p.getMaxSize(); i++) p.release(l[i]);
		Set<Long> l2 = new HashSet<>();
		for (int i = 0; i < inpool; i++) l2.add(p.acquire());
		for (int i = released; i <  p.getMaxSize(); i++) assertTrue(l2.contains(l[i]));
		for (Long v : l2) p.release(v);
		p.close();
	}

	@Test
	public void interruptLeaser() {

		PoolEventQueue events;
		Pruned p = TestUtil.createPrunedPool(events = new PoolEventQueue());
		p.setPruneIntervalMs(1L);
		p.setMaxLeaseTimeMs(1L);
		p.setInterruptLeaser(true);
		//p.setLogLeaseExpiredTraceAsWarn(true);
		events.register = true;
		p.open(0);
		Long l = p.acquire();
		try {
			// shared test pruner may already be running 
			// and can therefor throw an interrupted-exception almost immediately 
			// (typically after a cold start with "mvn test").
			TestUtil.runPruner(p);
			Thread.sleep(50L);
			fail("Should have been interrupted.");
		} catch (InterruptedException ie) {
			log.debug("Interrupted: " + ie);
		}
		p.release(l);
		p.close();
		//log.debug(events.toString());
	}

	@Test
	public void maxLifeLeased() {

		PoolEventQueue events;
		Pruned p = TestUtil.createPrunedPool(events = new PoolEventQueue());
		p.setPruneIntervalMs(1L);
		events.register = true;
		//events.logEvent = true;
		p.open(0);
		p.setMaxLifeTimeMs(5L);
		Long l = p.acquire();
		TestUtil.runPruner(p);
		TestUtil.sleep(20L);
		p.release(l);
		p.close();
		assertEquals(1, p.getLifeEndCount());
	}

}
