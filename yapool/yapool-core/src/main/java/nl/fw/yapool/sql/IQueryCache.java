package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import nl.fw.yapool.IPoolListener;

/**
 * A query cache that closes queries when the pool destroys connections.
 * This query cache must be registered as a pool event listener to function.
 * <p>
 * Statements returned by this cache may not be closed 
 * (see {@link DbConn#DbConn(SqlPool, IQueryCache)} which provides this function).
 * A query builder is used to create statement-objects.
 * See also {@link SimpleQueryCache} and {@link SimpleQueryBuilder}
 * @author Fred
 *
 */
public interface IQueryCache extends IPoolListener {
	
	/**
	 * Creates a prepared statement for the connection or re-uses one from cache.
	 */
	PreparedStatement getQuery(Connection c, String queryName) throws SQLException;
	/**
	 * Creates a named parameter statement for the connection or re-uses one from cache.
	 */
	NamedParameterStatement getNamedQuery(Connection c, String queryName) throws SQLException;
	
	/**
	 * The query builder used to create (named parameter) prepared statements.
	 */
	void setQueryBuilder(IQueryBuilder qb);
	
	/**
	 * Returns true if the (named) prepared statement is cached.
	 */
	boolean isCached(Connection c, Object statement);
}
