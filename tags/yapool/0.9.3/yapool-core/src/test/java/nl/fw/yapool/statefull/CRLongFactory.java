package nl.fw.yapool.statefull;

import java.util.concurrent.atomic.AtomicLong;

public class CRLongFactory extends CaptureRestoreFactory<Long>{

	public AtomicLong createCount = new AtomicLong();
	
	public void setStartValue(long start) {
		createCount.set(start);
	}

	@Override
	public Long createNew() {
		return createCount.incrementAndGet();
	}

	@Override
	public boolean isValid(Long resource) {
		return (resource != null);
	}

	@Override
	public void destroyNoCapture(Long resource) {
		// NO-OP
	}

}
