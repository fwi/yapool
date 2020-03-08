package com.github.fwi.yapool;

import com.github.fwi.yapool.BoundPool;

public class Bound extends BoundPool<Long> {
	
	public Bound() {
		super();
		setFactory(new LongFactory());
	}

	@Override
	public LongFactory getFactory() {
		return (LongFactory)super.getFactory();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
