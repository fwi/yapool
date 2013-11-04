package nl.fw.yapool.sql;

/**
 * A {@link SqlPool} that uses a {@link MySqlFactory}.
 * @author Fred
 *
 */
public class MySqlPool extends SqlPool {

	/**
	 * Sets the {@link MySqlFactory}.
	 */
	public MySqlPool() {
		super();
		setFactory(new MySqlFactory());
		setPoolName(getFactory().getPoolName());
	}
	
}
