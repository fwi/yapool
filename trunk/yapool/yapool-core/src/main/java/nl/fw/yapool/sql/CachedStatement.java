package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Helper class for QueryCache implementations.
 * @author fwiers
 *
 */
public class CachedStatement {

	private final Connection c;
	private final String queryName;
	
	private final PreparedStatement ps;
	private final NamedParameterStatement nps;
	
	private int weight;

	public CachedStatement(Connection c, String queryName, PreparedStatement ps) {
		this.c = c;
		this.queryName = queryName;
		this.ps = ps;
		this.nps = null;
	}
	
	public CachedStatement(Connection c, String queryName, NamedParameterStatement nps) {
		this.c = c;
		this.queryName = queryName;
		this.ps = null;
		this.nps = nps;
	}

	
	public Connection getConnection() { return c; }
	public String getQueryName() { return queryName; }
	
	public PreparedStatement getPs() { return ps; }
	public NamedParameterStatement getNps() { return nps; }

	public int getWeight() { return weight; }
	public void setWeight(int weight) { this.weight = weight; }
	
	public boolean isNamed() {
		return (nps != null);
	}
	
	public boolean isClosed() {
		
		boolean closed = true;
		if (getPs() != null) {
			try {
				closed = getPs().isClosed();
			} catch (Exception ignored) {}
		} else if (getNps() != null) {
			try {
				closed = getNps().getStatement().isClosed();
			} catch (Exception ignored) {}
		}
		return closed;
	}
	
	public void close() {
		
		if (getPs() != null) {
			DbConn.close(getPs());
		}
		if (getNps() != null) {
			DbConn.close(getNps());
		}
	}
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder("Cached ");
		if (isNamed()) {
			sb.append("named parameter ");
		} else {
			sb.append("prepared ");
		}
		sb.append("statement on connection [").append(getConnection().hashCode()).append("]")
		.append("[").append(getQueryName()).append("] with weight ").append(getWeight());
		return sb.toString();
	}
}
