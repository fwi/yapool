package nl.fw.yapool.sql.datasource;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;

import javax.naming.Reference;
import javax.sql.DataSource;

import nl.fw.yapool.PoolEvent;
import nl.fw.yapool.listener.PoolEventQueue;
import nl.fw.yapool.sql.datasource.DataSourceFactory;
import nl.fw.yapool.sql.datasource.DataSourceProxy;

import org.junit.Test;

public class TestDSFactory {
	
	@Test
	public void proxyFactory() {
		
		DataSourceFactory factory = new DataSourceFactory();
		DataSourceProxy dsp = null; 
		try {
			DataSource ds = (DataSource) factory.getObjectInstance(new Reference("DS"), null, null, null);
			assertEquals("Check default instance function from proxy-util", 0, ds.getLoginTimeout());
			
			dsp = (DataSourceProxy)Proxy.getInvocationHandler(ds);
			
			PoolEventQueue events;
			dsp.getPool().getEvents().addPoolListener(events = new PoolEventQueue());
			events.register = true;
			// get a pooled connection and release it via the connection-proxy
			ds.getConnection().close();
			assertTrue(events.getCount(PoolEvent.ACQUIRED) > 0);
			assertTrue(events.getCount(PoolEvent.RELEASING) > 0);
			assertFalse(events.getCount(PoolEvent.DESTROYING) > 0);
			
			// check that proxies are re-used
			Connection c  = ds.getConnection();
			assertFalse(c.isClosed());
			c.close();
			assertTrue(c.isClosed());
			assertTrue(dsp.getProxyCount() == 1);
			
			dsp.close();
			assertTrue(dsp.getPool().isClosed());
			assertTrue(dsp.getProxyCount() == 0);
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

}
