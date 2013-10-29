package nl.fw.yapool.mix;

import java.util.concurrent.Executor;

import nl.fw.yapool.sql.SqlPool;

public class SelectProducer extends QueryProducer {

	public SelectProducer(Executor taskExecutor, int amount, SqlPool dbp) {
		super(taskExecutor, amount, dbp);
	}
	
	@Override
	public Runnable produceQuery(SqlPool dbp) {
		return new Select(dbp);
	}

}
