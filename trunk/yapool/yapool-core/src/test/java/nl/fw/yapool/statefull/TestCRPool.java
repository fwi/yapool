package nl.fw.yapool.statefull;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates how to capture contents when closing a pool
 * and restoring these contents when creating a pool.
 * Not so much a hack compared to {@link TestSaveRestore}. 
 * @author fred
 *
 */
public class TestCRPool {

	protected Logger log = LoggerFactory.getLogger(getClass());

	@Test
	public void captureRestore() {
		
		log.debug("Starting restore/capture test.");
		CRBoundPool<Long> p = new CRBoundPool<Long>();
		p.setFactory(new CRLongFactory());
		List<Long> restore = Arrays.asList(new Long[] { 42L, 43L, 44L });
		p.getFactory().setRestore(restore);
		int maxSize = restore.size() * 2 + 1;
		p.setMaxSize(maxSize);
		p.open();
		assertTrue(p.getFactory().allRestored());
		
		List<Long> pooled = new LinkedList<Long>();
		for (int i = 0; i < restore.size(); i++) {
			Long l = p.acquire();
			assertTrue(restore.contains(l));
			pooled.add(l);
		}
		for (int i = restore.size(); i < maxSize; i++) {
			Long l = p.acquire();
			pooled.add(l);
		}
		for (int i = 0; i < maxSize; i++) {
			p.release(pooled.get(i));
		}
		
		Long l = p.acquire();
		p.close();
		List<Long> captured = p.getFactory().getCaptured();
		// Resources that are leased while pool is closed are evicted from pool but not destroyed and thus not captured.
		assertEquals(maxSize - 1, captured.size());
		p.release(l);
		// Resources that are released to the pool after the pool is closed are destroyed and thus, in this case, captured.
		assertEquals(maxSize, captured.size());
		log.debug("Capture test completed: " + captured);
	}

}
