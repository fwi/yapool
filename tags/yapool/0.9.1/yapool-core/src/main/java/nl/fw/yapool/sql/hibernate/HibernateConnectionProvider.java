package nl.fw.yapool.sql.hibernate;

import java.util.Properties;

import nl.fw.yapool.sql.SqlPool;

/**
 * Wrapper when using Hibernate, see projects yapool-hib3 and yapool-hib4 for usage.
 * @author frederikw
 *
 */
public class HibernateConnectionProvider  {

	private static IHibernatePools providedPools = new HibernatePools();

	/**
	 * By default, set to an instance of {@link HibernatePools}.
	 */
	public static IHibernatePools getProvidedPools() {
		return providedPools;
	}

	public static void setProvidedPools(IHibernatePools providedPools) {
		HibernateConnectionProvider.providedPools = providedPools;
	}

	protected Properties props;
	protected SqlPool pool;

}
