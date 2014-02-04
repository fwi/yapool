package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import nl.fw.yapool.IPool;
import nl.fw.yapool.PoolEvent;
import nl.fw.yapool.listener.PoolListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SimpleQueryCache} that also limits the amount of cached queries (see {@link #setMaxOpen(int)}).
 * To keep relevant queries in cache, a {@link BoundCacheWeight} instance is used.
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
public class BoundQueryCache extends PoolListener implements IQueryCache {
	
	/** By default, allow a maximum of 200 open queries. */
	public static final int DEFAULT_MAX_OPEN = 200;
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	protected Map<Connection, Map<String, Object>> qcache = new ConcurrentHashMap<Connection, Map<String, Object>>();
	protected QueryCacheStats qcacheStats;
	protected IQueryBuilder qb;
	protected String poolName = "SqlPool";
	private int maxOpen;
	protected final AtomicInteger openQueries = new AtomicInteger();
	protected BoundCacheWeight weightStats;

	public BoundQueryCache() {
		this(DEFAULT_MAX_OPEN);
	}
	
	public BoundQueryCache(int maxOpen) {
		super();
		addWantEvent(PoolEvent.DESTROYING);
		setQueryBuilder(new SimpleQueryBuilder());
		weightStats = new BoundCacheWeight(this);
		setMaxOpen(maxOpen);
	}

	/**
	 * Set to {@link SimpleQueryBuilder} by default constructor.
	 */
	@Override
	public void setQueryBuilder(IQueryBuilder qb) {
		this.qb = qb;
	}

	public IQueryBuilder getQueryBuilder() {
		return qb;
	}
	
	@Override
	public void listen(IPool<?> pool) {
		
		pool.getEvents().addPoolListener(this);
		poolName = pool.toString();
	}

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

	/**
	 * Closes (named) prepared statements related to a connection that gets event {@link PoolEvent#DESTROYING}
	 */
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		if (poolEvent.getAction() == PoolEvent.DESTROYING) {
			Map<String, Object> cc = qcache.get(poolEvent.getResource());
			if (cc != null) {
				if (log.isDebugEnabled()) {
					log.debug("Closing " + cc.size() + " cached queries for a database connection that is about to be destroyed.");
				}
				for (String sqlId : cc.keySet()) {
					closeQuery(sqlId, cc.get(sqlId));
				}
				cc.clear();
				qcache.remove(poolEvent.getResource());
			}
		}
	}
	
	/**
	 * Closes the PreparedStatement / NamedParameterStatement and catches any errors.
	 * <br>Decreases the {@link #openQueries} count and removes the query from {@link #weightStats}.
	 * @param queryName the name/ID associated with the statement.
	 * @param o the statement
	 */
	protected void closeQuery(String queryName, Object o) {
		
		if (o instanceof PreparedStatement) {
			DbConn.close(((PreparedStatement)o));
			openQueries.decrementAndGet();
		} else if (o instanceof NamedParameterStatement) {
			DbConn.close(((NamedParameterStatement)o));
			openQueries.decrementAndGet();
		} else {
			DbConn.closeLogger.warn("Cannot close unknown type of statement named " + queryName + ", statement: " + o);
		}
		weightStats.remove(o);
	}
	
	/**
	 * Returns the map containing the cached statements for the given connection.
	 * Creates a map if needed.
	 */
	protected Map<String, Object> getConnectionCache(Connection c) {
		
		Map<String, Object> cc = qcache.get(c);
		if (cc == null) {
			/*
			 * No need to use a concurrent hash-map for "cc":
			 * a connection can only be used by 1 thread at a given time.
			 */
			qcache.put(c, cc = new HashMap<String, Object>());
		}
		return cc;
	}

	@Override
	public PreparedStatement getQuery(Connection c, String queryName) throws SQLException {
		
		return (PreparedStatement) getQuery(c, queryName, false);
	}

	@Override
	public NamedParameterStatement getNamedQuery(Connection c, String queryName) throws SQLException {

		return (NamedParameterStatement) getQuery(c, queryName, true);
	}

	public Object getQuery(Connection c, String queryName, boolean named) throws SQLException {
		
		Map<String, Object> cc = getConnectionCache(c);
		Object o = cc.get(queryName);
		if (o != null) {
			boolean closed = false;
			if (named) {
				closed = ((NamedParameterStatement)o).getStatement().isClosed();
			} else {
				closed = ((PreparedStatement)o).isClosed();
			}
			if (closed) {
				cc.remove(o);
				openQueries.decrementAndGet();
				weightStats.remove(o);
				o = null;
				DbConn.closeLogger.warn("Cached " + (named ? "named" : "") + " prepared statement [" + queryName 
						+ "] removed from query cache because it was closed.");
			}
		}
		if (o == null) {
			if (qcacheStats != null) {
				qcacheStats.addMiss(queryName);
			}
			if (named) {
				o = qb.createNamedQuery(c, queryName);
			} else {
				o = qb.createQuery(c, queryName);
			}
			if (o == null) {
				throw new RuntimeException("Could not create a prepared statement for query name " + queryName);
			}
			openQueries.incrementAndGet();
			cc.put(queryName, o);
		} else {
			if (qcacheStats != null) {
				qcacheStats.addHit(queryName);
			}
		}
		weightStats.addHit(o, cc);
		if (openQueries.get() > maxOpen) {
			if (!cleanOpen(cc, queryName)) {
				cc.remove(queryName);
				weightStats.remove(o);
				openQueries.decrementAndGet();
				if (log.isDebugEnabled()) {
					log.debug("Cache full, cannot add query [" + queryName + "]");
				}
			}
		}
		return o;
	}
	
	/**
	 * Removes one query from cache or all queries with weight 0. 
	 * @param cc The query cache for the connection.
	 * @param excludeQueryName The query name that must be untouched.
	 * @return true if one or more queries were removed from cache.
	 */
	protected boolean cleanOpen(Map<String, Object> cc, String excludeQueryName) {
		
		boolean cleanedOne = false;
		int weight = 0;
		while (weight == 0) {
			String qname = weightStats.getLeastRelevant(cc, excludeQueryName);
			if (qname == null) {
				break;
			}
			cleanedOne = true;
			weight = weightStats.getWeight(cc.get(qname));
			closeQuery(qname, cc.remove(qname));
			if (log.isDebugEnabled()) {
				log.debug("Removed query [" + qname + "] with weight " + weight + " from cache, open queries in connection cache: " + cc.size());
			}
		}
		return cleanedOne;
	}
	
	@Override
	public boolean isCached(Connection c, Object statement) {
		Map<String, Object> cc = qcache.get(c);
		return (cc == null ? false : cc.containsValue(statement));
	}

	/**
	 * If set to non-null, query statistics are maintained.
	 */
	public void setStats(QueryCacheStats qcacheStats) {
		this.qcacheStats = qcacheStats;
	}
	
	public QueryCacheStats getStats() {
		return qcacheStats;
	}
	
	/**
	 * Sets the minimum weight factor used by this cache's {@link BoundCacheWeight}.
	 * See also {@link BoundCacheWeight#DEFAULT_MIN_WEIGHT_FACTOR}.
	 * 
	 * @return
	 */
	public void setMinWeightFactor(int minWeightFactor) {
		weightStats.setMinWeightFactor(minWeightFactor);
	}
	
	public int getMinWeightFactor() {
		return weightStats.getMinWeightFactor();
	}
	
	@Override
	public String toString() {
		
		int qcacheSize = 0;
		int cachedQueries = 0;
		for (Connection c : qcache.keySet()) {
			Map<String, Object> cc = qcache.get(c);
			if (cc != null) {
				qcacheSize++;
				cachedQueries += cc.size();
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" Query cache for pool ").append(poolName)
		.append(" caching queries for ").append(qcacheSize).append(" connections containing ")
		.append(cachedQueries).append(" cached queries.");
		sb.append(" Weight calculated for ").append(weightStats.size()).append(" cached queries.");
		return sb.toString();
	}

}
