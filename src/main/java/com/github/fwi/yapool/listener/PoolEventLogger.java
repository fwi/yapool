package com.github.fwi.yapool.listener;

import com.github.fwi.yapool.PoolEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolEventLogger extends PoolListener {

	protected Logger log = LoggerFactory.getLogger(getClass());

	public boolean silent;
	
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		if (!silent) {
			log.info(poolEvent.getSource() + " " + poolEvent);
		}
	}

}
