package nl.fw.yapool.sql.datasource;

import java.lang.reflect.Method;

public class ProxyUtil {
	
	private ProxyUtil() {}
	
	
	/**
	 * @return true if method-call is an object-method like toString, hashCode or equals.
	 */
	public static boolean isObjectMethod(Method method, Object[] args) {

		String methodName = method.getName();
		return (("toString".equals(methodName) && args == null) 
				|| ("hashCode".equals(methodName) && args == null) 
				|| ("equals".equals(methodName) && args != null && args.length == 1));
	}
	
	/**
	 * Calls the object-method on target and returns the result.
	 */
	public static Object invokeObjectMethod(Method method, Object[] args, Object target) {
		
		String methodName = method.getName();
		if ("toString".equals(methodName)) {
			return target.toString();
		} 
		if ("hashCode".equals(methodName)) {
			return target.hashCode();
		} 
		if ("equals".equals(methodName)) {
			return target.equals(args[0]);
		}
		return null;
	}

	/**
	 * Converts null to 0 or false when return-class is a primitive.
	 * @param param type of return class
	 * @return default value for return class or null.
	 */
	public static Object getDefaultInstance(Class<?> param) {
		
        if (param == int.class)
            return new Integer(0);
        if (param == boolean.class)
            return Boolean.FALSE;
        if (param == byte.class)
            return new Byte("0");
        if (param == short.class)
            return new Short("0");
        if (param == long.class)
            return new Long("0");
        if (param == float.class)
            return new Float("0");
        if (param == double.class)
            return new Double("0");
        return null;
    }

}
