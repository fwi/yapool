# Yapool

A generic object pool suitable for basic re-use of objects 
to full management of a healthy pool with idle-timeouts, lease-timeouts and maximum lifetime timeouts.

"No locks were hurt in the Yapool project."

Yapool does not use locks to synchronize which prevents potential bottlenecks.
Yapool can emit events for pool related actions (release, destroy, acquire, etc.).
Yapool events can be used to gather statistics but also provide entrypoints for customizations.

Pool implementations inherited each other from basic to full-featured: `IPool > Pool > BoundPool > PrunedPool`

A pool uses a `IPoolFactory` to create, validate and destroy pool resources.
Simple pool creation example:

```lang=java
Pool<Long> pool = new Pool<Long>();
pool.setFactory(new IPoolFactory<Long>() {

	private final AtomicLong creator = new AtomicLong();
	
	@Override
	public Long create() {
		return creator.incrementAndGet();
	}
});
```

For a `PrunedPool` that must be actively managed, register to pool with the `PoolPruner`:

```lang=java
PoolPruner.getInstance().add(pool);
```
		
Both a `PrunedPool` and a `BoundPool` (which limits the maximum amount of resources in the pool)
need to be opened:

```lang=java
pool.open();  
```

Pool usage is similar in all cases:

```lang=java
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
```

When a `PrunedPool`  is closed, the `PoolPruner` task stops 
and any executors are stopped and closed when this was the last pool that was being pruned. 

## Development

To install:

	mvn clean install
	
Full build for core:

	cd yapool
	mvn clean verify assembly:single

Coverage report:

	cd yapool
	mvn cobertura:cobertura

Report is stored in directory `target/site/cobertura`
