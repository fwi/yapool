package nl.fw.yapool.sql.hibernate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nl.fw.yapool.sql.SqlPool;

/**
 * Default registry for {@link SqlPool}s used by Hibernate.
 * See also {@link HibernateConnectionProvider}.
 * @author FWiers
 *
 */
public class HibernatePools implements IHibernatePools {

	protected static Map<String, SqlPool> pools = new  ConcurrentHashMap<String, SqlPool>();

	/** Uses factory-jdbcUrl as poolId. */
	public void addPool(SqlPool pool) {
		addPool(pool.getFactory().getJdbcUrl(), pool);
	}

	@Override
	public SqlPool removePool(SqlPool pool) {
		return pools.remove(pool);
	}

	@Override
	public void addPool(String poolId, SqlPool pool) {
		pools.put(poolId, pool);
	}

	@Override
	public SqlPool get(String poolId) {
		return pools.get(poolId);
	}

	@Override
	public SqlPool removePool(String poolId) {
		return pools.remove(poolId);
	}

}
