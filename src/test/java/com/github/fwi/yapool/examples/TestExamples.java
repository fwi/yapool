package com.github.fwi.yapool.examples;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestExamples {

	@Test 
	public void runExample() {
		
		try {
			new ExampleSimplePool().demonstrate(); 
			new ExamplePrunedPool().demonstrate(); 
			new ExampleEvents().demonstrate(); 
			new ExampleValidateOnCheckIn().demonstrate(); 
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
}
