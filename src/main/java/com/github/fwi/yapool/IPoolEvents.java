package com.github.fwi.yapool;

/**
 * Generic interface for handling {@link IPoolListener}s and {@link PoolEvent}s.
 * @author Fred
 */
public interface IPoolEvents {

	/** 
	 * @return true if this a pool listener listens for the poolEvent.
	 */
	boolean wantEventAction(String poolEvent);
	void addPoolListener(IPoolListener listener);
	void removePoolListener(IPoolListener listener);
	void firePoolEvent(PoolEvent poolEvent);
	public void clearListeners();
	
	/* See IPoolListener, this could be usefull:
	 	void resetAll();
		void disableAll();
		void enableAll();
	 */
}
