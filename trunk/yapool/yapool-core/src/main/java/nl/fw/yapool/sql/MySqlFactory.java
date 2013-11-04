package nl.fw.yapool.sql;

import static nl.fw.yapool.BeanConfig.putIfNotExists;

import java.util.Properties;

/**
 * A MySql database connection factory.
 * @author Fred
 *
 */
public class MySqlFactory extends SqlFactory {
	
	public static final String POOL_NAME_TEST = "MySqlTestDb";
	
	/** com.mysql.jdbc.Driver */
	public static final String JDBC_DRIVER_CLASS = "com.mysql.jdbc.Driver";

	/** jdbc:mysql://localhost:3306/test */ 
	public static final String JDBC_URL_TEST_LOCAL = "jdbc:mysql://localhost:3306/test";

	/**
	 * Overwrite some default SqlFactory values to use more appropriate MySql defaults.
	 * More defaults can be set using {@link #applyStableConnectionProps(Properties)}.
	 * <br>Note that autocommit is default set to false.
	 */
	public MySqlFactory() {
		super();
		setJdbcDriverClass(JDBC_DRIVER_CLASS);
		setJdbcUrl(JDBC_URL_TEST_LOCAL);
		getConnectionProps().setProperty("user", "test");
		getConnectionProps().setProperty("password", "test");
		setPoolName(POOL_NAME_TEST);
	}
	
	/**
	 * Sets a collection of MySQL connection properties that help performance and stability
	 * (if a property is already set, the value is not overwritten).
	 * <br>Note that the properties set a maximum of 5 minutes that a query can run,
	 * if this is no good, update the property {@code socketTimeout}
	 * to an appropriate value (time in milliseconds).
	 * @param connectionProps the properties for {@link #getConnectionProps()}.
	 */
	public static void applyStableConnectionProps(Properties connectionProps) {
		
		// http://dev.mysql.com/doc/refman/5.1/en/connector-j-reference-configuration-properties.html
		// Prevent memory leaks
		putIfNotExists(connectionProps, "dontTrackOpenResources", "true");
		// Prevent authorization errors when using functions/procedures 
		putIfNotExists(connectionProps, "noAccessToProcedureBodies", "true");
		// Prevent waiting forever for a new connection, wait a max. of 30 seconds.
		putIfNotExists(connectionProps, "connectionTimeout", "30000");
		// Prevent waiting forever for an answer to a query, wait a max. of 5 minutes (300 seconds).
		// Note: this is a fallback, use Statement.setQueryTimeout() for better query time-out.
		putIfNotExists(connectionProps, "socketTimeout", "300000");
		// Omit unnecessary commit() and rollback() calls.
		putIfNotExists(connectionProps, "useLocalSessionState", "true");
		// Omit unnecessary "set autocommit n" calls (needed for Hibernate).
		putIfNotExists(connectionProps, "elideSetAutoCommits", "true");
		// Fetch database meta-data from modern place.
		putIfNotExists(connectionProps, "useInformationSchema", "true");
		// Prevent errors when fetching dates (needed for Hibernate with MyISAM).
		putIfNotExists(connectionProps, "useFastDateParsing", "false");
		// In case of failover, do not set the connection to read-only.
		putIfNotExists(connectionProps, "failOverReadOnly", "false");
	}

}
