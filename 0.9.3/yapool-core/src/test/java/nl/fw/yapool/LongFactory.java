package nl.fw.yapool;

import java.util.concurrent.atomic.AtomicLong;

import nl.fw.yapool.IPoolFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongFactory implements IPoolFactory<Long> {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public AtomicLong createCount = new AtomicLong();
	
	public void setStartValue(long start) {
		createCount.set(start);
	}
	
	@Override
	public Long create() {
		return createCount.incrementAndGet();
	}

	@Override
	public boolean isValid(Long resource) { 
		
		if (log.isTraceEnabled()) {
			log.trace("resource validated: " + resource);
		}
		return (resource != null); 
	}

	@Override
	public void destroy(Long resource) {

		if (log.isTraceEnabled()) {
			log.trace("resource destroyed: " + resource);
		}
	}

}
