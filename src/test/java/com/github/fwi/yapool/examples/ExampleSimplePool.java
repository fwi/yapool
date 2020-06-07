package com.github.fwi.yapool.examples;

import java.util.concurrent.atomic.AtomicLong;

import com.github.fwi.yapool.IPoolFactory;
import com.github.fwi.yapool.Pool;

public class ExampleSimplePool {

	public static void main(String[] args) {
		new ExampleSimplePool().demonstrate();
	}
	
	public void demonstrate() {
		
		Pool<Long> pool = new Pool<Long>();
		pool.setFactory(new IPoolFactory<Long>() {

			private final AtomicLong creator = new AtomicLong();
			
			@Override
			public Long create() {
				return creator.incrementAndGet();
			}
			
		});
		
		Long resource = pool.acquire();
		try {
			System.out.println("Got resource " + resource);
		} finally {
			pool.release(resource);
		}

		Long resource1 = pool.acquire();
		try {
			System.out.println("Got resource1 " + resource1);
			Long resource2 = pool.acquire();
			try {
				System.out.println("Got resource2 " + resource2);
			} finally {
				pool.release(resource2);
			}
		} finally {
			pool.release(resource1);
		}
		
		pool.close();
	}

}
