package nl.fw.yapool.sql;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link IQueryBuilder} that uses the given sql-IDs as sql-statements.
 * Overload the methods to provide specific behavior for special sql-IDs.
 * @author Fred
 *
 */
public class SimpleQueryBuilder implements IQueryBuilder {

	private static final Logger log = LoggerFactory.getLogger(SimpleQueryBuilder.class);

	/** Map containing sqlIDs and their associated queries. */
	protected Map<String, String> queries = new HashMap<String, String>();

	/** Set containing sqlID's of prepared statements that generate keys. */
	protected Set<String> genKeyPreparedStatement = new HashSet<String>();
	/** Set containing sqlID's of named prepared statements that generate keys. */
	protected Set<String> genKeyNamedPreparedStatement = new HashSet<String>();
	
	/**
	 * Looks up the query associated with the sqlID.
	 * If there is no query associated with the sqlID, the sqlID is returned.
	 */
	public String getQuery(String sqlId) {
		
		String q = queries.get(sqlId);
		return (q == null ? sqlId : q);
	}

	/** Map containing sqlIDs and their associated queries. */
	public Map<String, String> getQueryMap() {
		return queries;
	}
	
	/**
	 * Determines if a query returns generated keys.
	 * Uses {@link #genKeyPreparedStatement} and {@link #genKeyNamedPreparedStatement}. 
	 * @param sqlId The query (ID).
	 * @param named True for a named prepared statement, false for a prepared statement.
	 * @return True if the statement should return generated keys.
	 */
	public boolean hasGeneratedKeys(String sqlId, boolean named) {
		return (named ? genKeyNamedPreparedStatement.contains(sqlId) :
			genKeyPreparedStatement.contains(sqlId));
	}
	
	/**
	 * Creates a prepared statement for the connection.
	 * @param sqlId Used as the sql-query.
	 */
	@Override
	public PreparedStatement createQuery(Connection c, String sqlId)  throws SQLException {
		
		String q = getQuery(sqlId);
		return (hasGeneratedKeys(sqlId, false) ? 
				c.prepareStatement(q, Statement.RETURN_GENERATED_KEYS) : 
					c.prepareStatement(q));
	}

	/**
	 * Creates a named prepared statement for the connection.
	 * @param sqlId Used as the named sql-query.
	 */
	@Override
	public NamedParameterStatement createNamedQuery(Connection c, String sqlId)  throws SQLException {
		
		String q = getQuery(sqlId);
		return (hasGeneratedKeys(sqlId, true) ? 
				new NamedParameterStatement(c, q, Statement.RETURN_GENERATED_KEYS) : 
					new NamedParameterStatement(c, q));
	}
	
	/** Beginning or end marker for a query in a query-file "{@code --[}". */
	public static final String QUERY_NAME_MARKER = "--[";

	/**
	 * Calls {@link #loadQueries(Reader, Map)} with an empty query map 
	 * and returns the query map after loading the queries.
	 */
	public static LinkedHashMap<String, String> loadQueries(Reader in) throws IOException {
		
		LinkedHashMap<String, String> qmap = new LinkedHashMap<String, String>();
		loadQueries(in, qmap);
		return qmap;
	}
	
	/**
	 * Loads queries form a sql-file/inputstream.
	 * Queries must be formatted using the {@link #QUERY_NAME_MARKER} in the following manner:
	 * <pre>{@literal 
-- Just a comment
// Also a comment
-- Empty start tag, query will get query-count as ID (in this case "1").
--[]
select something from somehwere
-- End query tag.
--[/]
-- Named query, ID will be INSERT_ITEM
--[INSERT_ITEM]
insert into items (item_key, item) values (@itemKey, @item)
-- Name in end tag must match start-tag.
--[/INSERT_ITEM]
--[BIG_QUERY]
select a, b,c 
from x, y, z
where this=that and other=stuff
--[/BIG_QUERY]
	 * }</pre>
	 * All text between begin and end tag is not formatted in any way and taken literally as the query,
	 * but a line is skipped if it is empty, starts with {@code --} (but not {@code --[} which is the tag-marker)
	 * or starts with {@code //}.
	 * @param in The characters to parse (stream is NOT closed by this method).
	 * @param qmap The map that will contain the query names/IDs and queries.
	 * @throws IOException if formatting is incorrect.
	 */
	public static void loadQueries(Reader in, Map<String, String> qmap) throws IOException {
		
		LineNumberReader reader = new LineNumberReader(in);
		String line = null;
		String q[] = null;
		int qCount = 0;
		while ((line = reader.readLine()) != null) {
			if (line.trim().isEmpty()) continue;
			if (line.startsWith(QUERY_NAME_MARKER)) {
				line = line.substring(QUERY_NAME_MARKER.length()-1);
			}
			if (line.startsWith("--") || line.startsWith("//")) {
				continue;
			}
			if (line.charAt(0) == '[' && line.charAt(line.length()-1) == ']') {
				if (line.charAt(1) == '/') {
					if (q == null) {
						throw new IOException("Unexpected closing query-key at line " + reader.getLineNumber() + ": " + line);
					} else if (q[0] == null) {
						throw new IOException("Closing query-key found without opening query-key at line " + reader.getLineNumber() + ": " + line);
					} else if (q[1] == null) {
						throw new IOException("Closing query-key found without query at line " + reader.getLineNumber() + ": " + line);
					}
					String k = line.substring(2, line.length()-1);
					if (k.isEmpty()) {
						k = Integer.toString(qCount + 1);
					}
					if (!k.equals(q[0])) {
						throw new IOException("Closing query-key does not match opening query key [" + q[0] + "] at line " + reader.getLineNumber() + ": " + line);
					}
					if (qmap.containsKey(q[0])) {
						if (log.isDebugEnabled()) {
							log.debug("Replacing old query for " + q[0]);
						}
					}
					qmap.put(q[0], q[1]);
					qCount++;
					if (log.isTraceEnabled()) {
						log.trace("Added query " + q[0] + ":\n" + q[1]);
					}
					q = null;
				} else {
					if (q != null) {
						throw new IOException("Missing closing query key at line " + reader.getLineNumber());
					}
					String k = line.substring(1, line.length()-1);
					if (k.isEmpty()) {
						k = Integer.toString(qCount + 1);
					}
					q = new String[2];
					q[0] = k;
				}
			} else {
				if (q == null) {
					throw new IOException("Missing opening query key at line " + reader.getLineNumber() + ": " + line);
				}
				if (q[1] == null) {
					q[1] = line;
				} else {
					q[1] = q[1] + "\n" + line;
				}
			}
		}
		log.debug("Loaded " + qCount + " queries.");
	}
	
}
