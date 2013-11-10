package nl.fw.yapool.examples;

import java.sql.Statement;

import nl.fw.yapool.PoolPruner;
import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates the use of a HSQL in-memory database with SqlPool.
 * This can be convenient with unit-testing. 
 * The example has the following output: <pre>{@literal
13:56:952 [main] INFO yapool.example.hsql - Starting ExampleHsql
2013-11-10 14:13:57.318 1 INSERT INTO SYSTEM_LOBS.BLOCKS VALUES(?,?,?) (0,2147483647,0)
2013-11-10 14:13:57.319 1 COMMIT 
13:57:324 [main] INFO yapool.example.hsql - Pruning: true
2013-11-10 14:13:57.325 2 create table t (id integer generated always as identity(start with 100) primary key, name varchar(256)) 
2013-11-10 14:13:57.325 2 COMMIT 
13:57:325 [main] INFO yapool.example.hsql - Create table: 0
2013-11-10 14:13:57.330 2 insert into t (name) values (?) ('Donald Duck')
2013-11-10 14:13:57.331 2 COMMIT 
13:57:334 [main] INFO yapool.example.hsql - Insert record: 1
13:57:334 [main] INFO yapool.example.hsql - Duck ID: 100
2013-11-10 14:13:57.337 2 select id from t where name like ? ('%ald%')
2013-11-10 14:13:57.337 2 COMMIT 
13:57:338 [main] INFO yapool.example.hsql - Select %ald% ID: 100
2013-11-10 14:13:57.339 2 ROLLBACK 
13:57:339 [main] INFO yapool.example.hsql - Pruning: false
13:57:340 [main] INFO yapool.example.hsql - Finished ExampleHsql
}</pre>

 * @author Fred
 *
 */
public class ExampleHsql {

	public static final String LOG_CATEGORY = "yapool.example.hsql";
	
	private static final Logger log = LoggerFactory.getLogger(LOG_CATEGORY);
	
	public static void main(String[] args) {

		log.info("Starting " + ExampleHsql.class.getSimpleName());
		ExampleHsql ev = new ExampleHsql();
		try {
			ev.demonstrate();
		} catch (Exception e) {
			log.error("Hsql example failed to run.", e);
		}
		log.info("Finished " + ExampleHsql.class.getSimpleName());
	}
	
	public static String createTable = "create table t (id integer generated always as identity(start with 100) primary key, name varchar(256))";
	public static String deleteTable = "drop table t";
	public static String insertRecord = "insert into t (name) values (@name)";
	public static String selectRecord = "select id from t where name like @name";

	public void demonstrate() throws Exception {
		
		SqlPool pool = new SqlPool();
		// Sqlfactory is default configured for an in-memory HSQL database.
		pool.setFactory(new SqlFactory());
		// Don't need transactions for this demonstration.
		// By default autocommit is off.
		pool.getFactory().setAutoCommit(true);
		// Let the HSQL driver show all queries
		pool.getFactory().getConnectionProps().put("hsqldb.sqllog", "3");
		// Close the database when last connection closes.
		pool.getFactory().getConnectionProps().put("shutdown", "true");
		// Prevent closing the last connection, this can shutdown/close the in memory HSQL database
		pool.setMinSize(1);
		pool.open();
		// For this demonstration, a pool is not required, a factory is enough. 
		// I.e. this demonstration can also run with: 
		// DbConn c = new DbConn(new SqlFactory())
		
		// A SqlPool registers itself for pruning
		log.info("Pruning: " + PoolPruner.getInstance().isRunning());
		try {
			// Constructing a DbConn never throws an error.
			runQueries(new DbConn(pool));
		} finally {
			// Always assume that runtime-errors can occur
			// and use a try-finally block to close open resources.
			pool.close();
			// A SqlPool unregisters itself for pruning when closed
			log.info("Pruning: " + PoolPruner.getInstance().isRunning());
		}
	}
	
	public void runQueries(DbConn c) {
		
		try {
			log.info("Create table: " + c.execute(createTable)); 
			// With autocommit off, a commit call is required, i.e. 
			// c.conn.commit();

			c.setNQuery(insertRecord, Statement.RETURN_GENERATED_KEYS).setString("name", "Donald Duck");
			log.info("Insert record: " + c.executeUpdate()); 
			c.rs.next();
			long duckId = c.rs.getLong(1);
			log.info("Duck ID: " + duckId);

			c.setNQuery(selectRecord).setString("name", "%ald%");
			c.executeQuery().next();
			log.info("Select %ald% ID: " + c.rs.getLong(1));
		} catch (Exception e) {
			// In case of error, break normal programm flow and throw a runtime-error.
			// But always have a finally block in place that closes open resources.
			c.rollbackAndClose(e);
		} finally {
			// Always release connection back to the pool.
			c.close();
		}
	}

}
