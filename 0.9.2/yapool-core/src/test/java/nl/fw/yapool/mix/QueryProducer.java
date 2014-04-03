package nl.fw.yapool.mix;

import java.util.concurrent.Executor;

import nl.fw.yapool.sql.SqlPool;

public class QueryProducer implements Runnable {

	public static volatile boolean stop;
	
	protected Executor taskExecutor;
	private int amount;
	private SqlPool dbp;

	public QueryProducer(Executor taskExecutor, int amount, SqlPool dbp) {
		this.taskExecutor = taskExecutor;
		this.amount = amount;
		this.dbp = dbp;
	}
	
	@Override
	public void run() {
		
		for (int i = 0; i < amount; i++) {
			taskExecutor.execute(produceQuery(dbp));
			if (stop) break;
		}
	}
	
	public Runnable produceQuery(SqlPool dbp) {
		return null;
	}

}
