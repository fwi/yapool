package nl.fw.yapool.sql;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BoundCacheWeight {
	
	private Map<Object, Integer> weightByQuery = new ConcurrentHashMap<Object, Integer>();
	
	public Integer remove(Object query) {
		return weightByQuery.remove(query);
	}
	
	public int getWeight(Object query) {
		
		Integer w = weightByQuery.get(query);
		return (w == null ? 0 : w);
	}
	
	public void addHit(Object query, Map<String, Object> cc) {
		
		final int csize = cc.size() + 1;
		weightByQuery.put(query, Math.min(csize * csize,  getWeight(query) + csize));
		for (Object o : cc.values()) {
			if (o != query) {
				addMiss(o);
			}
		}
	}
	
	public void addMiss(Object query) {
		
		weightByQuery.put(query, Math.max(0,  getWeight(query) - 1));
	}
	
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

}
