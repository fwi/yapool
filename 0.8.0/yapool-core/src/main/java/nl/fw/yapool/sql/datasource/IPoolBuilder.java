package nl.fw.yapool.sql.datasource;

import java.util.Properties;

import nl.fw.yapool.sql.SqlPool;

public interface IPoolBuilder {

	SqlPool buildPool(Properties props);
}
