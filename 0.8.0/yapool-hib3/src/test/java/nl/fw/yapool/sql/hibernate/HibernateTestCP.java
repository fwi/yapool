package nl.fw.yapool.sql.hibernate;

import java.util.Properties;

import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;
import nl.fw.yapool.sql.datasource.HsqlPoolBuilder;
import nl.fw.yapool.sql.hibernate.Hib3ConnectionProvider;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateTestCP extends Hib3ConnectionProvider {

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public static String poolId = HsqlPoolBuilder.JDBC_URL_TEST_IN_MEM + "Hib3";
	
	@Override
	public void configure(final Properties properties) throws HibernateException {
		
		props = new Properties();
		props.putAll(properties);
		pool = new SqlPool();
		SqlFactory factory = new SqlFactory();
		pool.setFactory(factory);
		factory.setJdbcUrl(poolId);
		props.setProperty(Environment.URL, factory.getJdbcUrl());
		props.setProperty(Environment.AUTOCOMMIT, Boolean.toString(factory.isAutoCommit()));
		// Disable cache so that all expected queries are executed.
		props.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "false");
		props.setProperty(Environment.SHOW_SQL, "true");
		properties.putAll(props);
		getProvidedPools().addPool(poolId, pool);
		// There is no start method, must open pool in this method. 
		pool.open();
		log.info("SqlPool configured and opened for Hibernate Test.");
	}
	
}
