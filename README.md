# Yapool

A generic object pool suitable for basic re-use of objects 
up to full management of a healthy pool with idle-timeouts, lease-timeouts and maximum lifetime timeouts.

"No locks were hurt in the Yapool project."

Yapool does not use locks to synchronize which prevents potential bottlenecks.
Yapool can emit events for pool related actions (release, destroy, acquire, etc.).
Yapool events can be used to gather statistics but also provide entrypoints for customizations.

Pool implementations inherit each other from basic to full-featured: `IPool > Pool > BoundPool > PrunedPool`
Finally, `PoolsMap` is available to manage a "Pool of Pools".

A pool needs an `IPoolFactory` to create, validate and destroy pool resources.
A simple pool creation example:

```java
Pool<Long> pool = new Pool<Long>();
pool.setFactory(new IPoolFactory<Long>() {

	private final AtomicLong creator = new AtomicLong();
	
	@Override
	public Long create() {
		return creator.incrementAndGet();
	}
});
```

A `PrunedPool` needs to be registered with a `PoolPruner` to be maintained properly:

```java
PoolPruner.getInstance().add(pool);
```
		
Both a `PrunedPool` and a `BoundPool` (which limits the maximum amount of resources in the pool)
need to be opened:

```java
pool.open();  
```

Pool properties can be changed at runtime (even after opening the pool), all `public` operations are thread-safe.

Pool usage is similar in all cases:

```java
Long resource = pool.acquire();
try {
	System.out.println("Got resource " + resource);
} finally {
	pool.release(resource);
}
pool.close();
```

When a `PrunedPool`  is closed, the `PoolPruner` task stops 
and any executors are stopped and closed when this was the last pool that was being pruned.

Pool events are used by the `com.github.fwi.yapool.listener.LeaserAcquiredTrace` class to log info-messages
with stack-traces of resources that were taken from the pool but not returned within the lease-period.
This is useful to track down coding mistakes or badly behaving application parts.
The tracer can be added to a `PrunedPool` using:

```java
pool.getEvents().addPoolListener(new LeaserAcquiredTrace());
```

Pool performance statistics can be reported using the `com.github.fwi.yapool.listener.PoolPerformance` class
which can be added to the `PrunedPool` as a listener just like the `LeaserAcquiredTrace` class.
Note that this class is for debugging purposes only, this class is not suitable for production.

The `PoolsMap` or "pool of pools" implementation can be used to manage resources 
that have the same base-class but different configurations. For example, SMTP-connections to different servers:
the type of connection is the same, but the configuration of the connection is a little bit different.
A `PoolsMap` requires the use of a `IPoolsMapFactory` which has some strict requirements,
see the [Javadoc for the interface](./src/main/java/com/github/fwi/yapool/IPoolsMapFactory.java).
For now, `PoolsMap` is only demonstrated in the related test-class [TestPoolsMap](./src/test/java/com/github/fwi/yapool/TestPoolsMap.java). 

A special-purpose `ObjectPool` is available in the `com.github.fwi.yapool.object` package.
This pool has virtually no limit on size (65k) and no maximum lease-time, but does have an idle-timeout.
Such an object-pool can be useful in situations where objects should be re-used
and some memory is freed when objects in the pool are no longer used. 

A number of examples that show how Yapool can be used are available in the `com.github.fwi.yapool.examples` 
[package](./src/test/java/com/github/fwi/yapool/examples) in the Java test-classes directory.

A demonstration of customization can be found in the `com.github.fwi.yapool.statefull` 
[package](./src/test/java/com/github/fwi/yapool/statefull) in the Java test-classes directory.
The classes in this package capture the contents of a pool when it is closed 
and add the contents back into the pool when it is opened (class `TestSaveRestore`).  

## Development

To install:

	mvn clean install
	
Full build:

	mvn clean verify

Coverage report:

	mvn cobertura:cobertura

Report is stored in `target/site/cobertura/index.html`

Zip project:

	mvn assembly:single -Pzip

