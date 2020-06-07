package com.github.fwi.yapool;

import com.github.fwi.yapool.IPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** acquires and releases a resource in a loop. */
public class PoolRunner<T> implements Runnable {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());

	private PoolRunnerStopper stopper;
	private IPool<T> pool;

	public PoolRunner(IPool<T> pool, PoolRunnerStopper stopper) {
		this.pool = pool;
		this.stopper = stopper;
	}

	public void run() {
		
		log.debug("Pool runner running.");
		long loopCount = 0;
		while (!stopper.isStop()) {
			try {
				pool.release(pool.acquire());
			} catch (Exception e) {
				log.error("Pool runner got error.", e);
			}
			loopCount++;
		}
		log.debug("Pool runner stopping, number of loops: " + loopCount);
	}

}
