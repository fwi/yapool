package nl.fw.yapool.sql;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

public class TestInvalid {

	/*
	 * Test database crash scenario, see
	 * http://stackoverflow.com/questions/21166584/aborting-cleaning-up-an-invalid-database-connecton-from-pool-in-tomcat/21171514#21171514
	 */
	@Test
	public void mimicDbCrash() {
		
		final int minSize = 3;
		
		SqlPool pool = new SqlPool();
		//pool.setPruneIntervalMs(30000);
		MimicCrashFactory mcf = new MimicCrashFactory();
		pool.setFactory(mcf);
		pool.setMinSize(minSize);
		pool.open();
		assertEquals(minSize, pool.getCreatedCount());
		assertEquals(minSize, pool.getSize());
		pool.release(pool.acquire());
		for (Map.Entry<Connection, Boolean> e : mcf.connValid.entrySet()) {
			e.setValue(false);
		}
		pool.release(pool.acquire());
		assertEquals(minSize, pool.getInvalidCount());
		assertEquals(minSize + 1, pool.getCreatedCount());
		assertEquals(1, pool.getSize());
		pool.ensureMinSize();
		assertEquals(minSize, pool.getSize());
		pool.close();
	}
	
	static class MimicCrashFactory extends SqlFactory {
		
		public Map<Connection, Boolean> connValid = new ConcurrentHashMap<Connection, Boolean>();
		
		@Override
		public Connection create() {
			
			Connection c = super.create();
			if (c != null) {
				connValid.put(c, true);
			}
			return c;
		}
		
		@Override
		public boolean isValid(Connection c) {
			
			Boolean valid = connValid.get(c);
			if (valid == null) {
				throw new RuntimeException("Expected connection in connection map.");
			}
			if (valid) {
				valid = super.isValid(c);
			}
			return valid;
		}
		
		@Override
		protected void destroy(Connection c, boolean rollback) {
			
			if (c != null) {
				connValid.remove(c);
			}
			super.destroy(c, rollback);
		}

	}
}
