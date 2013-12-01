package nl.fw.yapool.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static nl.fw.yapool.sql.SqlUtil.*;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
//import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;



import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.IQueryCache;
import nl.fw.yapool.sql.SqlPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbTask implements Runnable {
	
	public static AtomicInteger taskCount = new AtomicInteger();
	
	public static long maxSleep;
	public static int numberOfInserts;
	public static int querySearchKeySize;
	public static int numberOfSearches;
	public static int queryTimeOutSeconds;
	
	Logger log = LoggerFactory.getLogger(getClass());
	DbConn db;
	IQueryCache qc;
	
	//DbConnTimed db;
	boolean stop;
	volatile Thread runningThread;
	int taskId;
	int txCount;

	public static long idleSleep;
	
	public DbTask(SqlPool pool, IQueryCache qc) {
		super();
		this.qc = qc;
		db = new DbConn(pool, qc);
		//db = new DbConnTimed(pool);
		taskId = taskCount.incrementAndGet();
	}
	
	/** Calls task() in a loop. */
	@Override
	public void run() {
		
		runningThread = Thread.currentThread();
		runningThread.setName(getClass().getSimpleName() + "[" + taskId + "]");
		try {
			while (!stop) {
				try { 
					task();
				} catch (SQLException se) {
					if (se.getCause() != null && InterruptedException.class.equals(se.getCause().getClass())) {
						log.info(taskId + " " + se.toString());
					} else {
						log.error(taskId + " unexpected error.", se);
					}
				} catch (Throwable t) {
					if (t.getClass().getName().startsWith("com.mysql")) {
						log.error(t.toString());
					} else {
						throw t;
					}
				} finally {
					db.close();
				}
			}
		} catch (Throwable t) {
			log.error(taskId + " unexpected error.", t);
		} finally {
			db.close();
			//log.warn(taskId + " number of tx: " + txCount + " db connection " + db.getStats());
			log.info(taskId + " number of tx: " + txCount);
		}
		runningThread = null;
	}
	
	/** Called by run-loop. Inserts and selects a number of records in one transaction. */
	public void task() throws SQLException {
		
		//db.setNQuery(insertRecord, Statement.RETURN_GENERATED_KEYS);
		// The TestQueryBuilder sets Statement.RETURN_GENERATED_KEYS for SqlUtil.insertRecord
		db.setNQuery(insertRecord);
		if (queryTimeOutSeconds > 0) db.nps.getStatement().setQueryTimeout(queryTimeOutSeconds);
		int i = 0;
		while (i < numberOfInserts) {
			db.nps.setString("name", str(255));
			assertEquals("Insert 1 record.", 1, db.nps.executeUpdate());
			db.rs = db.nps.getStatement().getGeneratedKeys();
			assertTrue("Have a generated value.", db.rs.next());
			final ResultSetMetaData md = db.rs.getMetaData();
			String genKeyName = md.getColumnName(1);
			if ("GENERATED_KEY".equals(genKeyName)) {
				if (log.isTraceEnabled()) log.trace(taskId + " inserted record " + db.rs.getLong("GENERATED_KEY"));
			} else {
				if (log.isTraceEnabled()) log.trace(taskId + " inserted record with " + genKeyName +": " + db.rs.getInt("id"));
			}
			i++;
		}
		if (numberOfInserts > 0 && log.isDebugEnabled()) log.debug(taskId + " inserted " + numberOfInserts + " records.");
		db.setNQuery(selectRecord);
		if (queryTimeOutSeconds > 0) db.nps.getStatement().setQueryTimeout(queryTimeOutSeconds);
		i = 0;
		int hits = 0;
		while (i < numberOfSearches) {
			String nameSearch = str(querySearchKeySize);
			db.nps.setString("name", "%"+nameSearch+"%");
			db.rs = db.nps.executeQuery();
			int rsSize = 0; 
			while (db.rs.next()) rsSize++;
			if (rsSize > 0 && log.isTraceEnabled()) log.trace(taskId + " got " + rsSize + " records for " + nameSearch);
			hits += rsSize;
			DbConnTimed.close(db.rs);
			i++;
		}
		if (numberOfSearches > 0 && log.isDebugEnabled()) log.debug(taskId + " perfomed " + numberOfSearches + " searches with " + hits + " hits.");
		if (maxSleep > 0L) {
			final long sleepTime = (long)(Math.random()*maxSleep); 
			try { 
				if (!stop) {
					if (log.isDebugEnabled()) log.debug(taskId + " sleeping for " + sleepTime + " ms. before commit");
					Thread.sleep(sleepTime + 10); 
				}
			} catch (InterruptedException ie) {
				log.warn(taskId + " sleep of " + sleepTime + " ms. interrupted: " + ie);
			}
		}
		db.conn.commit();
		db.close();
		log.debug(taskId + " inserted " + numberOfInserts + " records, found " + hits);
		txCount++;
		if (idleSleep > 0L) {
			final long sleepTime = (long)(Math.random()*idleSleep); 
			try { 
				if (!stop) {
					if (log.isDebugEnabled()) log.debug(taskId + " sleeping for " + sleepTime + " ms. in idle");
					Thread.sleep(sleepTime); 
				}
			} catch (InterruptedException ie) {
				log.warn(taskId + " sleep of " + sleepTime + " ms. interrupted: " + ie);
			}
		}
	}
	
	public boolean isRunning() { return runningThread != null; }
	
	public void stop() {
		stop = true;
	}
	
	public void interrupt() {
		
		stop();
		Thread t = runningThread;
		if (t != null) t.interrupt();
	}

}
