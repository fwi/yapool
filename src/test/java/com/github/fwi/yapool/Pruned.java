package com.github.fwi.yapool;

import com.github.fwi.yapool.PrunedPool;

import org.slf4j.LoggerFactory;

public class Pruned extends PrunedPool<Long> {
	
	private static int poolInstanceCounter;
	
	private int poolNumber;
	
	public Pruned() {
		super();
		setFactory(new LongFactory());
		poolNumber = ++poolInstanceCounter;
		log = LoggerFactory.getLogger(getClass().getName() + ":" + poolNumber);
	}
	
	public int getNumber() { return poolNumber; }

	@Override
	public String toString() {
		return getClass().getSimpleName() + ":" + poolNumber;
	}

}
