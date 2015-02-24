package nl.fw.yapool.sql;

/**
 * A PostgreSQL database connection factory.
 * @author Fred
 *
 */
public class MariaDbFactory extends SqlFactory {
	
	public static final String POOL_NAME_TEST = "MariaTestDb";
	
	/** org.mariadb.jdbc.Driver */
	public static final String JDBC_DRIVER_CLASS = "org.mariadb.jdbc.Driver";

	/** 
	 * jdbc:mariadb://localhost:3306/test
	 * <br>use of <tt>mariadb</tt> is optional, it can be replaced with <tt>mysql</tt>
	 * <br>see also https://mariadb.com/kb/en/about-the-mariadb-java-client/ 
	 */ 
	public static final String JDBC_URL_TEST_LOCAL = "jdbc:mariadb://localhost:3306/test";

	/**
	 * Overwrite some default SqlFactory values to use more appropriate PostgreSQL defaults.
	 * <br>Note that autocommit is default set to false.
	 */
	public MariaDbFactory() {
		super();
		setJdbcDriverClass(JDBC_DRIVER_CLASS);
		setJdbcUrl(JDBC_URL_TEST_LOCAL);
		getConnectionProps().setProperty("user", "test");
		getConnectionProps().setProperty("password", "test");
		setPoolName(POOL_NAME_TEST);
	}

}
