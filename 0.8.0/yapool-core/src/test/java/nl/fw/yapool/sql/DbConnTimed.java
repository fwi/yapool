package nl.fw.yapool.sql;

import java.sql.Connection;

import nl.fw.yapool.sql.DbConn;
import nl.fw.yapool.sql.SqlPool;



/**
 * Same as {@link DbConn} but with time measurements for "acquire from pool" and "connection used".
 * These measurements can be printed out using the getStats() method. 
 * <br>Used by unit-test in DbTask.
 * @author frederikw
 *
 */
public class DbConnTimed extends DbConn {

	public int connAcquireCount;
	/** The total time waiting for a connection from the pool, set via getConnection(). */
	public long connAcquireWaitTime;
	/** The maximum time waiting for a connection from the pool, set via getConnection(). */
	public long connAcquireMaxWaitTime;
	/** The minimum time waiting for a connection from the pool, set via getConnection(). */
	public long connAcquireMinWaitTime = Long.MAX_VALUE;
	/** The total time waiting for a connection from the pool, set via getConnection(). */
	public long connLeaseTime;
	/** The maximum time waiting for a connection from the pool, set via getConnection(). */
	public long connLeaseMaxTime;
	/** The minimum time waiting for a connection from the pool, set via getConnection(). */
	public long connLeaseMinTime = Long.MAX_VALUE;
	/** Time connection was leased. */
	protected long connLeaseStart;
	
	public DbConnTimed(final SqlPool pool) {
		super(pool);
	}
	
	/** Acquires a connection from the pool, but only when conn is null. */
	@Override
	public Connection getConnection() {
		
		if (conn == null) {
			final long tstart = System.currentTimeMillis();
			conn = pool.acquire();
			connLeaseStart = System.currentTimeMillis();
			final long waitTime = (connLeaseStart - tstart);
			if (waitTime < connAcquireMinWaitTime) connAcquireMinWaitTime = waitTime;
			if (waitTime > connAcquireMaxWaitTime) connAcquireMaxWaitTime = waitTime;
			connAcquireWaitTime += waitTime;
			connAcquireCount++;
		}
		return conn;
	}
	
	@Override
	public void close() {
		
		final long leaseTime = (System.currentTimeMillis() - connLeaseStart);
		if (leaseTime < connLeaseMinTime) connLeaseMinTime = leaseTime;
		if (leaseTime > connLeaseMaxTime) connLeaseMaxTime = leaseTime;
		connLeaseTime += leaseTime;
		super.close();
	}

	
	/** Returns a description of this class with the acquire&lease times. */
	public String getStats() {
		return (connAcquireCount > 0 ? "total leased: "+ connAcquireCount 
				+ ", acquire time avg/min/max: " 
				+ (connAcquireWaitTime / connAcquireCount) + " / " + connAcquireMinWaitTime	+ " / " + connAcquireMaxWaitTime 
				+ ", lease time avg/min/max: "
				+ (connLeaseTime / connAcquireCount) + " / " + connLeaseMinTime	+ " / " + connLeaseMaxTime 
				: "no connections acquired");
	}
}
