package com.github.fwi.yapool.examples;

import java.util.concurrent.atomic.AtomicLong;

import com.github.fwi.yapool.IPoolFactory;
import com.github.fwi.yapool.PoolPruner;
import com.github.fwi.yapool.PrunedPool;

public class ExamplePrunedPool {

	public static void main(String[] args) {
		new ExamplePrunedPool().demonstrate();
	}
	
	public void demonstrate() {
		
		PrunedPool<Long> pool = new PrunedPool<Long>();
		pool.setFactory(new IPoolFactory<Long>() {

			private final AtomicLong creator = new AtomicLong();
			
			@Override
			public Long create() {
				return creator.incrementAndGet();
			}
			
		});
		// just for idle-time testing purposes, see futher on.
		pool.setMaxIdleTimeMs(5L);
		pool.setPruneIntervalMs(2L);
		
		PoolPruner.getInstance().add(pool);
		pool.open(2); // put 2 resources in the pool.
		System.out.println("Pool size: " + pool.getSize());
		
		// test removal of idle resources
		sleep(10L);
		System.out.println("Pool size: " + pool.getSize());
		pool.close(); // Also stops the Poolpruner.
	}
	
	public static void sleep(long sleepTimeMs) {
		try {
			Thread.sleep(sleepTimeMs);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
