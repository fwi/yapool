/**
 * Yet Another Pool - a generic pool implementation
 * <p>
 * This package provides 3 pool implementations (each extending the other).
 * <br>All pools:
 * <ul><li>
 * use a {@link nl.fw.yapool.IPoolFactory} (type-safe via Java Generics) to create, validate and destroy pooled resources.
 * </li><li>
 * fire {@link nl.fw.yapool.PoolEvent}s so that custom actions can be executed (see for example {@link nl.fw.yapool.listener.PoolResourcePerformance}).
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
 * One of the most challenging types of pools is a database connection pool,
 * this has been implemented in the {@link nl.fw.yapool.sql} package.
 * Using the proxies technique, {@link nl.fw.yapool.sql.SqlPool} is available as datasource in the
 * {@link nl.fw.yapool.sql.datasource} package. This allows the SqlPool to be used with  
 * Hibernate 3 and 4 (see the projects yapool-hib3 and yapool-hib4) and Tomcat (see {@link nl.fw.yapool.sql.datasource.DataSourceFactory}).
 * <p>
 * A generic object pool implementation is available in the {@link nl.fw.yapool.object} package.
 * <p>
 * The package {@link nl.fw.yapool.socket} contains an example of how to manage
 * the amount of work that is done in a standard Java threadpool in relation with a server socket.
 * As such, it contains an example of when the use of a pool (other then the threadpool)
 * is not needed and when, in this case, a simple atomic integer will do the trick.
 * The (simple) server socket implementation ({@link nl.fw.yapool.socket.SocketAcceptor}) also contains a number of best practices
 * which make it suitable for a production environment.
 * <p>
 * Last but not least, a large number of tests are available that not only touch most of the main source code,
 * but also demonstrate the capabilities of yapool.
 * 
 */
package nl.fw.yapool;
