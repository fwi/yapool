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
 * To keep relevant queries in cache, the {@link CachedStatement#getWeight()} is used together with {@link CachedStatement#isInUse()}.
 * In general, this cache only works properly when:
 * <br> - a LIFO connection queue is used (like {@link SqlPool} does).
 * <br> - idle connections are closed (default 60 seconds in {@link SqlPool}).
 * If this is not done, the query cache could end up containing only stale statements. 
 * <br> - statements used in a loop are re-used without closing (e.g. when inserting data in a loop).
 * <p>
 * To fine-tune cache-flushing, use {@link #setMinWeightFactor(int)}.
 * <br><b>Implementation note</b>: after fetching a query from the cache, the statement must be
 * closed using the {@link #close(java.sql.Statement)} function to mark a statement as 
 * "no longer in use" (or if it is not cached, really close it). 
 * If the close function is not called, statements are never flushed from cache,
 * rendering the cache useless.
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
		this(DEFAULT_MAX_OPEN, DEFAULT_MIN_WEIGHT_FACTOR);
	}

	public BoundQueryCache(int maxOpen) {
		this(maxOpen, DEFAULT_MIN_WEIGHT_FACTOR);
	}

	public BoundQueryCache(int maxOpen, int minWeightFactor) {
		super();
		setMaxOpen(maxOpen);
		setMinWeightFactor(minWeightFactor);
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
		if (openQueries.get() > maxOpen	&& !closeOneAndUnderUsed(cc)) {
			closedQuery(cs); // Just to update counter and remove references.
			cc.remove(queryName);
			if (log.isDebugEnabled()) {
				log.debug("Cache full, cannot add query [" + queryName + "] to cache [" + getConnectionHash(cs) + "]");
			}
		}
		return cs;
	}
	
	protected void addHit(Map<String, CachedStatement> cc, CachedStatement csHit) {
		
		final int csize = cc.size() + 1;
		csHit.setWeight(Math.min(csize * csize, csHit.getWeight() + csize));
		// Decrease weight of other queries, remove if below minWeight.
		final int minWeight = Math.min(-csize * minWeightFactor, -minWeightFactor);
		// Use iterator so that unused queries can be removed on the fly.
		final Iterator<Entry<String, CachedStatement>> ccEntries =  cc.entrySet().iterator();
		while (ccEntries.hasNext()) {
			final CachedStatement csMiss = ccEntries.next().getValue();
			if (csMiss == csHit) {
				continue;
			}
			csMiss.setWeight(csMiss.getWeight() - 1);
			if (!csMiss.isInUse() && csMiss.getWeight() < minWeight) {
				if (log.isDebugEnabled()) {
					log.debug("Removing query [" + csMiss.getQueryName() + "] from cache [" + getConnectionHash(csMiss) + "] (weight below " + minWeight + ")");
				}
				csMiss.close();
				closedQuery(csMiss);
				ccEntries.remove();
			}
		}
	}

	/**
	 * Removes one un-used query from cache or all queries with weight less than 1. 
	 * @param cc The query cache for the connection.
	 * @return true if one or more queries were removed from cache.
	 */
	protected boolean closeOneAndUnderUsed(Map<String, CachedStatement> cc) {
		
		boolean cleanedOne = false;
		while (true) {
			CachedStatement cs = getLeastRelevant(cc);
			if (cs == null || (cleanedOne && cs.getWeight() > 0)) {
				break;
			}
			cleanedOne = true;
			cs.close();
			closedQuery(cs);
			cc.remove(cs.getQueryName());
			if (log.isDebugEnabled()) {
				log.debug("Removed query [" + cs.getQueryName() + "] with weight " + cs.getWeight() + " from cache [" + getConnectionHash(cs) + "], open queries in connection cache: " + cc.size());
			}
		}
		return cleanedOne;
	}
	
	/**
	 * Called when cache is full but a new query needs to be added to the cache.
	 * This function can be called in a loop when there are many cached queries with a weight of zero or less.
	 */
	protected CachedStatement getLeastRelevant(Map<String, CachedStatement> cc) {
		
		CachedStatement lowest = null;
		int lowestWeight = Integer.MAX_VALUE;
		for (CachedStatement cs : cc.values()) {
			if (cs.isInUse()) {
				continue;
			}
			if (cs.getWeight() < lowestWeight) {
				lowestWeight = cs.getWeight();
				lowest = cs;
			}
		}
		return lowest;
	}
	
	/**
	 * 
	 * @return
	 */
	protected String getConnectionHash(CachedStatement cs) {
		return Integer.toString(cs.getConnection().hashCode());
		
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
