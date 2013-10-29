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
	 * @param sqlId the ID associated with the statement.
	 * @param o the statement
	 * @return true if statement was closed, false otherwise.
	 */
	protected boolean closeQuery(String sqlId, Object o) {
		
		boolean closed = false;
		try {
			if (o instanceof PreparedStatement) {
				((PreparedStatement)o).close();
				closed = true;
			} else if (o instanceof NamedParameterStatement) {
				((NamedParameterStatement)o).close();
				closed = true;
			}
		} catch (Exception e) {
			log.error("Could not close statement for " + sqlId, e);
		}
		return closed;
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
	public PreparedStatement getQuery(Connection c, String sqlId) throws SQLException {
		
		Map<String, Object> cc = getConnectionCache(c);
		Object o = cc.get(sqlId);
		PreparedStatement ps = null;
		if (o == null) {
			if (qcacheStats != null) {
				qcacheStats.addMiss(sqlId);
			}
			ps = qb.createQuery(c, sqlId);
			if (ps == null) {
				throw new RuntimeException("Could not create a prepared statement for SQL ID " + sqlId);
			}
			cc.put(sqlId, ps);
		} else {
			if (qcacheStats != null) {
				qcacheStats.addHit(sqlId);
			}
			ps = (PreparedStatement)o;
		}
		return ps;
	}

	@Override
	public NamedParameterStatement getNamedQuery(Connection c, String sqlId) throws SQLException {

		Map<String, Object> cc = getConnectionCache(c);
		Object o = cc.get(sqlId);
		NamedParameterStatement nps = null;
		if (o == null) {
			if (qcacheStats != null) {
				qcacheStats.addMiss(sqlId);
			}
			nps = qb.createNamedQuery(c, sqlId);
			if (nps == null) {
				throw new RuntimeException("Could not create a named prepared statement for SQL ID " + sqlId);
			}
			cc.put(sqlId, nps);
		} else {
			if (qcacheStats != null) {
				qcacheStats.addHit(sqlId);
			}
			nps = (NamedParameterStatement)o;
		}
		return nps;
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

}
