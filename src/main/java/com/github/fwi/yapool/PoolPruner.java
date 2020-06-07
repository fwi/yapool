package com.github.fwi.yapool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton pool-pruner that can prune multiple pools.
 * It will start/stop itself when pools are added/removed.
 * See also {@link PruneTask}.
 * <p>
 * The singleton-pattern from Bill Pugh is used as described on
 * <a href="http://en.wikipedia.org/wiki/Singleton_pattern#Initialization_On_Demand_Holder_Idiom">WikiPedia</a>
 * @author FWiers
 *
 */
public class PoolPruner {

    /**
    * SingletonHolder is loaded on the first execution of Singleton.getInstance() 
    * or the first access to SingletonHolder.INSTANCE, not before.
    */
    private static class SingletonHolder { 
            public static final PoolPruner INSTANCE = new PoolPruner();
    }

    public static PoolPruner getInstance() {
            return SingletonHolder.INSTANCE;
    }

	protected Logger log = LoggerFactory.getLogger(getClass());

    private ScheduledExecutorService executor;
    private CopyOnWriteArrayList<PrunedPool<?>> pools = new CopyOnWriteArrayList<PrunedPool<?>>();
    private volatile boolean started;
    private boolean shutdownExecutor;
    
	/** 
	 * This class should not be instantiated, use {@link #getInstance()} instead.
	 * Constructor is protected to allow extending this class (e.g. for unit testing).
	 */
    protected PoolPruner() {}

    /**
     * Starts pruning the given pool.
     * If this is the first pruned pool, this pool pruner is started. 
     * @return true when pool is pruned, false otherwise (prune interval is less than 1, pool is null or pool is closed).
     */
    public boolean add(PrunedPool<?> pool) {
    	
    	if (pool == null || pool.isClosed()) {
    		return false;
    	}
    	if (pools.contains(pool)) {
    		return true;
    	}
    	if (pool.getPruneIntervalMs() < 1L) {
    		return false;
    	}
   		// Prevent starting while stopping
		synchronized (this) {
    		if (!started) {
    	    	log.debug("Pool pruner starting.");
    			if (getExecutor() == null) {
    				ScheduledThreadPoolExecutor stp = new ScheduledThreadPoolExecutor(1);
    				stp.setRemoveOnCancelPolicy(true);
    				setExecutor(stp);
    				setShutdownExecutor(true);
    			}
    			started = true;
    		}
       		pools.add(pool);
    		if (pool.getPruneTask() == null) {
    			pool.setPruneTask(createPruneTask(executor, pool));
    		}
    		pool.getPruneTask().setPruner(this);
    		// pool task is started when pool is opened, 
    		// but unit-tests first open the pool and then add it to the pruner. 
    		if (pool.isOpen()) {
    			pool.getPruneTask().start();
    		}
		}
    	return true;
    }
    
    /* Separate method to facilitate unit testing. */
    protected PruneTask createPruneTask(ScheduledExecutorService executor, PrunedPool<?> pool) {
		return new PruneTask(executor, pool);
    }
    
    /**
     * Stops pruning the given pool.
     * If this was the last pruned pool, this pool pruner is stopped.
     */
    public boolean remove(PrunedPool<?> pool) {
    	
    	if (pools.contains(pool)) {
       		pools.remove(pool);
			pool.setPruneTask(null);
       		stopWhenEmpty();
    	}
    	return true;
    }

    /** Stops this pool pruner and all prune-tasks for all registered pools. */
    public void stop() {
    	stop(false);
    }

    /** Stops this pool pruner only when there are no more registered pools. */
    public void stopWhenEmpty() {
    	stop(true);
    }

    protected void stop(boolean onlyWhenEmpty) {
    	
    	// Prevent stopping while starting.
    	synchronized(this) {
    		if (!started || (onlyWhenEmpty && !pools.isEmpty())) {
    			return;
    		}
        	started = false;
           	log.trace("Pool pruner stopping.");
        	// Create a copy since pools are removed from the set when prune-task is stopped.
        	Set<PrunedPool<?>> poolsCopy = new HashSet<PrunedPool<?>>(pools);
        	for (PrunedPool<?> p : poolsCopy) {
        		p.getPruneTask().stop();
        	}
        	pools.clear();
        	if (getExecutor() != null && isShutdownExecutor()) {
        		getExecutor().shutdown();
               	log.trace("Pool pruner executor stopped.");
        		setExecutor(null);
        	}
        	log.debug("Pool pruner stopped.");
    	}
    }

	/* *** bean methods *** */

    /** Amount of pools being watched. */
    public int getSize() { 
    	return pools.size(); 
    } 

    public boolean isRunning() { 
    	return started; 
    }

    /**
     * Sets the executor to use for pruning tasks.
     * See also {@link #getExecutor()}.
     * Must be set before this pruner is started, cannot be updated after pruner has started.
     */
    public void setExecutor(ScheduledExecutorService executor) { 
    	if (!isRunning()) {
    		this.executor = executor;
    	}
    }
    
    /** 
     * The executor used to schedule prune tasks.
     * If none is set, a default {@link ScheduledThreadPoolExecutor} is set when the pruner is started
     * and {@link #setShutdownExecutor(boolean)} is set to true.
     */
    public ScheduledExecutorService getExecutor() { 
    	return executor; 
    }

    /** 
     * If true, shuts down the executor when there are no more pools to prune.
     * Default true if no executor was (explicitly) set via {@link #setExecutor(ScheduledExecutorService)}. 
     */ 
	public boolean isShutdownExecutor() {
		return shutdownExecutor;
	}
	
	/**
	 * Shutdown executor when no more pools are pruned, or not.
	 * See also {@link #isShutdownExecutor()}.
     * Must be set before this pruner is started, cannot be updated after pruner has started.
	 */
	public void setShutdownExecutor(boolean shutdownExecutor) {
    	if (!isRunning()) {
    		this.shutdownExecutor = shutdownExecutor;
    	}
	}

}
