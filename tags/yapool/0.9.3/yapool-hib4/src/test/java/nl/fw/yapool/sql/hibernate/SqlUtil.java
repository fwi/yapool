package nl.fw.yapool.sql.hibernate;

import java.sql.Connection;
import java.sql.SQLException;


import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlUtil {

	protected static Logger log = LoggerFactory.getLogger(SqlUtil.class);

	public static String createTable = "create table t (id integer generated always as identity(start with 100) primary key, name varchar(256))";
	public static String deleteTable = "drop table t";
	public static String insertRecord = "insert into t (name) values (@name)";
	public static String selectRecord = "select id from t where name like @name";
	
	/** Deletes any created tables. */
	public static void clearDbInMem(SqlPool pool) {
		
		Connection c = null;
		try {
			clearDbInMem(c = pool.acquire());
		} catch (Exception e) {
			log.info("Could not clear db in mem, or db already clear: " + e);
		} finally {
			pool.release(c);
		}
	}
		
	/** Deletes any created tables. */
	public static void clearDbInMem(Connection c) {
		
		DbConn db = new DbConn(c);
		try {
			db.setQuery(deleteTable);
			db.ps.execute();
			db.conn.commit();
		} catch (Exception sqle) {
			log.info("Could not clear db in mem, or db already clear: " + sqle);
		} finally {
			db.closeQuery();
		}
	}

	/** Creates required tables. */
	public static void initDbInMem(SqlPool pool) throws SQLException {

		Connection c = null;
		try {
			initDbInMem(c = pool.acquire());
		} catch (SQLException sqle) {
			log.warn("Could not init db in mem: " + sqle);
		} finally {
			pool.release(c);
		}
	}
		
	/** Creates required tables. */
	public static void initDbInMem(Connection c) throws SQLException {
		
		long tstart = System.currentTimeMillis();
		clearDbInMem(c);
		DbConn db = new DbConn(c);
		try {
			db.setQuery(createTable);
			db.ps.execute();
			db.conn.commit();
		} finally {
			db.closeQuery();
		}
		log.debug("Clear time: " + (tstart - System.currentTimeMillis()));
	}
	
	/** Returns a random string with given length containing a-z characters. */
	public static String str(int length) {
		StringBuilder sb = new StringBuilder("");
		for (int i = 0; i < length; i++) {
			int c = ((int)'a') + (int)(Math.random()*26); 
			sb.append(((char)c));
		} 
		return sb.toString();
	}
	
	/** Returns a random number as String with the given length. */
	public static String num(int length) {
		if (length == 0) return "";
		StringBuilder sb = new StringBuilder("1");
		for (int i = 1; i < length; i++) {
			int c = ((int)'0') + (int)(Math.random()*10); 
			sb.append(((char)c));
		} 
		return sb.toString();
	}
	
	public static long number(int length) {
		return Long.valueOf(num(length));
	}
	
	public static void execute(final Runnable r, final boolean daemon) { 
		
		final Thread t = new Thread(r);
		t.setDaemon(daemon);
		t.start();
	}

}
