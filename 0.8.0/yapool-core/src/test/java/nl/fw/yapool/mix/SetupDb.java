package nl.fw.yapool.mix;

import java.util.concurrent.CountDownLatch;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;

public class SetupDb extends DbTask {

	private CountDownLatch doneLock;
	
	public SetupDb(SqlPool db) {
		super(db);
		doneLock = new CountDownLatch(1);
	}
	
	@Override
	public void doQuery(DbConn q) throws Exception {
		
		try {
			try { 
				q.setQuery(deleteTable); 
				q.ps.execute();
				q.conn.commit();
			} catch (Exception e) {
				log.debug("Delete table: " + e);
			}
			q.setQuery(createTable); 
			q.ps.execute();
			q.conn.commit();
		} finally {
			doneLock.countDown();
		}
	}
	
	public void await() {
		try { doneLock.await(); } catch (Exception ignored) {}
	}
}
