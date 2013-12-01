package nl.fw.yapool.sql;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import nl.fw.yapool.TestUtil;

import org.junit.Test;

public class TestLoadQueries {

	@Test
	public void loadDummyOK() {
		
		InputStream in = null;
		try {
			Map<String, String> m = SimpleQueryBuilder.loadQueries(new InputStreamReader(in = TestUtil.getResourceStream("dummy-queries-ok.sql")));
			assertEquals(3, m.size());
			assertEquals("select dummy2", m.get("DUMMY2"));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		} finally {
			TestUtil.close(in);
		}
	}
	
	@Test
	public void loadDummyBad() {
		
		InputStream in = null;
		try {
			SimpleQueryBuilder.loadQueries(new InputStreamReader(in = TestUtil.getResourceStream("dummy-queries-bad.sql")));
			fail();
		} catch (Exception e) {
			//e.printStackTrace();
			assertTrue(e.toString().contains("Closing query-key found without query"));
		} finally {
			TestUtil.close(in);
		}
	}

	@Test
	public void loadDummyShort() {
		
		InputStream in = null;
		try {
			Map<String, String> m = SimpleQueryBuilder.loadQueries(new InputStreamReader(in = TestUtil.getResourceStream("dummy-queries-short.sql")));
			assertEquals(4, m.size());
			assertEquals("select dummy1", m.get("DUMMY1"));
			assertEquals("select dummy2", m.get("DUMMY2"));
			assertEquals("select dummy3", m.get("DUMMY3"));
			assertEquals("select dummy6", m.get("DUMMY6"));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		} finally {
			TestUtil.close(in);
		}
	}

}
