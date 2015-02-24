package nl.fw.yapool.sql.datasource;

import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;

import nl.fw.yapool.sql.SqlPool;

/**
 * A {@link SqlPool} that hands out proxies for {@link Connection}s (via {@link ConnectionProxy} instances).
 * Proxied connections release themselves back into the pool when they are closed by the user.
 * @author Fred
 *
 */
public class SqlProxyPool extends SqlPool {
	
	protected ConcurrentHashMap<Connection, ConnectionProxy> proxies = new ConcurrentHashMap<Connection, ConnectionProxy>(); 

	@Override
	public Connection acquire(long acquireTimeOutMs, long maxLeasedTimeMs) {
		return getProxyConnection(super.acquire(acquireTimeOutMs, maxLeasedTimeMs));
	}
	
	@Override
	protected void destroy(Connection c) {
		super.destroy(c);
		proxies.remove(c);
	}
	
	protected Connection getProxyConnection(Connection real) {
		
		ConnectionProxy proxy = proxies.get(real);
		if (proxy == null) {
			proxy = new ConnectionProxy(this, real);
			proxies.put(real, proxy);
		}
		proxy.setClosed(false);
		return proxy.getProxyConnection();
	}
	
	/**
	 * Returns the number of connection proxies in cache.
	 */
	public int getProxyCount() {
		return proxies.size();
	}

}
