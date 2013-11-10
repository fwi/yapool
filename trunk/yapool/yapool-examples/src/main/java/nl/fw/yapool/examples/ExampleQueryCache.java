package nl.fw.yapool.examples;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.QueryCacheStats;
import nl.fw.yapool.sql.SimpleQueryBuilder;
import nl.fw.yapool.sql.SimpleQueryCache;
import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An advanced example demonstrating the use of cached queries.
 * Cached queries can reduce database network traffic by a significant amount.
 * {@link DbConn} can recognize cached queries and does not close them (which is required
 * when cached queries are to be re-used).
 * <br>This example demonstrates loading statements from files (opened as resources from src/main/resources),
 * initializing a database, preparing and using a query cache 
 * and executing an on-the-fly created select-query with multiple 'in' values.
 * <br>The example has the following output: <pre>{@literal
38:50:073 [main] INFO yapool.example.qcache - Starting ExampleQueryCache
2013-11-10 16:38:50.443 1 INSERT INTO SYSTEM_LOBS.BLOCKS VALUES(?,?,?) (0,2147483647,0)
2013-11-10 16:38:50.444 1 COMMIT 
38:50:451 [main] INFO yapool.example.qcache - Executing statements loaded from example-qc-struct.sql
2013-11-10 16:38:50.455 2 create table t 
(id integer generated always as identity(start with 100) primary key, 
name varchar(256)) 
2013-11-10 16:38:50.455 2 COMMIT 
38:50:455 [main] INFO yapool.example.qcache - Executing statements loaded from example-qc-data.sql
2013-11-10 16:38:50.456 2 insert into t (name) values ('Donald Duck') 
2013-11-10 16:38:50.456 2 insert into t (name) values ('Mickey Mouse') 
2013-11-10 16:38:50.456 2 insert into t (name) values ('Marvin the Martian') 
2013-11-10 16:38:50.457 2 insert into t (name) values ('Woody Pride') 
2013-11-10 16:38:50.457 2 insert into t (name) values ('Buzz Lightyear') 
2013-11-10 16:38:50.457 2 insert into t (name) values ('Jessica Jane Pride') 
2013-11-10 16:38:50.457 2 COMMIT 
38:50:457 [main] INFO yapool.example.qcache - Loading queries from example-qc-queries.sql
2013-11-10 16:38:50.467 2 select id from t where name like ? ('%Pride')
38:50:471 [main] INFO yapool.example.qcache - Query cache statistics:
Queries in cache: 1
Percentage hits: 0%
SELECT_ID
Misses: 1, hits: 0
2013-11-10 16:38:50.472 2 select name from t where id = ? (103)
2013-11-10 16:38:50.472 2 select name from t where id = ? (105)
38:50:472 [main] INFO yapool.example.qcache - Query cache statistics:
Queries in cache: 2
Percentage hits: 33% (1 hits out of 3)
SELECT_ID
Misses: 1, hits: 0
SELECT_NAME
Misses: 1, hits: 1
2013-11-10 16:38:50.473 2 insert into t (name) values (?) ('Huey Duck')
2013-11-10 16:38:50.473 2 insert into t (name) values (?) ('Dewey Duck')
2013-11-10 16:38:50.473 2 insert into t (name) values (?) ('Louie Duck')
2013-11-10 16:38:50.473 2 COMMIT 
38:50:473 [main] INFO yapool.example.qcache - Nephew IDs: [106, 107, 108]
38:50:473 [main] INFO yapool.example.qcache - Query cache statistics:
Queries in cache: 3
Percentage hits: 50% (3 hits out of 6)
INSERT_NAME
Misses: 1, hits: 2
SELECT_ID
Misses: 1, hits: 0
SELECT_NAME
Misses: 1, hits: 1
38:50:473 [main] INFO yapool.example.qcache - SimpleQueryCache Query cache for pool QCExample caching queries for 1 connections containing 3 cached queries.
2013-11-10 16:38:50.481 2 select name from t where id in (?,?,?) (106,107,108)
38:50:481 [main] INFO yapool.example.qcache - Nephew names: [Huey Duck, Dewey Duck, Louie Duck]
2013-11-10 16:38:50.483 2 ROLLBACK 
2013-11-10 16:38:50.483 2 ROLLBACK 
38:50:484 [main] INFO yapool.example.qcache - Query cache size: SimpleQueryCache Query cache for pool QCExample caching queries for 0 connections containing 0 cached queries.
38:50:484 [main] INFO yapool.example.qcache - Finished ExampleQueryCache
}</pre>
 * 
 * @author Fred
 *
 */
public class ExampleQueryCache {
	
	public static final String LOG_CATEGORY = "yapool.example.qcache";
	
	private static final Logger log = LoggerFactory.getLogger(LOG_CATEGORY);
	
	public static void main(String[] args) {

		log.info("Starting " + ExampleQueryCache.class.getSimpleName());
		ExampleQueryCache ev = new ExampleQueryCache();
		try {
			ev.demonstrate();
		} catch (Exception e) {
			log.error("QueryCache example failed to run.", e);
		}
		log.info("Finished " + ExampleQueryCache.class.getSimpleName());
	}

	/** A prepared statement that auto-generates keys. */
	public static final String INSERT_NAME = "INSERT_NAME";
	/** A named parameter statement. */
	public static final String SELECT_ID = "SELECT_ID";
	/** A named parameter statement. */
	public static final String SELECT_NAME = "SELECT_NAME";
	public static final String[] DDUCK_NEPHEWS = {"Huey Duck", "Dewey Duck", "Louie Duck"};
	
