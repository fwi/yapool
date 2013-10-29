package nl.fw.yapool;

public class SleepFactory extends LongFactory {
	
	public static long createSleep = 10L;
	public static long validateSleep = 10L;
	public static long destroySleep = 10L;
	
	@Override
	public Long create() {

		TestUtil.sleep(createSleep);
		return super.create();
	}

	@Override
	public boolean isValid(Long resource) { 
		
		TestUtil.sleep(validateSleep);
		return super.isValid(resource);
	}

	@Override
	public void destroy(Long resource) {

		TestUtil.sleep(destroySleep);
		super.destroy(resource);
	}

}
