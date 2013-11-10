package nl.fw.yapool.sql.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to mock interfaces.
 * <br>Usefull in case java.sql-interfaces need to be mocked.
 * These interfaces have changed between Java 1.6 and Java 1.7
 * which can give compile errors.
 * Proxy-classes do not have this problem: they proxy/mock anything at runtime.
 *  
 * @author FWiers
 *
 */
public class MockByProxy implements InvocationHandler {

	protected Logger log = LoggerFactory.getLogger(this.getClass());

	/** 
	 * Mocks an interface (classToMock must be an interface).
	 * To retrieve this proxy-class associated with the returned object, 
	 * use {@link Proxy#getInvocationHandler(Object)}.
	 */
	public <T> T getInstance(Class<T> classToMock) {
		// Copied from http://www.kdgregory.com/index.php?page=junit.proxy
		return classToMock.cast(
				Proxy.newProxyInstance (
						this.getClass().getClassLoader(),
						new Class[] {classToMock},
						this));
	}

	/**
	 * First calls {@link #isObjectMethod(Method, Object[])}, if that returns true,
	 * {@link #invokeObjectMethod(Method, Object[])} is called.
	 * Else {@link #call(Method, Object[])} is called.
	 * <br>If the result of any of the called methods mentioned above is null, 
	 * and the method should return a primitive, the value is converted to 0 or false
	 * (see also {@link ProxyUtil#getDefaultInstance(Class)}. 
	 */
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		
		Object result = null;
		if (isObjectMethod(method, args)) {
			result =  invokeObjectMethod(method, args);
		} else {
			result = call(method, args);
		}
		return (result == null ? ProxyUtil.getDefaultInstance(method.getReturnType()) : result);
	}
	
	/**
	 * Overload/extend this method to do something with method-calls.
	 * This method gets called by the invoke-method for non-object methods (i.e. toString, hashCode and equals are handled by the invoke method itself).
	 * <br> If this method returns null but the proxied method requires a primitive return value, 
	 * the invoke-method will convert null to 0 or false of the correct primitive type. 
	 */
	public Object call(Method method, Object[] args) throws Throwable {
		return null;
	}
	
	/**
	 * Calls {@link ProxyUtil#isObjectMethod(Method, Object[])}.
	 */
	protected boolean isObjectMethod(Method method, Object[] args) {
		return ProxyUtil.isObjectMethod(method, args);
	}
	
	/**
	 * Calls {@link ProxyUtil#invokeObjectMethod(Method, Object[], Object)} with target set to this.
	 * Must overload this method if an underlying object is used.
	 */
	protected Object invokeObjectMethod(Method method, Object[] args) {
		return ProxyUtil.invokeObjectMethod(method, args, this);
	}
	
	/** Writes a debug-statement about the call. */
	protected void logCall(String methodName, Object[] args) {
		logCall(methodName, args, false);
	}
	
	protected void logCall(String methodName, Object[] args, boolean info) {
		
		if (info && !log.isDebugEnabled()) return;
		String msg = "Method [" + methodName + "] called with " + (args == null ? "0" : Integer.toString(args.length)) + " arguments.";
		if (info) {
			log.info(msg);
		} else {
			log.debug(msg);
		}
	}
	
}
