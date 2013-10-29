package nl.fw.yapool.mix;

import static nl.fw.yapool.sql.SqlUtil.str;

import java.util.concurrent.atomic.AtomicLong;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;

public class Insert extends DbTask {
	
	public static AtomicLong recordsInserted = new AtomicLong();
	
	public static int insertAtOnce = 4;

	public Insert(SqlPool db) {
		super(db);
	}

	@Override
	public void doQuery(DbConn q) throws Exception {
		
		q.setNQuery(insertRecord);
		for (int i = 0; i < insertAtOnce; i++) {
			q.nps.setString("name", str(255));
			q.nps.executeUpdate();
		}
		q.conn.commit();
		recordsInserted.addAndGet(insertAtOnce);
	}

}
