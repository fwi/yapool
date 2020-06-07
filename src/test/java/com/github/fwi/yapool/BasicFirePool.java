package com.github.fwi.yapool;

import com.github.fwi.yapool.PoolEvent;
import com.github.fwi.yapool.listener.PoolEventQueue;

/**
 * Sets a pool-event queue on the fireEvent method.
 * This makes it possible to look inside the pool fire-event logic. 
 * @author Fred
 *
 */
public class BasicFirePool extends BasicPool {

	public PoolEventQueue peq = new PoolEventQueue() {{ register = true; }};
	
	@Override
	protected void fireEvent(PoolEvent pe) {
		
		peq.onPoolEvent(pe);
		super.fireEvent(pe);
	}

}
