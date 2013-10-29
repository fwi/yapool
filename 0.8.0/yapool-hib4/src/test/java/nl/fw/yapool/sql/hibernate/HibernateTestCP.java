package nl.fw.yapool.sql.hibernate;

import java.util.Map;
import java.util.Properties;

import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;
import nl.fw.yapool.sql.datasource.HsqlPoolBuilder;
import nl.fw.yapool.sql.hibernate.Hib4ConnectionProvider;

import org.hibernate.cfg.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateTestCP extends Hib4ConnectionProvider {

	private static final long serialVersionUID = -1738074832972697023L;

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public static String poolId = HsqlPoolBuilder.JDBC_URL_TEST_IN_MEM + "Hib4";
	
	@SuppressWarnings( {"unchecked", "rawtypes"})
	@Override
	public void configure(Map properties) {
		
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
		log.info("SqlPool configure for Hibernate Test.");
	}
	
	@Override
	public void start() {
		log.info("SqlPool starting for Hibernate Test.");
		super.start();
	}
	
	@Override
	public void stop() {
		log.info("SqlPool stopping for Hibernate Test.");
		super.stop();
	}

}
