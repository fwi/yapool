package nl.fw.yapool.sql;

import java.sql.Connection;

import nl.fw.yapool.PoolPruner;
import nl.fw.yapool.PrunedPool;

/**
 * A pruned pool that shares {@link Connection}s.
 * Uses {@link PoolPruner} for pruning the pool.
 * @author Fred
 *
 */
public class SqlPool extends PrunedPool<Connection> {
	
	/**
	 * Sets some defaults for database connection pools:
	 * <br> - fair resource handout
	 * <br> - synchronized connection creation
	 * <br> - max acquire time 50 seconds
	 * <br> - max idle time 1 minute
	 * <br> - max lease time 5 minutes
	 * <br> - open 1 connection at start (min size).
	 */
	public SqlPool() {
		super();
		setFair(true);
		setSyncCreation(true);
		setMaxAcquireTimeMs(50000L);
		setMaxIdleTimeMs(60000L);
		setMaxLeaseTimeMs(300000L);
		setMinSize(1);
	}
	
	@Override
	public SqlFactory getFactory() {
		return (SqlFactory)super.getFactory();
	}
	
	@Override
	public void open(int amount) {
		
		PoolPruner.getInstance().add(this);
		getFactory().loadDbDriver();
		super.open(amount);
	}

	@Override
	public void close() {
		PoolPruner.getInstance().remove(this);
		super.close();
	}

	/** 
	 * Provides general information and statistics from this pool.
	 */
	public String getStatusInfo() {
		
		final SqlFactory f = getFactory();
		if (f == null) {
			return "No factory for SQL Pooll set.";
		}
		final String lf = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder("Status of database pool");
		sb.append(" ").append(getPoolName()).append(lf);

		sb.append(lf).append("Type: ").append(f.getJdbcDriverClass());
		sb.append(lf).append("URL : ").append(f.getJdbcUrl());
		sb.append(lf).append("User: ").append(f.getUser()).append(lf);

		sb.append(lf).append("Waiting requests: ").append(getWaitingSize());
		sb.append(lf).append("Used connections: ").append(getLeasedSize());
		sb.append(lf).append("Open connections: ").append(getSize())
		.append(" (minimum: ").append(getMinSize()).append(", maximum: ").append(getMaxSize()).append(")").append(lf);

		sb.append(lf).append("Created connections       : ").append(getCreatedCount());
		sb.append(lf).append("Closed invalid connections: ").append(getInvalidCount());
		if (getMaxIdleTimeMs() == 0L) {
			sb.append(lf).append("Not watching idle connections.");
		} else {
			sb.append(lf).append("Closed idle connections   : ").append(getIdledCount())
			.append(" (maximum idle time : ").append(getMaxIdleTimeMs()).append(")");
		}
		if (getMaxLeaseTimeMs() == 0L) {
			sb.append(lf).append("Not watching for expired leases.");
		} else {
			sb.append(lf).append("Number of expired leases  : ").append(getExpiredCount())
			.append(" (maximum lease time: ").append(getMaxLeaseTimeMs()).append(")");
		}
		sb.append(lf);
		sb.append(lf).append("Time-out watch interval        : ").append(getPruneIntervalMs());
		sb.append(lf).append("Maximum connection acquire time: ").append(getMaxAcquireTimeMs());
		if (getMaxLeaseTimeMs() > 0L) {
			sb.append(lf).append("When a connection lease expires: ").append(lf)
			.append("\t- leasing thread is interrupted  : ").append(isInterruptLeaser()).append(lf)
			.append("\t- connection is closed immediatly: ").append(isDestroyOnExpiredLease());
		}
		sb.append(lf).append("Shown time values are in milliseconds.").append(lf);
		return sb.toString();
	}

}
