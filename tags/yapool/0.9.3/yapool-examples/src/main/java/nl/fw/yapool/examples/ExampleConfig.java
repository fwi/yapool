package nl.fw.yapool.examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

import nl.fw.yapool.BeanConfig;
import nl.fw.yapool.sql.MySqlFactory;
import nl.fw.yapool.sql.MySqlPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates the use of BeanConfig to configure a (MySQL) pool and pool factory.
 * The example has the following output: <pre>{@literal
34:40:728 [main] INFO yapool.example.config - Starting ExampleConfig
34:40:744 [main] INFO yapool.example.config - Configuring properties for nl.fw.yapool.sql.MySqlFactory@22db19d3
setPoolName to MyAppDb
setJdbcUrl to jdbc:mysql://localhost:3306/mydb-test
setConnectionProps to {user=mydb-user, password=secret}
34:40:744 [main] INFO yapool.example.config - Actual password: mydb-password
34:40:746 [main] INFO yapool.example.config - All properties from factory:
autoCommit=false
connection.connectionTimeout=30000
connection.dontTrackOpenResources=true
connection.elideSetAutoCommits=true
connection.failOverReadOnly=false
connection.noAccessToProcedureBodies=true
connection.password=secret
connection.socketTimeout=300000
connection.useFastDateParsing=false
connection.useInformationSchema=true
connection.useLocalSessionState=true
connection.user=mydb-user
jdbcDriverClass=com.mysql.jdbc.Driver
jdbcUrl=jdbc:mysql://localhost:3306/mydb-test
poolName=MyAppDb
transactionIsolation=2
validateTimeOutS=3
34:40:749 [main] INFO yapool.example.config - Configuring properties for MySqlTestDb
setMaxIdleTimeMs to 180000
setPoolName to MyAppDb
setMinSize to 2
setMaxSize to 10
setPruneIntervalMs to 3000
34:40:750 [main] INFO yapool.example.config - All properties from pool:
destroyOnExpiredLease=false
fair=true
interruptLeaser=false
logLeaseExpiredTrace=false
logLeaseExpiredTraceAsError=false
logLeaseExpiredTraceAsWarn=false
maxAcquireTimeMs=50000
maxIdleTimeMs=180000
maxLeaseTimeMs=300000
maxSize=10
minSize=2
poolName=MyAppDb
pruneIntervalMs=3000
syncCreation=true
34:40:750 [main] INFO yapool.example.config - Finished ExampleConfig
}</pre>
 * @author Fred
 *
 */
public class ExampleConfig {

	public static final String LOG_CATEGORY = "yapool.example.config";
	
	private static final Logger log = LoggerFactory.getLogger(LOG_CATEGORY);
	
	public static void main(String[] args) {

		log.info("Starting " + ExampleConfig.class.getSimpleName());
		ExampleConfig ev = new ExampleConfig();
		try {
			ev.demonstrate();
		} catch (Exception e) {
			log.error("Config example failed to run.", e);
		}
		log.info("Finished " + ExampleConfig.class.getSimpleName());
	}
	
	public static final String FACT_PREFIX = "my.app.db.factory.";
	public static final String POOL_PREFIX = "my.app.db.pool.";
	public static final String POOL_NAME = "MyAppDb";
	
	public void demonstrate() throws Exception {
		
		// Set some factory properties for environment ".test" and ".prod"
		Properties porg = new Properties();
		porg.put(FACT_PREFIX + "jdbcUrl.test", "jdbc:mysql://localhost:3306/mydb-test");
		porg.put(FACT_PREFIX + "jdbcUrl.prod", "jdbc:mysql://localhost:3306/mydb-prod");
		porg.put(FACT_PREFIX + "poolName", POOL_NAME);
		porg.put(FACT_PREFIX + "connection.user", "mydb-user");
		porg.put(FACT_PREFIX + "connection.password", "mydb-password");
		porg.put(FACT_PREFIX + "connection.password.prod", "mydb-password-prod");

		// set some pool properties.
		porg.put(POOL_PREFIX + "poolName", POOL_NAME);
		porg.put(POOL_PREFIX + "minSize.test", "2");
		porg.put(POOL_PREFIX + "minSize.prod", "4");
		porg.put(POOL_PREFIX + "maxSize.test", "10");
		porg.put(POOL_PREFIX + "maxSize.prod", "20");
		porg.put(POOL_PREFIX + "pruneIntervalS", "3");
		porg.put(POOL_PREFIX + "maxIdleTimeS", "180");
		
		MySqlPool pool = new MySqlPool();
		MySqlFactory fact = pool.getFactory();
		
		// Get the properties for the factory in the ".test" environment.
		// Properties for the ".prod" environment must be filtered out,
		// else they will become part of the connection-properties for the database connection. 
		Properties pfact = BeanConfig.configure(null, porg, FACT_PREFIX, ".test", ".prod");
		
		// Note that password values are replaced with "secret" in the returned string.
		log.info(BeanConfig.configure(fact, pfact));
		// The real password is set in the factory.
		log.info("Actual password: " + fact.getConnectionProps().get("password"));
		
		// Set additional connection properties.
		MySqlFactory.applyStableConnectionProps(fact.getConnectionProps());
		
		// Extract all properties from the factory.
		Properties pall = new Properties();
		BeanConfig.extract(fact, pall);
		// Note that password values are again replaced with "secret".
		log.info("All properties from factory:" + prettyPrint(pall));
		
		// Get the properties for the pool in the ".test" environment.
		// There is not need to filter out properties for the ".prod" environment
		// since a pool has no bean-method (set/get) that uses properties. 
		Properties ppool = BeanConfig.configure(null, porg, POOL_PREFIX, ".test");
		
		// Note that Seconds are automatically converted to Milliseconds for pruneInterval and maxIdleTime.
		log.info(BeanConfig.configure(pool, ppool));
		pall.clear();
		BeanConfig.extract(pool, pall);
		log.info("All properties from pool:" + prettyPrint(pall));
	}
	
	public static String prettyPrint(Properties p) {
		
		ArrayList<String> keys = new ArrayList<String>();
		for (String k : p.stringPropertyNames()) {
			keys.add(k.toString());
		}
		Collections.sort(keys);
		StringBuilder sb = new StringBuilder();
		for (String k : keys) {
			sb.append('\n').append(k).append('=').append(p.getProperty(k));
		}
		return sb.toString();
	}
}
