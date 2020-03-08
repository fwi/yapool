package com.github.fwi.yapool;

import java.util.concurrent.CountDownLatch;

import com.github.fwi.yapool.BoundPool;
import com.github.fwi.yapool.PoolEvent;
import com.github.fwi.yapool.listener.PoolEventQueue;
import com.github.fwi.yapool.listener.PoolPerformance;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.fwi.yapool.PoolEvent.*;
import static org.junit.Assert.*;

public class TestBoundPool {

	protected Logger log = LoggerFactory.getLogger(getClass());

	@Test 
	public void failOpen() {
		
		BoundPool<Long> p = new BoundPool<Long>();
		// There is no factory set, should throw NullPointerException.
		try {
			p.open(1);
		} catch (Exception e) {
			assertTrue(e.toString().contains("factory is required"));
		}
		p.setFactory(new NpeFactory());
		NpeFactory.npeForCreate = true;
		// Initial object creation may fail but pool is still open.
		p.open(1);
		assertTrue(p.isOpen());
		assertEquals(0, p.getSize());
		NpeFactory.npeForCreate = false;
	}
	
	@Test
	public void openLeaseAndClose() {
		
		Bound p = new Bound();
		PoolEventQueue events;
		p.getEvents().addPoolListener(events = new PoolEventQueue());
		events.register = true;
		//events.logEvent = true;
		p.open(1);
		assertFalse(p.isClosed());
		Long l = p.acquire();
		assertHaveEvent(events, CREATED, l);
		assertHaveEvent(events, OPENED);
		assertHaveEvent(events, ACQUIRING);
		assertHaveEvent(events, ACQUIRED, l);
		assertFalse(l == null);
		assertEquals(1, p.getLeasedSize());
		p.release(l);
		assertHaveEvent(events, RELEASING, l);
		assertEquals(1, p.getIdleSize());
		assertEquals(0, p.getLeasedSize());
		p.close();
		assertHaveEvent(events, CLOSED);
		assertHaveEvent(events, DESTROYING, l);
	}

	protected void assertHaveEvent(PoolEventQueue events, String action) {
		assertHaveEvent(events, action, null);
	}

	protected void assertHaveEvent(PoolEventQueue events, String action, Object resource) {
		
		PoolEvent e = events.queue.remove();
		assertEquals(action, e.getAction());
		assertEquals(resource, e.getResource());
	}
	
	@Test
	public void useMax() {
		
		Bound p = TestUtil.createPool(null);
		p.open();
		Long[] l = new Long[p.getMaxSize()];
		for (int i = 0; i < p.getMaxSize(); i++) l[i] = p.acquire();
		try {
			p.acquire();
		} catch (RuntimeException re) {
			assertTrue(re.toString().contains("Could not acquire"));
		}
		assertTrue(p.isFull());
		p.close();
	}

	@Test
	public void reuse() {
		
		Bound p = TestUtil.createPool(null);
		p.open();
		Long[] l = new Long[p.getMaxSize()];
		for (int i = 0; i < p.getMaxSize(); i++) l[i] = p.acquire();
		p.release(l[0]);
		assertEquals(l[0], p.acquire());
		p.close();
	}
	
	@Test
	public void releaseAfterClose() {
		
		PoolEventQueue events;
		Bound p = TestUtil.createPool(events = new PoolEventQueue());
		p.open();
		Long l = p.acquire();
		events.register = true;
		p.close();
		p.release(l);
		//log.info(events.toString());
		assertHaveEvent(events, CLOSED);
		assertHaveEvent(events, DESTROYING, l);
	}
	
	@Test 
	public void singletonPool() {
		
		PoolEventQueue events;
		PoolPerformance pp;
		Bound p = TestUtil.createPool(events = new PoolEventQueue());
		p.getEvents().addPoolListener(pp = new PoolPerformance(p));
		events.register = true;
		p.setMinSize(1);
		p.setMaxSize(1);
		p.setFair(true);
		p.setMaxAcquireTimeMs(100L);
		p.open();
		PoolRunnerStopper stopper = new PoolRunnerStopper();
		Thread[] t = new Thread[3];
		CountDownLatch latch = new CountDownLatch(3);
		for (int i = 0; i < t.length; i++) {
			t[i] = TestUtil.start(new PoolRunner<Long>(p, stopper), latch);
		}
		TestUtil.await(latch);
		TestUtil.sleep(200L);
		stopper.setStop(true);
		assertEquals(1, p.getFactory().createCount.get());
		log.debug("Amount of events: " + events.queue.size());
		p.close();
		log.info(pp.toString());
	}
	
}
