package com.github.fwi.yapool;

import com.github.fwi.yapool.listener.PoolEventQueue;
import static com.github.fwi.yapool.PoolEvent.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEventActionFilter {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Check that fire-event is only called when needed.
	 * The purpose of "wantEvent" is to reduce the number of PoolEvent ojects created.
	 */
	@Test
	public void actionFilter() {
		
		final BasicFirePool p = new BasicFirePool();
		assertFalse(p.getEvents().wantEventAction(ACQUIRED));
		PoolEventQueue events1 = new PoolEventQueue() {{ addWantEvent(CREATED); register = true; }};
		assertFalse(events1.wantAllEventActions());
		p.getEvents().addPoolListener(events1);
		assertFalse(p.getEvents().wantEventAction(ACQUIRED));
		PoolEventQueue events2 = new PoolEventQueue() {{ addWantEvent(RELEASING); register = true; }};
		p.getEvents().addPoolListener(events2);
		
		assertTrue(p.getEvents().wantEventAction(CREATED));
		assertTrue(p.getEvents().wantEventAction(RELEASING));
		assertFalse(p.getEvents().wantEventAction(DESTROYING));
		
		p.release(p.acquire());
		log.debug(p.peq.toString());
		assertEquals("Check that fire-event in pool was not called", 0, p.peq.getCount(ACQUIRED));
		p.release(p.acquire());
		
		log.debug(events1.toString());
		log.debug(events2.toString());
		assertEquals(1, events1.queue.size());
		assertEquals(2, events2.queue.size());
		
		PoolEventQueue eventsAll = new PoolEventQueue() {{ register = true; }};
		p.getEvents().addPoolListener(eventsAll);
		assertTrue(p.getEvents().wantEventAction(ACQUIRED));
		p.release(p.acquire());
		assertEquals("Check that fire-event in pool was called", 1, p.peq.getCount(ACQUIRED));
		p.close();
		log.debug(eventsAll.toString());
		assertTrue(eventsAll.queue.size() > 3);
	}

}
