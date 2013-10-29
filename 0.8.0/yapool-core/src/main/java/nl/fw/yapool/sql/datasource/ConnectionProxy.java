package nl.fw.yapool.sql.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import nl.fw.yapool.sql.SqlPool;

/**
 * Proxy for a java.sql.Connection: "close connection" is converted to "release to pool". 
 * <br>The java.sql.Connection interface/implementation changes with every Java update.
 * A proxy is slow (about 300 nano-seconds extra per invocation) but flexible and
 * does not suffer from Java version differences at compile time.
 * @author fwiers
 *
 */
public class ConnectionProxy implements InvocationHandler {

	private final SqlPool pool;
	private final Connection target;
	private boolean closed;
	private Connection proxy;

	public ConnectionProxy(SqlPool pool, Connection c) {
		super();
		this.pool = pool;
		this.target = c;
		proxy = null;
	}
	
	/**
	 * Re-using proxies is potentially dangerous: 
	 * isClosed() on the proxy-Connection may return true if it was released and then re-used later.
	 * The first client to use the proxy-Connection can then suddenly get "false" for isClosed(),
	 * where it was "true" before. 
	 */
	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	/**
	 * Returns the proxy instance associated with the wrapped Connection.
	 * This is potentially dangerous (see comments on {@link #setClosed(boolean)}.
	 */
	public Connection getProxyConnection() {
		
		if (proxy == null) {
			proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), 
					new Class[] {Connection.class}, this);
		}
		return proxy;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		
		final Object result;
		if ("close".equals(method.getName())) {
			closed = true;
			result = pool.release(target);
		} else if ("isClosed".equals(method.getName())) {
			result = closed;
		} else {
			if (closed && !ProxyUtil.isObjectMethod(method, args)) {
				throw new SQLException("Database connection is closed.");
			}
			result = method.invoke(target, args);
		}
		return result;
	}
	
}
