package com.github.fwi.yapool;

import com.github.fwi.yapool.Pool;

public class BasicPool extends Pool<Long> {
	
	public BasicPool() {
		super();
		setFactory(new LongFactory());
	}
	
	@Override
	public LongFactory getFactory() {
		return (LongFactory)super.getFactory();
	}
	
	public long getCreateCount() {
		return getFactory().createCount.get();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
