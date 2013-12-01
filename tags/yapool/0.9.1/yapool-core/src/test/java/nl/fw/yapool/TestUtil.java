package nl.fw.yapool;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {
	
	private static final Logger log = LoggerFactory.getLogger(TestUtil.class);
	
	public static void sleep(long timeMs) { 
		try { Thread.sleep(timeMs); } catch (Exception ignored) {
			log.debug("Sleep of " + timeMs + " interrupted.");
		}
	}
	
	public static BasicPool createBasicPool() { return createBasicPool(null); }
	
	public static BasicPool createBasicPool(IPoolListener pl) {
		
		BasicPool p = new BasicPool();
		if (pl != null) {
			p.getEvents().addPoolListener(pl);
		}
		return p;
	}

	public static Bound createPool() { return createPool(null); }
	
	public static Bound createPool(IPoolListener pl) {
		
		Bound p = new Bound();
		if (pl != null) {
			p.getEvents().addPoolListener(pl);
		}
		return p;
	}
	
	public static Pruned createPrunedPool(IPoolListener pl) {
		
		Pruned p = new Pruned();
		if (pl != null) {
			p.getEvents().addPoolListener(pl);
		}
		return p;
	}
	
	public static Pruned createSleepPool(IPoolListener pl) {
		
		Pruned p = new Pruned();
		p.setFactory(new SleepFactory());
		if (pl != null) {
			p.getEvents().addPoolListener(pl);
		}
		return p;
	}
	
	public static SharedTestPruner runPruner(PrunedPool<?> p) {
		
		SharedTestPruner stp = new SharedTestPruner();
		stp.add(p);
		await(((SharedTestPruner.PruneTestTask)p.getPruneTask()).latch);
		return stp;
	}
	
	public static Thread start(Runnable r, CountDownLatch latch) {
		
		WaitStart w = new WaitStart(r, latch);
		Thread t = new Thread(w);
		t.start();
		return t;
	}
	
	public static void await(CountDownLatch latch) {

		long tstart = System.currentTimeMillis();
		try {
			latch.await();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		log.debug("Await time: " + (System.currentTimeMillis() - tstart));
	}
	
	public static void close(Closeable c) {
	
		try {
			if (c != null) c.close();
		} catch (Exception ignored) {}
	}

	public static InputStream getResourceStream(String rname) {
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(rname);
	}
	
}
