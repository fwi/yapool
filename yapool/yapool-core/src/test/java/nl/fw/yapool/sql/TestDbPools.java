package nl.fw.yapool.sql;

import static nl.fw.yapool.sql.SqlUtil.*;
import static org.junit.Assert.*;

import java.sql.Statement;

import nl.fw.yapool.PoolPruner;
import nl.fw.yapool.listener.PoolEventLogger;
import nl.fw.yapool.listener.PoolPerformance;
import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.QueryCacheStats;
import nl.fw.yapool.sql.SimpleQueryCache;
import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestDbPools {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	/** Use one connection to create a table and insert a record. */
	@Test
	public void testDbInMem() {
		
		SqlPool pool = new SqlPool();
		DbConn db = new DbConn(pool);
		try {
			pool.setFactory(new SqlFactory());
			pool.setMaxLeaseTimeMs(1000L);
			pool.setMaxIdleTimeMs(100L);
			pool.setPruneIntervalMs(10L);
			PoolEventLogger eventLogger;
			pool.getEvents().addPoolListener(eventLogger = new PoolEventLogger());
			eventLogger.silent = true;
			pool.open();
			clearDbInMem(pool);
			db.setQuery(createTable);
			assertFalse("Table creation.", db.ps.execute());
			db.ps.close();
			db.setNQuery(insertRecord, Statement.RETURN_GENERATED_KEYS);
			db.nps.setString("name", "Frederik");
			assertEquals("Insert 1 record.", 1, db.nps.executeUpdate());
			db.rs = db.nps.getStatement().getGeneratedKeys();
			assertTrue("Have a result.", db.rs.next());
			assertEquals("Generated id value.", 100, db.rs.getInt("id"));
			db.conn.commit();
		} catch (Exception se) {
			se.printStackTrace();
			throw new AssertionError("DbInMem test failed with: " + se);
		} finally {
			db.close();
			pool.close();
		}
	}

	/** Runs tasks that perform database actions.
	 * There are more tasks then database connections so tasks must wait for 
	 * a database connection to become available.
	 * At the end of the test time-statistics are shown for each task. These numbers
	 * should, on average, be about the same.
	 */
	@Test
	public void testDbTasks() {
		
		int taskCount = 4;
		long sleepTime = 500;
		
		//SqlPoolPruner.getInstance().setPruneInterval(10L);
		SqlPool pool = new SqlPool();
		pool.setLogLeaseExpiredTrace(true);
		pool.setMaxSize(3);
		pool.setMaxAcquireTimeMs(50L);
		pool.setMaxIdleTimeMs(150L);
		pool.setMaxLeaseTimeMs(150L);
		pool.setPruneIntervalMs(10L);
		
		//DbTask.maxSleep = 100L;
		DbTask.maxSleep = 10L;
		DbTask.idleSleep = 20L;
		DbTask.numberOfInserts = 3;
		DbTask.numberOfSearches = 3;
		DbTask.querySearchKeySize = 3;
		//DbConn db = null;
		DbTask[] tasks = new DbTask[taskCount];
		SimpleQueryCache qcache = new SimpleQueryCache();
		qcache.setStats(new QueryCacheStats());
		qcache.setQueryBuilder(new TestQueryBuilder());
		PoolPerformance pstats = null;
		try {
			pool.setFactory(new SqlFactory());
			pool.open();
			initDbInMem(pool); // messes with performance statistics
			pool.getEvents().addPoolListener(pstats = new PoolPerformance(pool));
			pool.getEvents().addPoolListener(qcache);
			for (int i = 0; i < taskCount; i++) {
				tasks[i] = new DbTask(pool, qcache);
				SqlUtil.execute(tasks[i], true);
			}
			Thread.sleep(sleepTime);
			//pool.connFactory.close(pool.connections.keySet().iterator().next());
			//pool.flush();
			//Thread.sleep(1000);
			//System.out.println(pool.getStatusInfo());
			for (int i = 0; i < taskCount; i++) tasks[i].stop();
			boolean tasksRunning = true;
			long waitStart = System.currentTimeMillis(); 
			while (tasksRunning) {
				Thread.sleep(50L);
				long now = System.currentTimeMillis();
				tasksRunning = false;
				for (int i = 0; i < taskCount; i++) {
					if (tasks[i].isRunning()) {
						tasksRunning = true;
						if (now - waitStart > 1000L) {
							tasks[i].interrupt();
							log.warn("Task " + i + " had to be interrupted.");
						} else {
							break;
						}
					}
				}
			}
		} catch (Exception se) {
			se.printStackTrace();
			throw new AssertionError("DbTask test failed with " + se);
		} finally {
			//if (db != null) db.close();
			pool.close();
			if (pool.getFactory() != null) {
				//assertEquals(pool.getFactory().getCreated(), pool.getFactory().getClosed());
				//assertEquals(dbConf.getMaxSize(), pool.getFactory().getCreated());
			}
			if (pstats != null) {
				log.debug(pstats.toString());
			}
			if (qcache != null) {
				log.debug(qcache.getStats().toString());
			}
			try { Thread.sleep(50L); } catch (Exception ignored) {}
			assertFalse(PoolPruner.getInstance().isRunning());
		}
	}

}
