package nl.fw.yapool.mix;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import nl.fw.yapool.listener.MaxUsageTracker;
import nl.fw.yapool.listener.PoolPerformance;
import nl.fw.yapool.sql.SqlFactory;
import nl.fw.yapool.sql.SqlPool;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMix {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	public int tasks = 200; 
	
	public int inserts = 3;
	public int selects = 1;
	
	@Test
	public void mix() {

		ExecutorService producer = Executors.newCachedThreadPool();
		
		ExecutorService tp = Executors.newCachedThreadPool();
		//ThreadPoolWithQ tp = new ThreadPoolWithQ("DbTasks");
		
		SqlPool dbp = new SqlPool();
		dbp.setPoolName("DbPool");
		dbp.setMaxSize(5);
		SqlFactory dbpFact;
		dbp.setFactory(dbpFact = new SqlFactory());
		dbpFact.setPoolName(dbp.getPoolName());
		PoolPerformance dbPerf;
		dbp.getEvents().addPoolListener(dbPerf = new PoolPerformance(dbp));
		MaxUsageTracker maxUsage;
		dbp.getEvents().addPoolListener(maxUsage = new MaxUsageTracker(dbp));
		dbp.open();
		
		SetupDb setup;
		tp.execute(setup = new SetupDb(dbp));
		setup.await();
		
		for (int i = 0; i < inserts; i++) {
			producer.execute(new InsertProducer(tp, tasks, dbp));
		}
		for (int i = 0; i < selects; i++) {
			producer.execute(new SelectProducer(tp, tasks, dbp));
		}

		log.debug("Waiting for running producer tasks");
		producer.shutdown();
		try { 
			producer.awaitTermination(300000, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			e.printStackTrace();
		}
		QueryProducer.stop = true;
		log.debug("Waiting for running tasks");
		tp.shutdown();
		try { 
			tp.awaitTermination(300000, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.debug("Closing db-pool");
		dbp.close();
		
		log.debug(dbPerf.toString());
		log.debug(maxUsage.toString());
		log.debug("Inserts: " + Insert.recordsInserted.get() 
				+ ", selects: " + Select.recordsSelected.get() 
				+ ", queries: " + DbTask.queryCount.get() 
				+ ", failed: " + DbTask.failedQueryCount.get());
	}

}
