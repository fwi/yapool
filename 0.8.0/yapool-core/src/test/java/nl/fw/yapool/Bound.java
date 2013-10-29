package nl.fw.yapool;

import nl.fw.yapool.BoundPool;

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
