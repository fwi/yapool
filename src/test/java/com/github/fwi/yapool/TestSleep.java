package com.github.fwi.yapool;

import com.github.fwi.yapool.PoolEvent;
import com.github.fwi.yapool.listener.PoolEventQueue;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

public class TestSleep {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	@Test
	public void sleep() {
		
		PoolEventQueue events;
		Pruned p = TestUtil.createSleepPool(events = new PoolEventQueue());
		events.register = true;
		p.open();
		for (int i = 0; i < p.getMaxSize(); i++) {
			p.release(p.acquire());
			//log.debug("Idle: " + p.getIdleSize() + ", leased: " + p.getLeasedSize());
		}
		assertEquals(1, events.getCount(PoolEvent.CREATED));
		assertEquals(p.getMaxSize(), events.getCount(PoolEvent.ACQUIRED));
		p.close();
	}

}
