package nl.fw.yapool;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A default prune task that prunes a pool at regular intervals (uses {@link PrunedPool#getPruneIntervalMs()}).
 * This runnable is also used when {@link PoolPruner#add(PrunedPool)} is called.
 * The shared pruner will also call {@link PrunedPool#setPruneTask(PruneTask)} to register this prune task.
 * @author fwiers
 *
 */
public class PruneTask implements Runnable {

	private final PrunedPool<?> pool;
	private final ScheduledExecutorService executor;
	private ScheduledFuture<?> scheduledTask;
	private volatile boolean stop;
	private volatile boolean started;
	private PoolPruner pruner;
	
	public PruneTask(ScheduledExecutorService executor, PrunedPool<?> pool) {
		this.executor = executor;
		this.pool = pool;
	}
	
	/**
	 * The (shared) pruner used for pruning pools.
	 */
	public void setPruner(PoolPruner pruner) {
		this.pruner = pruner;
	}
	
	/**
	 * Starts pruning the pool at regular intervals.
	 */
	public void start() {
		stop = false;
		if (started) {
			if (pool.log.isTraceEnabled()) {
				pool.log.trace(pool.getPoolName() + " pool pruner task already started.");
			}
		} else {
			started = true;
			scheduleTask();
		}
	}

	/**
	 * Stops pruning the pool.
	 * This method is called via {@link PrunedPool#close()}.
	 * If a shared pruner was set, this prune task and pool is removed from the shared pruner.
	 */
	public void stop() {
		
		if (stop) return;
		stop = true;
		if (scheduledTask != null) {
			scheduledTask.cancel(false);
			scheduledTask = null;
		}
		if (pruner != null) {
			pruner.remove(pool);
		}
	}
	
	private void scheduleTask() {
		
		if (!stop) {
			scheduledTask = executor.schedule(this, pool.getPruneIntervalMs(), TimeUnit.MILLISECONDS);
			if (pool.log.isTraceEnabled()) {
				pool.log.trace(pool.getPoolName() + " Scheduled new prune task.");
			}
		}
	}
	
	@Override
	public void run() {
		
		if (!pool.isClosed()) {
			pool.prune();
			scheduleTask();
		}
	}
}
