package nl.fw.yapool.sql;

import nl.fw.yapool.PoolPruner;
import nl.fw.yapool.TestUtil;

import org.junit.Test;

/**
 * Use this test to observe CPU usage and alike with various scheduled prune tasks.
 * @author FWiers
 *
 */
public class TestIdle {

	//@Test
	public void testIdle() {
		
		SqlPool pool = new SqlPool();
		//pool.setPruneIntervalMs(30000);
		pool.setFactory(new SqlFactory());
		pool.open();
		//PoolPruner.getInstance().remove(pool);
		TestUtil.sleep(200000);
		
		
	}

}
