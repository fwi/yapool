package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nl.fw.yapool.IPool;
import nl.fw.yapool.PoolEvent;
import nl.fw.yapool.listener.PoolListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple {@link IQueryCache}
 * that also has the option to update some {@link QueryCacheStats}. 
 * @author Fred
 *
 */
public class SimpleQueryCache extends PoolListener implements IQueryCache {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	protected Map<Connection, Map<String, CachedStatement>> qcache;
	protected Map<Object, CachedStatement> qcacheRef;

	protected QueryCacheStats qcacheStats;
	protected IQueryBuilder qb;
	protected String poolName = "SqlPool";

	public SimpleQueryCache() {
		super();
		qcache = new ConcurrentHashMap<Connection, Map<String, CachedStatement>>();
		qcacheRef = new ConcurrentHashMap<Object, CachedStatement>();
		addWantEvent(PoolEvent.DESTROYING);
		setQueryBuilder(new SimpleQueryBuilder());
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

	/**
	 * If set to non-null, query statistics are maintained.
	 */
	public void setStats(QueryCacheStats qcacheStats) {
		this.qcacheStats = qcacheStats;
	}
	
	public QueryCacheStats getStats() {
		return qcacheStats;
	}
	
	@Override
	public void listen(IPool<?> pool) {
		
		pool.getEvents().addPoolListener(this);
		poolName = pool.toString();
	}

	@Override
	public boolean isCached(Object statement) {
		return (qcacheRef.containsKey(statement));
	}

	/**
	 * Closes (named) prepared statements related to a connection that gets event {@link PoolEvent#DESTROYING}
	 */
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		if (poolEvent.getAction() == PoolEvent.DESTROYING) {
			Map<String, CachedStatement> cc = qcache.get(poolEvent.getResource());
			if (cc != null) {
				if (log.isDebugEnabled()) {
					log.debug("Closing " + cc.size() + " cached queries for a database connection that is about to be destroyed.");
				}
				for (CachedStatement cs : cc.values()) {
					cs.close();
					closedQuery(cs);
				}
				cc.clear();
				qcache.remove(poolEvent.getResource());
			}
		}
	}

	/**
	 * Removes CachedStatement from reference cache.
	 */
	protected void closedQuery(CachedStatement cs) {
		
		if (cs.getPs() != null) {
			qcacheRef.remove(cs.getPs());
		} else {
			qcacheRef.remove(cs.getNps());
		}
	}
	
	/**
	 * Adds CachedStatement to reference cache.
	 */
	protected void createdQuery(CachedStatement cs) {
		
		if (cs.getPs() != null) {
			qcacheRef.put(cs.getPs(), cs);
		} else {
			qcacheRef.put(cs.getNps(), cs);
		}
	}

	/**
	 * Returns the map containing the cached statements for the given connection.
	 * Creates a map if needed.
	 */
	protected Map<String, CachedStatement> getConnectionCache(Connection c) {
		
		Map<String, CachedStatement> cc = qcache.get(c);
		if (cc == null) {
			/*
			 * No need to use a concurrent hash-map for "cc":
			 * a connection can only be used by 1 thread at a given time.
			 */
			qcache.put(c, cc = new HashMap<String, CachedStatement>());
		}
		return cc;
	}

	@Override
	public PreparedStatement getQuery(Connection c, String queryName) throws SQLException {
		
		return getQuery(c, queryName, false).getPs();
	}

	@Override
	public NamedParameterStatement getNamedQuery(Connection c, String queryName) throws SQLException {

		return getQuery(c, queryName, true).getNps();
	}

	protected CachedStatement getQuery(Connection c, String queryName, boolean named) throws SQLException {
		
		Map<String, CachedStatement> cc = getConnectionCache(c);
		CachedStatement cs = cc.get(queryName);
		if (cs != null && cs.isClosed()) {
			closedQuery(cs);
			cc.remove(queryName);
			DbConn.closeLogger.warn("Removed query from cache that was closed outside cache: " + cs);
			cs = null;
		}
		if (cs == null) {
			if (qcacheStats != null) {
				qcacheStats.addMiss(queryName);
			}
			if (named) {
				cs = new CachedStatement(c, queryName,  qb.createNamedQuery(c, queryName));
			} else {
				cs = new CachedStatement(c, queryName,  qb.createQuery(c, queryName));
			}
			createdQuery(cs);
			cc.put(queryName, cs);
		} else {
			if (qcacheStats != null) {
				qcacheStats.addHit(queryName);
			}
		}
		return cs;
	}

	@Override
	public String toString() {
		
		int qcacheSize = 0;
		int cachedQueries = 0;
		for (Connection c : qcache.keySet()) {
			Map<String, CachedStatement> cc = qcache.get(c);
			if (cc != null) {
				qcacheSize++;
				cachedQueries += cc.size();
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(" Query cache for pool ").append(poolName)
		.append(" caching queries for ").append(qcacheSize).append(" connections containing ")
		.append(cachedQueries).append(" cached queries.");
		return sb.toString();
	}

}
