package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SimpleQueryCache} that also limits the amount of cached queries (see {@link #setMaxOpen(int)}).
 * To keep relevant queries in cache, the {@link CachedStatement#getWeight()} is used.
 * In general, this cache only works properly when:
 * <br> - a LIFO connection queue is used (like {@link SqlPool} does).
 * <br> - idle connections are closed (default 60 seconds in {@link SqlPool}).
 * If this is not done, the query cache could end up containing only stale statements. 
 * <br> - statements used in a loop are not closed but re-used (e.g. when inserting data in a loop).
 * If this is not done, one statement can push other statements out of the cache.
 * <p>
 * To fine-tune cache-flushing, use {@link #setMinWeightFactor(int)}.
 * @author Fred
 *
 */
public class BoundQueryCache extends SimpleQueryCache {
	
	/** By default, allow a maximum of 200 open queries. */
	public static final int DEFAULT_MAX_OPEN = 200;
	
	/**
	 * When to remove an under-used cached query.
	 * A lower factor removes queries faster, a higher factor removes queries slower.
	 * Default 5 (which removes queries that are missed 5 times more often then the other queries in the connection's cache). 
	 */
	public static final int DEFAULT_MIN_WEIGHT_FACTOR = 5;

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	protected final AtomicInteger openQueries = new AtomicInteger();
	private int minWeightFactor;
	private int maxOpen;

	public BoundQueryCache() {
		this(DEFAULT_MAX_OPEN);
	}
	
	public BoundQueryCache(int maxOpen) {
		super();
		setMaxOpen(maxOpen);
	}

	/**
	 * Removes CachedStatement from reference cache and decreases the open queries counter.
	 */
	@Override
	protected void closedQuery(CachedStatement cs) {
		
		super.closedQuery(cs);
		openQueries.decrementAndGet();
	}
	
	/**
	 * Adds CachedStatement to reference cache and increases the open queries counter.
	 */
	@Override
	protected void createdQuery(CachedStatement cs) {
		
		super.createdQuery(cs);
		openQueries.incrementAndGet();
	}

	@Override
	protected CachedStatement getQuery(Connection c, String queryName, boolean named) throws SQLException {

		CachedStatement cs = super.getQuery(c, queryName, named);
		Map<String, CachedStatement> cc = getConnectionCache(c);
		addHit(cc, cs);
		if (openQueries.get() > maxOpen) {
			if (!closeOneUnused(cc, cs)) {
				closedQuery(cs); // Just to update counter and remove references.
				cc.remove(queryName);
				if (log.isDebugEnabled()) {
					log.debug("Cache full, cannot add query [" + queryName + "]");
				}
			}
		}
		return cs;
	}
	
	protected void addHit(Map<String, CachedStatement> cc, CachedStatement csHit) {
		
		final int csize = cc.size() + 1;
		csHit.setWeight(Math.min(csize * csize, csHit.getWeight() + csize));
		// Decrease weight of other queries, remove if below minWeight.
		final int minWeight = Math.min(-csize * minWeightFactor,  -5);
		// Use iterator so that unused queries can be removed on the fly.
		final Iterator<Entry<String, CachedStatement>> ccEntries =  cc.entrySet().iterator();
		while (ccEntries.hasNext()) {
			final CachedStatement csMiss = ccEntries.next().getValue();
			if (csMiss == csHit) {
				continue;
			}
			csMiss.setWeight(csMiss.getWeight() - 1);
			if (csMiss.getWeight() < minWeight) {
				if (log.isDebugEnabled()) {
					log.debug("Removing query [" + csMiss.getQueryName() + "] from cache (weight below " + minWeight + ")");
				}
				csMiss.close();
				closedQuery(csMiss);
				ccEntries.remove();
			}
		}
	}

	/**
	 * Removes one query from cache or all queries with weight less than 1. 
	 * @param cc The query cache for the connection.
	 * @param excludeCs The query that must be untouched.
	 * @return true if one or more queries were removed from cache.
	 */
	protected boolean closeOneUnused(Map<String, CachedStatement> cc, CachedStatement excludeCs) {
		
		boolean cleanedOne = false;
		int weight = 0;
		while (weight < 1) {
			CachedStatement cs = getLeastRelevant(cc, excludeCs);
			if (cs == null) {
				break;
			}
			cleanedOne = true;
			weight = cs.getWeight();
			cs.close();
			closedQuery(cs);
			cc.remove(cs.getQueryName());
			if (log.isDebugEnabled()) {
				log.debug("Removed query [" + cs.getQueryName() + "] with weight " + cs.getWeight() + " from cache, open queries in connection cache: " + cc.size());
			}
		}
		return cleanedOne;
	}
	
	/**
	 * Called when cache is full but a new query needs to be added to the cache.
	 * This function can be called in a loop when there are many cached queries with a weight of zero or less.
	 */
	protected CachedStatement getLeastRelevant(Map<String, CachedStatement> cc, CachedStatement excludeCs) {
		
		if (cc.size() < 2) {
			return null;
		}
		CachedStatement lowest = null;
		int lowestWeight = Integer.MAX_VALUE;
		for (CachedStatement cs : cc.values()) {
			if (cs == excludeCs) {
				continue;
			}
			if (cs.getWeight() < lowestWeight) {
				lowestWeight = cs.getWeight();
				lowest = cs;
			}
		}
		return lowest;
	}

	/* *** BEAN METHODS *** */
	
	public int getMaxOpen() {
		return maxOpen;
	}
	
	/**
	 * The maximum amount of (open) cached queries.
	 * Default set to 200, see {@link #DEFAULT_MAX_OPEN}.
	 */
	public void setMaxOpen(int maxOpen) {
		if (maxOpen > 0) {
			this.maxOpen = maxOpen;
		}
	}

	public int getMinWeightFactor() {
		return minWeightFactor;
	}
	

	/**
	 * Sets the minimum weight factor.
	 * See also {@link #DEFAULT_MIN_WEIGHT_FACTOR}.
	 */
	public void setMinWeightFactor(int minWeightFactor) {
		if (minWeightFactor > 0) {
			this.minWeightFactor = minWeightFactor;
		}
	}
	
}
