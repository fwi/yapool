package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A {@link IQueryBuilder} that uses the given sql-IDs as sql-statements.
 * Overload the methods to provide specific behavior for special sql-IDs.
 * @author Fred
 *
 */
public class SimpleQueryBuilder implements IQueryBuilder {

	/**
	 * Creates a prepared statement for the connection.
	 * @param sqlId Used as the sql-query.
	 */
	@Override
	public PreparedStatement createQuery(Connection c, String sqlId)  throws SQLException {
		return c.prepareStatement(sqlId);
	}

	/**
	 * Creates a named prepared statement for the connection.
	 * @param sqlId Used as the named sql-query.
	 */
	@Override
	public NamedParameterStatement createNamedQuery(Connection c, String sqlId)  throws SQLException {
		return new NamedParameterStatement(c, sqlId);
	}
	
}
