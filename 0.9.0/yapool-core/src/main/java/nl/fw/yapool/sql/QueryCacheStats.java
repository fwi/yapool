package nl.fw.yapool.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks hits/misses by query ID (sqlID).
 * {@link #toString()} shows a report.
 *
 */
public class QueryCacheStats {
	
	protected Map<String, QueryStats> qstats = new ConcurrentHashMap<String, QueryStats>();
	
	public QueryStats getStats(String sqlId) {
		
		QueryStats qcs = qstats.get(sqlId);
		if (qcs == null) {
			qstats.put(sqlId, qcs = new QueryStats(sqlId));
		}
		return qcs;
	}
	
	public void addMiss(String sqlId) {
		getStats(sqlId).missCount.incrementAndGet();
	}

	public void addHit(String sqlId) {
		getStats(sqlId).hitCount.incrementAndGet();
	}
	
	/**
	 * Returns a report of hits/misses per query.
	 */
	public String toString() {
		
		StringBuilder sb = new StringBuilder("Query cache statistics:");
		sb.append('\n').append("Queries in cache: ").append(qstats.size());
		if (qstats.size() == 0) return sb.toString();
		long totalHits = 0;
		long totalMisses = 0;
		for (QueryStats qs : qstats.values()) {
			totalHits += qs.hitCount.get();
			totalMisses += qs.missCount.get();
		}
		long total = totalHits + totalMisses;
		sb.append('\n').append("Percentage hits: ");
		if (totalHits == 0L) {
			sb.append("0%");
		} else if (totalMisses == 0L) {
			sb.append("100%");
		} else {
			sb.append(((total-totalMisses)*100) / total)
			.append("% (").append(totalHits).append(" hits out of ").append(total).append(")");
		}
		ArrayList<String> queries = new ArrayList<String>(qstats.keySet());
		Collections.sort(queries);
		for (String q : queries) {
			sb.append('\n').append(q).append('\n').append("Misses: ").append(getStats(q).missCount.get())
			.append(", hits: ").append(getStats(q).hitCount.get());
		}
		return sb.toString();
	}
}
