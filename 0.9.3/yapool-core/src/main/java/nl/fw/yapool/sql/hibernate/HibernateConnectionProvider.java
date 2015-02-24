package nl.fw.yapool.sql.hibernate;

import java.util.Map;
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

	public static final String HIBERNATE_KEY_PREFIX = "hibernate.";
	public static final String JPA_KEY_PREFIX = "javax.persistence.";

	/**
	 * The properties provided by Hibernate include all JVM/System properties.
	 * This method can be used to filter out just the Hibernate and JPA properties
	 * (which start with {@link #HIBERNATE_KEY_PREFIX} or {@link #JPA_KEY_PREFIX}).
	 * @param allProps The properties provided by Hibernate/JPA.
	 * @param filterPassword must be set to true if properies are logged. 
	 * Changes the value for keys containing "password" to "secret". 
	 * @return The Hibernate/JPA properties.
	 */
	public static Properties getHibernateJpaProps(Map<?, ?> allProps, boolean filterPassword) {
		
		Properties p = new Properties();
		for (Object k : allProps.keySet()) {
			if (k == null) continue;
			String ks = k.toString();
			if (ks.startsWith(HIBERNATE_KEY_PREFIX) || ks.startsWith(JPA_KEY_PREFIX)) {
				Object v = allProps.get(k);
				if (v == null) continue;
				String vs = v.toString();
				if (vs != null) {
					if (filterPassword && ks.toUpperCase().contains("PASSWORD")) {
						p.setProperty(ks, "secret");
					} else {
						p.setProperty(ks, vs);
					}
				}
			}
		}
		return p;
	}

}
