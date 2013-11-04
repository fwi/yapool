package nl.fw.yapool.sql.hibernate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import nl.fw.yapool.sql.hibernate.HibernateConnectionProvider;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Startable;
import org.hibernate.service.spi.Stoppable;

/**
 * Wrapper when using Hibernate, see TestHibernate for usage.
 * Example copied from http://www.javalobby.org/java/forums/t18406.html
 * and
 * http://webcache.googleusercontent.com/search?q=cache:VHAngomSzWUJ:jeff.familyyu.net/2011/09/08/upgrading-hibernate-3-3-2-to-hibernate-4-0-0/+connectionprovider+hibernate+4+close&cd=1&hl=en&ct=clnk
 * @author frederikw
 *
 */
public class Hib4ConnectionProvider extends HibernateConnectionProvider implements ConnectionProvider, Configurable, Startable, Stoppable {

	private static final long serialVersionUID = 5671863092508823460L;

	@SuppressWarnings( {"unchecked", "rawtypes"})
	public void configure(Map properties) {
		
		props = new Properties();
		props.putAll(properties);
		String dbUrl = props.getProperty(Environment.URL);
		pool = getProvidedPools().get(dbUrl);
		if (pool == null) {
			throw new HibernateException("Could not find a connection pool for URL " + dbUrl);
		}
	}

	@Override
	public void start() {
		pool.open();
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
	public void stop() {
		pool.close(); 
	}

	/** Returns false which means most JTA features are not used by Hibernate. */
	@Override
	public boolean supportsAggressiveRelease() { 
		return false; 
	}

	@SuppressWarnings("rawtypes")
	public boolean isUnwrappableAs(Class unwrapType) {
		return ConnectionProvider.class.equals(unwrapType) 
				|| HibernateConnectionProvider.class.isAssignableFrom(unwrapType);
	}

	@SuppressWarnings({ "unchecked", "hiding" })
	@Override
	public <T> T unwrap(Class<T> unwrapType) {
		
		if (ConnectionProvider.class.equals(unwrapType) ||
				HibernateConnectionProvider.class.isAssignableFrom(unwrapType)) {
			return (T) this;
		} else {
			throw new UnknownUnwrapTypeException(unwrapType);
		}	
	}
}
