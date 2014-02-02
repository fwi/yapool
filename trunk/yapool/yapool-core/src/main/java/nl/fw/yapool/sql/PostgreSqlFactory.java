package nl.fw.yapool.sql;

import java.util.Properties;

/**
 * A MySql database connection factory.
 * @author Fred
 *
 */
public class PostgreSqlFactory extends SqlFactory {
	
	public static final String POOL_NAME_TEST = "PostgreSqlTestDb";
	
	/** com.mysql.jdbc.Driver */
	public static final String JDBC_DRIVER_CLASS = "org.postgresql.Driver";

	/** jdbc:mysql://localhost:3306/test */ 
	public static final String JDBC_URL_TEST_LOCAL = "jdbc:postgresql://localhost:5432/test";

	/**
	 * Overwrite some default SqlFactory values to use more appropriate MySql defaults.
	 * More defaults can be set using {@link #applyStableConnectionProps(Properties)}.
	 * <br>Note that autocommit is default set to false.
	 */
	public PostgreSqlFactory() {
		super();
		setJdbcDriverClass(JDBC_DRIVER_CLASS);
		setJdbcUrl(JDBC_URL_TEST_LOCAL);
		getConnectionProps().setProperty("user", "test");
		getConnectionProps().setProperty("password", "test");
		setPoolName(POOL_NAME_TEST);
	}

}
