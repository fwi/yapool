package nl.fw.yapool.sql.datasource;

import java.util.Properties;

import static nl.fw.yapool.BeanConfig.*;

import nl.fw.yapool.BeanConfig;
import nl.fw.yapool.sql.MySqlFactory;

public class MySqlPoolBuilder implements IPoolBuilder {

	/**
	 * Opens a MySQL database connection pool connecting to {@link MySqlFactory#JDBC_URL_TEST_LOCAL}.
	 * Does not overwrite any given properties, i.e. if <code>jdbcUrl</code> is
	 * set to something other then {@link MySqlFactory#JDBC_URL_TEST_LOCAL}, that value will be preserved.
	 * <br>Note that this pool has <code>autoCommit</code> set to false by default.
	 * <br>Calls {@link #applyStableConnectionProps(Properties)} for additional connection properties.
	 */
	@Override
	public SqlProxyPool buildPool(Properties props) {

		SqlProxyPool pool = new SqlProxyPool();
		pool.setFactory(new MySqlFactory());
		applyStableConnectionProps(props);
		BeanConfig.configure(pool, props);
		BeanConfig.configure(pool.getFactory(), props);
		pool.open();
		return pool;
	}
	
	/**
	 * Sets a collection of MySQL connection properties that help performance and stability
	 * (if a property is already set, the value is not overwritten).
	 * All properties are prefixes with "{@code connection.}" so that they can be
	 * use with {@link BeanConfig#configure(Object, java.util.Map)} for the SqlFactory bean.
	 * <br>Note that the properties set a maximum of 5 minutes that a query can run,
	 * if this is no good, update the property <code>connection.socketTimeout</code>
	 * to an appropriate value (time in milliseconds).
	 * 
	 */
	public static void applyStableConnectionProps(Properties props) {
		
		// http://dev.mysql.com/doc/refman/5.1/en/connector-j-reference-configuration-properties.html
		// Prevent memory leaks
		putIfNotExists(props, "connection.dontTrackOpenResources", "true");
		// Prevent authorization errors when using functions/procedures 
		putIfNotExists(props, "connection.noAccessToProcedureBodies", "true");
		// Prevent waiting forever for a new connection, wait a max. of 30 seconds.
		putIfNotExists(props, "connection.connectionTimeout", "30000");
		// Prevent waiting forever for an answer to a query, wait a max. of 5 minutes (300 seconds).
		// Note: this is a fallback, use Statement.setQueryTimeout() for better query time-out.
		putIfNotExists(props, "connection.socketTimeout", "300000");
		// Omit unnecessary commit() and rollback() calls.
		putIfNotExists(props, "connection.useLocalSessionState", "true");
		// Omit unnecessary "set autocommit n" calls (needed for Hibernate).
		putIfNotExists(props, "connection.elideSetAutoCommits", "true");
		// Fetch database meta-data from modern place.
		putIfNotExists(props, "connection.useInformationSchema", "true");
		// Prevent errors when fetching dates (needed for Hibernate with MyISAM).
		putIfNotExists(props, "connection.useFastDateParsing", "false");
		// In case of failover, do not set the connection to read-only.
		putIfNotExists(props, "connection.failOverReadOnly", "false");
	}

}
