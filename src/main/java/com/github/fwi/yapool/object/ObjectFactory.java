package com.github.fwi.yapool.object;

import com.github.fwi.yapool.IPoolFactory;

public class ObjectFactory<T> implements IPoolFactory<T> {

	private Class<T> objectClass;
	private String objectClassName;
	
	public ObjectFactory() {
		this(null);
	}

	public ObjectFactory(Class<T> objectClass) {
		setObjectClass(objectClass);
	}

	public Class<T> getObjectClass() {
		return objectClass;
	}
	
	/**
	 * The class used to create new object instances from.
	 * If {@link #getObjectClassName()} returns null, {@link #setObjectClassName(String)} is called with the class name.
	 */
	public void setObjectClass(Class<T> objectClass) {
		
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
	@SuppressWarnings("unchecked")
	public void setObjectClassName(String objectClassName) {
		
		this.objectClassName = objectClassName;
		if (objectClassName != null && getObjectClass() == null) {
			try {
				setObjectClass((Class<T>) Class.forName(objectClassName));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	/**
	 * Creates an object of class {@link #getObjectClass()}.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T create() {
		
		Object o = null;
		try {
			o = getObjectClass().newInstance();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return (T) o;
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
