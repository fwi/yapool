package nl.fw.yapool.mix;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;

public class DbTask implements Runnable {

	public static String createTable = "create table t (id integer generated always as identity(start with 100) primary key, name varchar(256))";
	public static String deleteTable = "drop table t";
	public static String insertRecord = "insert into t (name) values (@name)";
	public static String selectRecord = "select id from t where name like @name";

	public static AtomicLong queryCount = new AtomicLong();
	public static AtomicLong failedQueryCount = new AtomicLong();
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	protected SqlPool db;
	
	public DbTask(SqlPool db) {
		this.db = db;
	}

	public void run() {

		DbConn q = new DbConn(db);
		try {
			doQuery(q);
			updateQueryCount();
		} catch (Exception e) {
			log.error("Query failed", e);
			updateFailedQueryCount();
		} finally {
			q.close();
		}
	}
	
	public void doQuery(DbConn q) throws Exception {
		
		log.debug("Empty task " + toString());
	}
	
	public void updateQueryCount() {
		queryCount.getAndIncrement();
	}
	
	public void updateFailedQueryCount() {
		failedQueryCount.getAndIncrement();
	}

}
