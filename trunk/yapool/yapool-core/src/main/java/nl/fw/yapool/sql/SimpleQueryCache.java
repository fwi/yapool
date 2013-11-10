package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.fw.yapool.PoolEvent;
import nl.fw.yapool.listener.PoolListener;

/**
 * A simple {@link IQueryCache}
 * that also has the option to update some {@link QueryCacheStats}. 
 * @author Fred
 *
 */
public class SimpleQueryCache extends PoolListener implements IQueryCache {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	protected Map<Connection, Map<String, Object>> qcache = new ConcurrentHashMap<Connection, Map<String, Object>>();
	protected QueryCacheStats qcacheStats;
	protected IQueryBuilder qb;
	protected String poolName = "SqlPool";
	
	public SimpleQueryCache() {
		super();
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
	
	public void listen(SqlPool pool) {
		
		pool.getEvents().addPoolListener(this);
		poolName = pool.getPoolName();
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
	 * @param queryName the name/ID associated with the statement.
	 * @param o the statement
	 */
	protected void closeQuery(String queryName, Object o) {
		
		if (o instanceof PreparedStatement) {
			DbConn.close(((PreparedStatement)o));
		} else if (o instanceof NamedParameterStatement) {
			DbConn.close(((NamedParameterStatement)o));
		} else {
			DbConn.closeLogger.warn("Cannot close unknown type of statement named " + queryName + ", statement: " + o);
		}
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
			cc.put(queryName, o);
		} else {
			if (qcacheStats != null) {
				qcacheStats.addHit(queryName);
			}
		}
		return o;
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
		return sb.toString();
	}

}
