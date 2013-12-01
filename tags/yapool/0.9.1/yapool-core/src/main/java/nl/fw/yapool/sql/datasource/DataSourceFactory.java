package nl.fw.yapool.sql.datasource;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data-source factory that calls a {@link IPoolBuilder} to configure and open a database pool.
 * In Tomcat's context.xml, a configuration can look like:
 * <pre>&lt;Resource name="myDS"
        type="javax.sql.DataSource"
        factory="nl.fw.pool.sql.datasource.DateSourceFactory" 
        auth="Container"
        poolBuilder="com.mycompany.DSPoolBuilder"
        jdbcUrl="jdbc:hsqldb:mem:testdb"
   /&gt;</pre>
 * This factory creates and runs the class specified in "poolBuilder" (which must implement {@link IPoolBuilder}).
 * This is the primary property to use for creating and configuring your own custom database pool.
 * If no value is specified for "poolBuilder", {@link HsqlPoolBuilder} is used.
 * <br>In Tomcat 6 the data-source can be retrieved using:
 * <pre>InitialContext ic = new InitialContext();
DataSource ds = (DataSource)ic.lookup("java:comp/env/myDS");
ds.getConnection().close();</pre>
 * The last statement acquires a connection from the pool and immediatly releases it back to the pool.
 * The connection acquired from the pool is wrapped, see class {@link ConnectionProxy}.
 * @author FWiers
 *
 */
public class DataSourceFactory implements ObjectFactory {

	protected Logger log = LoggerFactory.getLogger(this.getClass());

	/** 
	 * See {@link javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable)}
	 * @return a proxy instance of {@link DataSourceProxy}. 
	 */
	@Override
	public Object getObjectInstance(Object obj, Name name, Context nameCtx,
			Hashtable<?, ?> environment) throws Exception {

		if (log.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append('\n').append("Object : ").append(obj);
			sb.append('\n').append("Name   : ").append(name);
			sb.append('\n').append("CtxName: ").append(nameCtx);
			sb.append('\n').append("Envir  : ").append(environment);
			log.trace("Creating instance: " + sb.toString());
		}
		
		Reference ref = (Reference) obj;
		Enumeration<RefAddr> addrs = ref.getAll();
		Properties props = new Properties();
		String builderName = null;
		while (addrs.hasMoreElements()) {
			RefAddr addr = addrs.nextElement();
			if (addr.getType().equals("poolBuilder")){
				builderName = addr.getContent().toString();
			}
			props.put(addr.getType(), addr.getContent());
		}
		
		if (builderName == null) {
			builderName = HsqlPoolBuilder.class.getName();
		}
		
		if (log.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder();
			for (Object key : props.keySet()) {
				if ("password".equals(key.toString())) {
					sb.append('\n').append(key.toString()).append("\t: ");
					for (int i = 0; i < props.getProperty(key.toString()).length(); i++) sb.append('*');
				} else {
					sb.append('\n').append(key.toString()).append("\t: ").append(props.getProperty(key.toString()));
				}
			}
			log.debug("Configuring SqlPool with following parameters: " + sb.toString());
		}
		
		IPoolBuilder builder = null;
		try {
			builder = (IPoolBuilder) Class.forName(builderName).newInstance();
		} catch (Throwable t) {
			log.error("Could not find the database pool builder [" + builderName +"]", t);
			return null;
		}
		
		SqlProxyPool pool = null;
		try {
			pool = (SqlProxyPool) builder.buildPool(props);
		} catch (Exception e) {
			log.error("Pool builder [" + builder.getClass().getName() +"] failed to create a database pool.", e);
			return null;
		}
		
		log.info("Created database pool {}", pool.toString());
		DataSourceProxy ds = new DataSourceProxy(pool);
		return ds.getInstance(DataSource.class); 
	}
	
}
