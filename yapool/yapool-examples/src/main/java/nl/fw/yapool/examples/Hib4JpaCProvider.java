package nl.fw.yapool.examples;

import java.util.Map;
import java.util.Properties;

import org.hibernate.cfg.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.fw.yapool.listener.PoolEventLogger;
import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;
import nl.fw.yapool.sql.hibernate.Hib4ConnectionProvider;

public class Hib4JpaCProvider extends Hib4ConnectionProvider {

	private static final long serialVersionUID = 5650209473144698010L;

	public static final String LOG_CATEGORY = "yapool.example.hib4jpa.cp";

	private static final Logger log = LoggerFactory.getLogger(LOG_CATEGORY);

	public static final String poolId = "Hib4Jpa";
	
	// Set true to see what is happening in the pool. NOT for production use.
	public static boolean logPoolEvents;
	
	@SuppressWarnings( {"rawtypes"})
	@Override
	public void configure(Map properties) {

		// General setup.
		
		props = new Properties();
		props.putAll(getHibernateJpaProps(properties, false));
		pool = new SqlPool();
		pool.setPoolName(poolId);
		getProvidedPools().addPool(poolId, pool);
		SqlFactory factory = new SqlFactory();
		pool.setFactory(factory);
		factory.setPoolName(poolId);
		
		// Setup factory.
		
		factory.setJdbcDriverClass(props.getProperty(Environment.DRIVER));
		factory.setAutoCommit("true".equalsIgnoreCase(props.getProperty(Environment.AUTOCOMMIT)));
		
		// Typically you do NOT put the following three parameters in a persistence.xml
		// but load them from a configuration file specific to the test/acceptance/production environment.
		
		factory.setJdbcUrl(props.getProperty(Environment.URL));
		factory.getConnectionProps().setProperty("user", props.getProperty(Environment.USER));
		String pass = props.getProperty(Environment.PASS);
		if (pass == null) {
			pass = "";
		}
		factory.getConnectionProps().setProperty("password", pass);
		
		// POOL_SIZE is actually for the internal Hibernate pool, but why not use it here.
		pool.setMaxSize(Integer.valueOf(props.getProperty(Environment.POOL_SIZE)));
		
		if (logPoolEvents) {
			pool.getEvents().addPoolListener(new PoolEventLogger());
		}
		
		log.info("SqlPool configured for Hibernate4Jpa example, using JDBC URL " + factory.getJdbcUrl());
	}
	
	@Override
	public void stop() {
		
		super.stop();
		log.info("SqlPool closed.");
	}

	/**
	 * Convenience method for getting the SqlPool create by this class.
	 */
	public static SqlPool getPool() {
		return getProvidedPools().get(poolId);
	}
	
}
