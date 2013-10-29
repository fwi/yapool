package nl.fw.yapool.sql;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import nl.fw.yapool.IPoolFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlFactory implements IPoolFactory<Connection> {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private String jdbcDriverClass;
	
	private String jdbcUrl;
	private String poolName;
	private int transactionIsolation;
	private Properties connectionProps;
	private boolean autoCommit;
	private int validateTimeOutS;
	
	/** Sets some default bean-values for a HSQL database. */
	public SqlFactory() {
		super();
		setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		setValidateTimeOutS(3);
		setJdbcDriverClass("org.hsqldb.jdbc.JDBCDriver");
		setJdbcUrl("jdbc:hsqldb:mem:test");
		Properties p = new Properties();
		p.setProperty("user", "SA");
		p.setProperty("password", "");
		setConnectionProps(p);
		setPoolName(getClass().getSimpleName() + "[" + hashCode() + "]");
	}
	
	/**
	 * This method should be called once to load the database driver.
	 * @throws RuntimeException if loading the driver failed.
	 */
	public Class<?> loadDbDriver()  {
		
		Class<?> driverClass = null;
		try {
			driverClass = Class.forName(getJdbcDriverClass());
			driverClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Unable to find JDBC database driver " + getJdbcDriverClass(), e);
		}
		return driverClass;
	}
	
	/**
	 * Creates a connection. This method is called synchronized from DbPool
	 * (i.e. no need to synchronize anything further).
	 */
	@Override
	public Connection create() {
		
		Connection c = null;
		boolean OK = false;
		try {
			c = DriverManager.getConnection(getJdbcUrl(), getConnectionProps());
			c.setAutoCommit(isAutoCommit());
			c.setTransactionIsolation(getTransactionIsolation());
			OK = true;
			if (log.isDebugEnabled()) {
				log.debug("Created a database connection for " + getPoolName());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (!OK) {
				destroy(c, false);
			}
		}
		return c;
	}
	
	@Override
	public boolean isValid(Connection c) {

		boolean valid = false;
		try {
			valid = c.isValid(getValidateTimeOutS());
		} catch (SQLException e) {
			log.info("Database connection invalid.", e);
		}
		return valid;
	}
	
	@Override
	public void destroy(Connection c) {
		destroy(c, !isAutoCommit());
	}
	
	protected void destroy(Connection c, boolean rollback) {

		if (c == null) return;
		try {
			boolean closed = c.isClosed();
			if (closed) {
				if (log.isDebugEnabled()) {
					log.debug("Database connection was already closed for pool " + getPoolName());
				}
				return;
			}
			setLowSocketTimeOut(c);
			if (rollback) { 
				try { 
					if (!c.getAutoCommit()) {
						c.rollback();
					}
				} catch (SQLException se) {
					log.warn("Failed to call rollback on a database connection about to be closed: " + se);
				}
			}
			c.close();
			if (log.isDebugEnabled()) {
				log.debug("Closed a database connection for " + getPoolName());
			}
		} catch (SQLException sqle) {
			log.warn("Failed to properly close a database connection: " + sqle);
		}
	}
	
	/**
	 * When rolling back and closing a database connection, make sure this does not hang on a connection issue.
	 * If "setSocketTimeout(int)" method exists, set it to 1000 milliseconds.
	 */
	protected void setLowSocketTimeOut(Connection c) {
		
		try {
			Method m = c.getClass().getMethod("setSocketTimeout", Integer.class);
			m.invoke(c, 1000);
			log.trace("Socket time-out for closing database connection set to 1000 milliseconds.");
		} catch (Exception ignored) {}
	}

	/* *** bean methods *** */

	public String getJdbcDriverClass() {
		return jdbcDriverClass;
	}
	public void setJdbcDriverClass(String jdbcDriverClass) {
		this.jdbcDriverClass = jdbcDriverClass;
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	public String getPoolName() {
		return poolName;
	}
	public void setPoolName(String name) {
		this.poolName = name;
	}

	public int getTransactionIsolation() {
		return transactionIsolation;
	}
	public void setTransactionIsolation(int transactionIsolation) {
		this.transactionIsolation = transactionIsolation;
	}
	
	/**
	 * User or username is usually stored in the connection properties.
	 * @return the user or an empty string.
	 */
	public String getUser() {
		
		if (connectionProps == null) return "";
		String user = connectionProps.getProperty("user");
		if (user == null) {
			user = connectionProps.getProperty("username");
		}
		return (user == null ? "" : user);
	}

	public Properties getConnectionProps() {
		return connectionProps;
	}
	public void setConnectionProps(Properties connectionProps) {
		this.connectionProps = connectionProps;
	}

	public boolean isAutoCommit() {
		return autoCommit;
	}
	public void setAutoCommit(boolean autoCommit) {
		this.autoCommit = autoCommit;
	}

	public int getValidateTimeOutS() {
		return validateTimeOutS;
	}
	public void setValidateTimeOutS(int validateTimeOutS) {
		this.validateTimeOutS = validateTimeOutS;
	}

}
