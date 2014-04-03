package nl.fw.yapool.sql.datasource;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.sql.DataSource;

/**
 * A proxy for {@link DataSource} backed by a {@link SqlProxyPool}.
 * <br>Proxy is used to prevent compile errors ({@link DataSource} changed between Java 1.6 and Java 1.7).
 * <br>Connections requested from this DataSource are taken from the pool 
 * and are also proxied (see {@link ConnectionProxy}) so that
 * connections are returned to the pool instead of closed.
 *
 * @author FWiers
 */
public class DataSourceProxy extends MockByProxy {
	
	protected SqlProxyPool pool;
	
	public DataSourceProxy(SqlProxyPool pool) {
		super();
		this.pool = pool;
	}

	@Override
	public Object call(Method method, Object[] args) throws Throwable {
		
		String methodName = method.getName();
		if ("getConnection".equals(methodName)) {
			if (args != null && args.length == 2) {
				if (args[0] != null && !pool.getFactory().getUser().equals(args[0])) {
					throw new UnsupportedOperationException("getConnection with different username [" + args[0] + "] is not supported.");
				}
			}
			return pool.acquire();
		}
		return null;
	}
	
	@Override
	protected Object invokeObjectMethod(Method method, Object[] args) {
		return ProxyUtil.invokeObjectMethod(method, args, pool);
	}

	
	/**
	 * Closes the pool.
	 * This method can be reached by using DataSource returned from the {@link DataSourceFactory} in
	 * {@link Proxy#getInvocationHandler(Object)}, casting to this class and calling this method.
	 */
	public void close() {
		pool.close();
	}
	
	/**
	 * Returns the used pool.
	 * See also {@link #close()} about how to reach this method.
	 */
	public SqlProxyPool getPool() {
		return pool;
	}
	
	/**
	 * Returns the number of connection proxies in cache.
	 */
	public int getProxyCount() {
		return pool.getProxyCount();
	}

}
