package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Creates (named) prepared statements for a connection.
 * <p>
 * Methods do not have to be synchronized since a connection can only be used by 1 thread at a given time.
 * @author Fred
 *
 */
public interface IQueryBuilder {

	/**
	 * Creates a prepared statement for the connection.
	 */
	PreparedStatement createQuery(Connection c, String queryName) throws SQLException;
	/**
	 * Creates a named parameter statement for the connection.
	 */
	NamedParameterStatement createNamedQuery(Connection c, String queryName)  throws SQLException;
}
