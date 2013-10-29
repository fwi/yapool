package nl.fw.yapool.sql.hibernate;

import nl.fw.yapool.sql.SqlPool;

/**
 * Registry interface for Hibernate Connection Providers.
 * See also {@link HibernatePools}.
 * @author FWiers
 *
 */
public interface IHibernatePools {
	
	void addPool(String poolId, SqlPool pool);
	SqlPool get(String poolId);
	SqlPool removePool(SqlPool pool);
	SqlPool removePool(String poolId);

}
