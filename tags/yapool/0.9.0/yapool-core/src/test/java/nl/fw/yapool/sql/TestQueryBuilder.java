package nl.fw.yapool.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import nl.fw.yapool.sql.NamedParameterStatement;
import nl.fw.yapool.sql.SimpleQueryBuilder;

public class TestQueryBuilder extends SimpleQueryBuilder {
	
	@Override
	public NamedParameterStatement createNamedQuery(Connection c, String sqlId)  throws SQLException {
		
		NamedParameterStatement nps = null;
		if (sqlId == SqlUtil.insertRecord) {
			nps = new NamedParameterStatement(c, sqlId, Statement.RETURN_GENERATED_KEYS);
		} else {
			nps  = super.createNamedQuery(c, sqlId);
		}
		return nps;
	}

}
