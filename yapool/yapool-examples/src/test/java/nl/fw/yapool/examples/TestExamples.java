package nl.fw.yapool.examples;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestExamples {

	@Test 
	public void runExample() {
		
		try {
			new ExampleEvents().demonstrate(); 
			new ExampleConfig().demonstrate(); 
			new ExampleHsql().demonstrate(); 
			new ExampleValidateOnCheckIn().demonstrate();
			new ExampleQueryCache().demonstrate(); 
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
}
