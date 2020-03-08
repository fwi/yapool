package com.github.fwi.yapool;

import java.util.Collection;
import java.util.EventListener;

/**
 * Listener that can be added to a {@link IPoolEvents} registry
 * to receive {@link PoolEvent}s.
 * @author Fred
 *
 */
public interface IPoolListener extends EventListener {

	/**
	 * @return true if this pool listener wants to receive all pool events.
	 */
	boolean wantAllEventActions();
	/**
	 * @return collection of pool events that this pool listener wants to receive.
	 */
	Collection<String> getWantEventActions();
	
	/**
	 * @return true if this pool listener wants to receive the given pool event type.
	 */
	boolean wantsEventAction(String poolEventAction);
	
	/**
	 * Called by pool when a pool event occurs.
	 * @param poolEvent
	 */
	void onPoolEvent(PoolEvent poolEvent);
	
	/*
	 * This could be usefull ...
	 void enable(boolean listen);
	 void reset();
	 */

}
