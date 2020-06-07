package com.github.fwi.yapool.statefull;

import static org.junit.Assert.*;

import java.util.List;

import com.github.fwi.yapool.BoundPool;
import com.github.fwi.yapool.LongFactory;

import org.junit.Test;

/**
 * A bit of a hack but demonstrates how to capture contents when closing a pool
 * and restoring these contents when creating a pool.
 * @author fred
 *
 */
public class TestSaveRestore {

	@Test
	public void saveRestore() {
		
		BoundPool<Long> p = new BoundPool<Long>();
		p.setFactory(new LongFactory());
		p.open(3);
		SaveFactory sf = new SaveFactory();
		p.setFactory(sf);
		p.close();
		List<Long> toRestore = sf.getSaved();
		assertEquals(3, toRestore.size());
		//System.out.println(toRestore);
		
		p = new BoundPool<Long>();
		RestoreFactory rf = new RestoreFactory(toRestore);
		p.setFactory(rf);
		p.open(toRestore.size());
		p.setMaxSize(10);
		assertTrue(rf.allRestored());
		
		LongFactory lf = new LongFactory();
		lf.setStartValue(3L);
		p.setFactory(lf);
		Long[] acquired = new Long[7];
		for (int i = 0; i < 7; i++) {
			acquired[i] = p.acquire();
		}
		for (int i = 0; i < 7; i++) {
			p.release(acquired[i]);
		}

		sf = new SaveFactory();
		p.setFactory(sf);
		p.close();
		toRestore = sf.getSaved();
		assertEquals(7, toRestore.size());
		//System.out.println(toRestore);
	}
}
