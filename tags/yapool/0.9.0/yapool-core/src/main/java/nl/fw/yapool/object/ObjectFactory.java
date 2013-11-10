package nl.fw.yapool.object;

import nl.fw.yapool.IPoolFactory;

public class ObjectFactory implements IPoolFactory<Object> {

	private Class<?> objectClass;
	private String objectClassName;
	
	public Class<?> getObjectClass() {
		return objectClass;
	}
	
	/**
	 * The class used to create new object instances from.
	 * If {@link #getObjectClassName()} returns null, {@link #setObjectClassName(String)} is called with the class name.
	 */
	public void setObjectClass(Class<?> objectClass) {
		
		this.objectClass = objectClass;
		if (objectClass != null && getObjectClassName() == null) {
			setObjectClassName(objectClass.getName());
		}
	}
	
	public String getObjectClassName() {
		return objectClassName;
	}
	
	/**
	 * The class name used to create new object instances from.
	 * If {@link #getObjectClass()} returns null, {@link #setObjectClass(Class)} is called with the class found for the class name.
	 */
	public void setObjectClassName(String objectClassName) {
		
		this.objectClassName = objectClassName;
		if (objectClassName != null && getObjectClass() == null) {
			try {
				setObjectClass(Class.forName(objectClassName));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	/**
	 * Creates an object of class {@link #getObjectClass()}.
	 */
	@Override
	public Object create() {
		
		Object o = null;
		try {
			o = getObjectClass().newInstance();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return o;
	}
	
	/** NO-OP. */
	@Override
	public boolean isValid(Object resource) {
		return true;
	}
	
	/** NO-OP. */
	@Override
	public void destroy(Object resource) {}

}