	public void demonstrate() throws Exception {
		
		// See ExampleHsql for an explanation of an HSQL pool. 
		SqlPool pool = new SqlPool();
		pool.setPoolName("QCExample");
		pool.setFactory(new SqlFactory());
		pool.getFactory().getConnectionProps().put("hsqldb.sqllog", "3");
		pool.getFactory().getConnectionProps().put("shutdown", "true");
		pool.setMinSize(1);
		pool.open();
		
		// Prepare a query cache to be used with named queries.
		SimpleQueryCache qc = new SimpleQueryCache();
		
		// Important: register QueryCache with pool-listener.
		// This ensures that when a connection is closed, cached queries for the connection are cleaned up.
		qc.listen(pool);
		DbConn c = new DbConn(pool);
		try {
			// Create database structure and set inital data
			initDb(pool);
			// Keep track of hits and misses
			qc.setStats(new QueryCacheStats());

			// Register the queries with names that are cached
			SimpleQueryBuilder qb = (SimpleQueryBuilder) qc.getQueryBuilder();
			
			final String qcacheFile = "example-qc-queries.sql";
			log.info("Loading queries from " + qcacheFile);
			qb.getQueryMap().putAll(loadQueries(qcacheFile));
			
			// Query cache cannot determine if a query generates keys automatically.
			// This has to be registered "manually" and is one if the reasons to extend
			// the SimpleQueryBuilder class.
			qb.addGeneratesKeys(INSERT_NAME, false);
			
			// Let DbConn use the query cache
			c.qc = qc;
			
			// The query cache looks up the actual query for the given query name. 
			c.setNQuery(SELECT_ID).setString("name", "%Pride");
			c.executeQuery();
			List<Long> prideIDs = new ArrayList<Long>();
			while (c.rs.next()) {
				prideIDs.add(c.rs.getLong("id"));
			}
			// The SELECT_ID query is only used 1 time, which means it was not re-used.
			// This means the cache "hits" is 0%.
			log.info(qc.getStats().toString());
			
			List<String> prideNames = new ArrayList<String>();
			// Two names will be retrieved: Woody Pride and Jessica Jane Pride
			for (Long id: prideIDs) {
				c.setNQuery(SELECT_NAME).setLong("id", id);
				c.executeQuery().next();
				prideNames.add(c.rs.getString("name"));
			}
			// The SELECT_NAME query is used 2 times, which means it was re-used 1 time.
			log.info(qc.getStats().toString());
			
			List<Long> nephewIDs = new ArrayList<Long>();
			// Re-using an insert-statement can save a lot of database network traffic.
			for (String n: DDUCK_NEPHEWS) {
				// Query cache was already configured to return generated keys for INSERT_NAME.
				c.setQuery(INSERT_NAME).setString(1, n);
				c.executeUpdate();
				c.rs.next();
				nephewIDs.add(c.rs.getLong(1));
			}
			c.conn.commit();
			// It is always a good idea to release a connection to the pool if it is not used for a while
			c.close();

			log.info("Nephew IDs: " + nephewIDs.toString());
			log.info(qc.getStats().toString());
			log.info(qc.toString());
			
			// Custom queries not in cache/associated with a query name can still be executed.
			// Create a select statement to retrieve all nephew names for known nephew IDs.
			String selectNames = "select name from t where id in (%s)";
			selectNames = String.format(selectNames, DbConn.preparePlaceHolders(nephewIDs.size()));
			
			c.setQuery(selectNames); // DbConn fetches a connection from the pool automatically.
			// The query cache and query builder cannot find a query name for the value of "selectNames"
			// and decide to use the value of "selectNames" as the SQL-query. 
			
			DbConn.setValues(c.ps, nephewIDs);
			c.executeQuery();

			List<String> nephewNames = new ArrayList<String>();
			while (c.rs.next()) {
				nephewNames.add(c.rs.getString(1));
			}
			c.close();
			
			log.info("Nephew names: " + nephewNames.toString());
		} catch (Exception e) {
			c.rollbackAndClose(e);
		} finally {
			c.close();
			pool.close();
			// There should be nothing in cache when the pool is closed.
			log.info("Query cache size: " + qc.toString());
		}
	}
	
	public void initDb(SqlPool pool) throws Exception {
		
		final String structFile = "example-qc-struct.sql";
		log.info("Executing statements loaded from " + structFile);
		// Load statements that create the structure of the database
		Map<String, String> statements = loadQueries(structFile);
		executeInitStatements(pool, statements);

		final String dataFile = "example-qc-data.sql";
		log.info("Executing statements loaded from " + dataFile);
		// Load statements that fill the database with data
		statements = loadQueries(dataFile);
		executeInitStatements(pool, statements);
	}
	
	public Map<String, String> loadQueries(String resourceName) throws Exception {
		
		Map<String, String> m = null;
		InputStreamReader in = null;
		try {
			m = SimpleQueryBuilder.loadQueries(in = new InputStreamReader(
					Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)));
		} finally {
			if (in != null) {
				in.close();
			}
		}
		return m;
	}

	public void executeInitStatements(SqlPool pool, Map<String, String> statements) throws Exception {
		
		DbConn c = new DbConn(pool);
		try {
			for (String qname: statements.keySet()) {
				c.execute(statements.get(qname));
			}
			c.conn.commit();
		} catch (Exception e) {
			c.rollbackAndClose(e);
		} finally {
			c.close();
		}
	}
}
