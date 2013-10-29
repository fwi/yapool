package nl.fw.yapool.sql.hibernate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import nl.fw.yapool.sql.hibernate.HibernateConnectionProvider;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.connection.ConnectionProvider;

/**
 * Wrapper when using Hibernate, see TestHibernate for usage.
 * @author frederikw
 *
 */
public class Hib3ConnectionProvider extends HibernateConnectionProvider implements ConnectionProvider {

	@Override
	public void configure(final Properties properties) throws HibernateException {
		
		props = new Properties();
		props.putAll(properties);
		String dbUrl = props.getProperty(Environment.URL);
		pool = getProvidedPools().get(dbUrl);
		if (pool == null) {
			throw new HibernateException("Could not find a connection pool for URL " + dbUrl);
		}
	}

	@Override
	public Connection getConnection() throws SQLException {	
		return pool.acquire(); 
	}

	@Override
	public void closeConnection(final Connection c) throws SQLException { 
		pool.release(c);
	}

	@Override
	public void close() throws HibernateException { 
		pool.close(); 
		getProvidedPools().removePool(pool);
	}

	/** Returns false which means most JTA features are not used by Hibernate. */
	@Override
	public boolean supportsAggressiveRelease() { 
		return false;
	}
	
}
