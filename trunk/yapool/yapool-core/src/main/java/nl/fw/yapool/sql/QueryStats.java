package nl.fw.yapool.sql;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class for keeping track of hits/misses by query-ID.
 */
public class QueryStats {

	public String sqlId;
	public AtomicLong hitCount = new AtomicLong();
	public AtomicLong missCount = new AtomicLong();
	
	public QueryStats(String sqlId) {
		this.sqlId = sqlId;
	}

}
