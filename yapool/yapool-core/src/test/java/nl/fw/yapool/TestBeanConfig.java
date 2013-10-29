package nl.fw.yapool;

import java.util.Map;
import java.util.Properties;

import nl.fw.yapool.BeanClone;
import nl.fw.yapool.BeanConfig;
import nl.fw.yapool.PrunedPool;
import nl.fw.yapool.sql.SqlFactory;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import static nl.fw.pool.PoolEvent.*;
//import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

public class TestBeanConfig {

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	@Test
	public void primitiveProps() {
		
		PrunedPool<Object> pool = new PrunedPool<Object>();
		assertFalse(pool.isFair());
		assertEquals(0, pool.getMaxAcquireTimeMs());
		assertFalse(pool.getMaxLeaseTimeMs() == 1000);
		Properties props = new Properties();
		props.setProperty("fair", "true");
		props.setProperty("maxacquiretimems", "10");
		props.setProperty("maxLeaseTimeS", "1");
		BeanConfig.configure(pool, props);
		assertTrue(pool.isFair());
		assertEquals(10, pool.getMaxAcquireTimeMs());
		assertEquals(1000, pool.getMaxLeaseTimeMs());
		
		Properties extracted = new Properties();
		BeanConfig.extract(pool, extracted);
		assertEquals("true", extracted.getProperty("fair"));
		assertEquals("1000", extracted.getProperty("maxLeaseTimeMs"));
		assertEquals("0", extracted.getProperty("minSize"));
		assertEquals("4", extracted.getProperty("maxSize"));
		assertFalse(extracted.containsKey("events"));
	}

	@Test
	public void propsInProps() {
		
		SqlFactory factory = new SqlFactory();
		Properties props = new Properties();
		String jdbcDriver = factory.getJdbcDriverClass();
		props.setProperty("jdbcUrl", "jdbc:test:url");
		props.setProperty("connection.customProp", "customPropValue");
		props.setProperty("connection.AnotherProp", "AnotherPropValue");
		props.setProperty("connection.driver.special.class", "driver.special.class.value");
		BeanConfig.configure(factory, props);
		assertEquals("jdbc:test:url", factory.getJdbcUrl());
		assertEquals(jdbcDriver, factory.getJdbcDriverClass());
		assertEquals(3, factory.getConnectionProps().size());
		assertTrue(factory.getConnectionProps().containsKey("AnotherProp"));
		assertTrue(factory.getConnectionProps().contains("AnotherPropValue"));

		Properties extracted = new Properties();
		BeanConfig.extract(factory, extracted);
		assertEquals("false", extracted.getProperty("autoCommit"));
		assertEquals("AnotherPropValue", extracted.getProperty("connection.AnotherProp"));
		assertEquals("driver.special.class.value", extracted.getProperty("connection.driver.special.class"));
	}
	
	@Test
	public void filterProps() {
		
		Properties p = new Properties();
		p.setProperty("base.maxacquiretimems", "10");
		p.setProperty("small.maxsize", "2");
		p.setProperty("large.maxsize", "12");
		p.setProperty("large.connection.user", "LargeUser");
		p.setProperty("large.connection.password", "");
		
		Map<String, Object> base = BeanConfig.filterPrefix(p, "base.");
		Map<String, Object> small = BeanClone.clone(base);
		small.putAll(BeanConfig.filterPrefix(p, "small."));
		Map<String, Object> large = BeanClone.clone(base);
		large.putAll(BeanConfig.filterPrefix(p, "large."));
		
		PrunedPool<Object> poolSmall = new PrunedPool<Object>();
		BeanConfig.configure(poolSmall, small);
		PrunedPool<Object> poolLarge = new PrunedPool<Object>();
		BeanConfig.configure(poolLarge, large);
		SqlFactory factory = new SqlFactory();
		BeanConfig.configure(factory, large);
		
		assertEquals(10, poolSmall.getMaxAcquireTimeMs());
		assertEquals(10, poolLarge.getMaxAcquireTimeMs());
		assertEquals(2, poolSmall.getMaxSize());
		assertEquals(12, poolLarge.getMaxSize());
		assertEquals("LargeUser", factory.getConnectionProps().get("user"));
		assertEquals("", factory.getConnectionProps().get("password"));
	}
	
	@Test
	public void prioritizeProps() {

		Properties p = new Properties();
		p.setProperty("maxsize.test", "10");
		p.setProperty("maxsize.prod", "20");
		BeanConfig.prioritizeSuffix(p, ".test");
		assertEquals("10", p.getProperty("maxsize"));
		BeanConfig.prioritizeSuffix(p, ".prod");
		assertEquals("20", p.getProperty("maxsize"));
	}
}
