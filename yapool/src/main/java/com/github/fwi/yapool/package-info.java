/**
 * Yet Another Pool - a generic pool implementation
 * <p>
 * This package provides 3 pool implementations (each extending the other).
 * <br>All pools:
 * <ul><li>
 * use a {@link com.github.fwi.yapool.IPoolFactory} (type-safe via Java Generics) to create, validate and destroy pooled resources.
 * </li><li>
 * fire {@link com.github.fwi.yapool.PoolEvent}s so that custom actions can be executed (see for example {@link com.github.fwi.yapool.listener.PoolResourcePerformance}).
 * </li><li>
 * use Java Concurrent to run fast and reliable.
 * </li></ul>
 * <p>
 * Following 3 pool implementations are provided:
 * <ul><li>
 * Basic: provides an object re-use capability.
 * </li><li>
 * Bound: ensures a limit to the amount of resources used. 
 * </li><li>
 * Pruned: keeps resources in the pool healthy.  
 * </li></ul>
 * <p>
 * A generic object pool implementation is available in the <tt>com.github.fwi.yapool.object</tt> package.
 * <p>
 * Last but not least, a large number of tests are available that not only touch most of the main source code,
 * but also demonstrate the capabilities of yapool.
 * 
 */
package com.github.fwi.yapool;
