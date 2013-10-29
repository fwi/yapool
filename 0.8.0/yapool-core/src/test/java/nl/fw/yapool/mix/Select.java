package nl.fw.yapool.mix;

import static nl.fw.yapool.sql.SqlUtil.str;

import java.util.concurrent.atomic.AtomicLong;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;

public class Select extends DbTask {

	public static AtomicLong recordsSelected = new AtomicLong();
	
	public static int selectSize = 4;
	
	public Select(SqlPool db) {
		super(db);
	}

	@Override
	public void doQuery(DbConn q) throws Exception {
		
		q.setNQuery(selectRecord);
		String nameSearch = str(selectSize);
		q.nps.setString("name", "%"+nameSearch+"%");
		q.rs = q.nps.executeQuery();
		int rsSize = 0; 
		while (q.rs.next()) rsSize++;
		recordsSelected.addAndGet(rsSize);
	}

}
