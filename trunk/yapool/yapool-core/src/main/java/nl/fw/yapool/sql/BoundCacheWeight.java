package nl.fw.yapool.sql;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for the {@link BoundQueryCache}.
 * Keeps track of the weight-values per connection cache.
 * @author fred
 *
 */
public class BoundCacheWeight {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * When to remove an under-used cached query.
	 * A lower factor removes queries faster, a higher factor removes queries slower.
	 * Default 5 (which removes queries that are missed 5 times more often then the other queries in the connection's cache). 
	 */
	public static final int DEFAULT_MIN_WEIGHT_FACTOR = 5;
	
	private Map<Object, Integer> weightByQuery = new ConcurrentHashMap<Object, Integer>();
	private BoundQueryCache qc;
	private int minWeightFactor;
	
	public BoundCacheWeight(BoundQueryCache qc) {
		this.qc = qc;
		minWeightFactor = DEFAULT_MIN_WEIGHT_FACTOR;
	}
	
	public Integer remove(Object query) {
		return weightByQuery.remove(query);
	}
	
	/**
	 * If all queries are used equally, weight is zero.
	 * <br>If a query is popular, weight is up to (connection cache size * connection cache size).
	 * There is an upper limit to prevent a once popular query never leaving the cache.
	 * <br>If a query is under-used, weight drops below zero.
	 * If the weight drops below ({@link #getMinWeightFactor()} * connection cache size),
	 * tje query is removed from cache.
	 * @param query
	 * @return
	 */
	public int getWeight(Object query) {
		
		Integer w = weightByQuery.get(query);
		return (w == null ? 0 : w);
	}
	
	public void addHit(Object query, Map<String, Object> cc) {
		
		final int csize = cc.size() + 1;
		weightByQuery.put(query, Math.min(csize * csize,  getWeight(query) + csize));
		// Decrease weight of other queries, remove if below minWeight.
		final int minWeight = Math.min(-csize * minWeightFactor,  -5);
		final Iterator<Entry<String, Object>> ccEntries =  cc.entrySet().iterator();
		while (ccEntries.hasNext()) {
			Entry<String, Object> entry = ccEntries.next();
			if (entry.getValue() == query) {
				continue;
			}
			if (addMiss(entry.getValue()) < minWeight) {
				if (log.isDebugEnabled()) {
					log.debug("Removing query [" + entry.getKey() + "] from cache (weight below " + minWeight + ")");
				}
				qc.closeQuery(entry.getKey(), entry.getValue());
				ccEntries.remove();
			}
		}
	}
	
	private int addMiss(Object query) {
		
		int w = getWeight(query) - 1;
		weightByQuery.put(query, w);
		return w;
	}
	
	/**
	 * Called by {@link BoundQueryCache) when cache is full but a new query needs to be added to the cache.
	 * This function can be called in a loop when there are many cached queries with a weight of zero or less.
	 */
	public String getLeastRelevant(Map<String, Object> cc, String excludeQueryName) {
		
		if (cc.size() < 2) {
			return null;
		}
		String lowest = null;
		Integer lowestWeight = null;
		for (String s : cc.keySet()) {
			if (s.equals(excludeQueryName)) {
				continue;
			}
			Object q = cc.get(s);
			int weight = getWeight(q);
			if (lowestWeight == null || weight < lowestWeight) {
				lowestWeight = weight;
				lowest = s;
			}
		}
		return lowest;
	}
	
	public void setMinWeightFactor(int minWeightFactor) {
		if (minWeightFactor > 0) {
			this.minWeightFactor = minWeightFactor;
		}
	}
	
	public int getMinWeightFactor() {
		return minWeightFactor;
	}

	/**
	 * Expensive calculation! Do not use regularly.
	 * @return amount of queries in cache with a calculated weight.
	 */
	public int size() {
		return weightByQuery.size();
	}

}
