package com.github.fwi.yapool.statefull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.github.fwi.yapool.IPoolFactory;

public class SaveFactory implements IPoolFactory<Long> {

	private List<Long> saved = Collections.synchronizedList(new LinkedList<Long>());
	
	@Override
	public Long create() {
		throw new IllegalStateException("Factory can only be used to capture objects about to be destroyed.");
	}

	@Override
	public boolean isValid(Long resource) {
		return (resource != null);
	}

	@Override
	public void destroy(Long resource) {
		saved.add(resource);
	}
	
	public List<Long> getSaved() {
		return saved;
	}

}
