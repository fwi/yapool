package com.github.fwi.yapool;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolsMapCleanTask implements Runnable {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PoolsMap<?, ?> poolsMap;
	private volatile boolean stop;
	private volatile ScheduledFuture<?> scheduledTask;
	
	public PoolsMapCleanTask(PoolsMap<?, ?> poolsMap) {
		this.poolsMap = poolsMap;
	}
	
	/**
	 * Cleans the pool and re-schedules task.
	 */
	@Override
	public void run() {
		
		try {
			poolsMap.clean();
		} catch (Exception e) {
			log.error("Failed to clean pools-map {}.", poolsMap, e);
		}
		schedule();
	}
	
	/**
	 * Schedules this task when not stopped and pools-map is open.
	 */
	public void schedule() {
		
		ScheduledExecutorService executor = poolsMap.getExecutor();
		if (executor == null || isStopped() || poolsMap.isClosed()) {
			log.debug("Clean-task stopped for pools-map {}", poolsMap);
		} else {
			scheduledTask = executor.schedule(this, poolsMap.getCleanIntervalMs(), TimeUnit.MILLISECONDS);
			log.trace("Clean-task scheduled for pools-map {}", poolsMap);
		}
	}
	
	public boolean isStopped() {
		return stop;
	}
	
	public void stop() {
		
		if (stop) return;
		stop = true;
		ScheduledFuture<?> st = scheduledTask;
		if (st != null) {
			st.cancel(false);
			scheduledTask = null;
		}
		if (log.isTraceEnabled()) {
			log.trace("[{}} clean task stopped.", poolsMap.getPoolsName());
		}
	}

}
