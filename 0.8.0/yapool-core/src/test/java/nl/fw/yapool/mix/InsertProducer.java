package nl.fw.yapool.mix;

import java.util.concurrent.Executor;

import nl.fw.yapool.sql.SqlPool;

public class InsertProducer extends QueryProducer {

	public InsertProducer(Executor taskExecutor, int amount, SqlPool dbp) {
		super(taskExecutor, amount, dbp);
	}
	
	@Override
	public Runnable produceQuery(SqlPool dbp) {
		return new Insert(dbp);
	}

}
