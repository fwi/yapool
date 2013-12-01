package nl.fw.yapool;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpeFactory extends LongFactory {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public static boolean npeForAll;
	public static boolean npeForCreate;
	/** returns false for isValid() if greater > 1, is decreased everytime isValid() is called */
	public static AtomicInteger npeForValidate = new AtomicInteger();
	public static boolean npeForDestroy;
	
	@Override
	public Long create() {
		
		if (npeForAll || npeForCreate) {
			throw new NullPointerException();
		}
		return super.create();
	}

	@Override
	public boolean isValid(Long resource) { 
		
		if (npeForAll) {
			throw new NullPointerException();
		}
		if (npeForValidate.get() > 0) {
			int i = npeForValidate.getAndDecrement();
			if (i > 0) { 
				return false;
			}
		}
		return super.isValid(resource); 
	}

	@Override
	public void destroy(Long resource) {

		if (npeForAll || npeForDestroy) {
			throw new NullPointerException();
		}
		super.destroy(resource);
	}
}
