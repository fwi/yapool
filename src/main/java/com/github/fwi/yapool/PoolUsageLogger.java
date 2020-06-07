package com.github.fwi.yapool;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fwi.yapool.PrunedPool;

/**
 * Logs a pruned pool's status at regular intervals using all readily available counters and sizes
 * (there is no performance impact on the pool itself).
 * E.g. a log-statement could look like:
 * <br>{@code PoolName size: 10, leased: 6, idle: 4, created: 1, EXPIRED: 1}
 * <br>If values do not change or are of no interest (zero), nothing is logged.
 * If nothing happens with the pool, no log statement will appear.
 * @author FWiers
 *
 */
public class PoolUsageLogger implements Runnable {

	/** 
	 * Logger used to log report.
	 * Can be changed to a logger with category "{@code usage.report}" for example.
	 */
	public Logger log = LoggerFactory.getLogger(getClass());

	private PrunedPool<?> pool;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> scheduledTask;
	private long reportIntervalMs = 10000L;
	private volatile boolean stop;

	public PoolUsageLogger() {
		super();
	}
	public PoolUsageLogger(PrunedPool<?> pool) {
		super();
		setPool(pool);
	}
	
	public void setPool(PrunedPool<?> pool) {
		this.pool = pool;
	}
	public PrunedPool<?> getPool() { 
		return pool; 
	}
	
	public long getReportIntervalMs() {
		return reportIntervalMs;
	}

	/** Default 10 seconds. */
	public void setReportInterval(long reportIntervalMs) {
		this.reportIntervalMs = reportIntervalMs;
	}

	public void start(ScheduledExecutorService executor) {
		
		if (pool == null) {
			log.error("No pool set, cannot start pool usage logger.");
			return;
		}
		this.executor = executor;
		scheduleTask();
		debug("pool usage logger started.");
	}
	
	protected void scheduleTask() {
		scheduledTask = executor.schedule(this, getReportIntervalMs(), TimeUnit.MILLISECONDS);
	}
	
	public void stop() {
		
		stop = true;
		if (scheduledTask != null) {
			scheduledTask.cancel(false);
			scheduledTask = null;
		}
		debug("pool usage logger stopped.");
	}
	
	protected void debug(String msg) {
		
		if (log.isDebugEnabled()) {
			String prefix = pool.getPoolName() + " - ";
			log.debug(prefix + msg);
		}
	}
	
	@Override
	public void run() {
		
		if (stop) {
			scheduledTask = null;
		} else {
			if (report()) {
				log.info(getReport());
			}
			scheduleTask();
		}
	}
	
	protected long lastCreated;
	protected long lastExpired;
	protected long lastIdled;
	protected int lastIdleSize;
	protected long lastInvalid;
	protected int leasedSize;
	protected int lastSize;
	protected int lastWaiting;
	
	/**
	 * Evaluates the "lastCount" values.
	 * @return true if a resource is leased or any values have changed since last call to {@link #getReport()},
	 */
	public boolean report() {
		
		leasedSize = pool.getLeasedSize();
		boolean report = (leasedSize > 0)
				|| (pool.getCreatedCount() != lastCreated) 
				|| (pool.getExpiredCount() != lastExpired)
				|| (pool.getIdledCount() != lastIdled)
				|| (pool.getIdleSize() != lastIdleSize)
				|| (pool.getInvalidCount() != lastInvalid)
				|| (pool.getLeasedSize() != leasedSize)
				|| (pool.getSize() != lastSize)
				|| (pool.getWaitingSize() != lastWaiting);
		return report;
	}
	
	
	/** Creates a report for the log and updates the "lastCount" values. */
	public String getReport() {

		StringBuilder sb = new StringBuilder(128);

		sb.append(pool.getPoolName()).append(" ");
		lastSize = pool.getSize();
		sb.append("size: ").append(lastSize);
		if (leasedSize > 0) {
			sb.append(", leased: ").append(leasedSize);
		}
		lastIdleSize = pool.getIdleSize();
		if (lastIdleSize > 0) {
			sb.append(", idle: ").append(lastIdleSize);
		}
		lastWaiting = pool.getWaitingSize();
		if (lastWaiting > 0) {
			sb.append(", waiting: ").append(lastWaiting);
		}
		if (pool.getExpiredCount() != lastExpired) {
			sb.append(", EXPIRED: ").append(pool.getExpiredCount() - lastExpired);
			lastExpired = pool.getExpiredCount();
		}
		if (pool.getInvalidCount() != lastInvalid) {
			sb.append(", INVALID: ").append(pool.getInvalidCount() - lastInvalid);
			lastInvalid = pool.getInvalidCount();
		}
		if (pool.getCreatedCount() != lastCreated) {
			sb.append(", created: ").append(pool.getCreatedCount() - lastCreated);
			lastCreated = pool.getCreatedCount();
		}
		if (pool.getIdledCount() != lastIdled) {
			sb.append(", idled: ").append(pool.getIdledCount() - lastIdled);
			lastIdled = pool.getIdledCount();
		}
		return sb.toString();
	}
}
