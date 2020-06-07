package com.github.fwi.yapool.statefull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.fwi.yapool.IPoolFactory;

public class RestoreFactory implements IPoolFactory<Long> {
	
	private final AtomicInteger restored = new AtomicInteger();
	private final List<Long> toRestore;
	
	public RestoreFactory(List<Long> toRestore) {
		this.toRestore = toRestore;
	}

	@Override
	public Long create() {
		
		int i = restored.getAndIncrement();
		if (i >= toRestore.size()) {
			throw new IllegalStateException("Cannot restore resource " + i + " from resource list of size " + toRestore.size()); 
		}
		return toRestore.get(i);
	}

	@Override
	public boolean isValid(Long resource) {
		return (resource != null);
	}

	@Override
	public void destroy(Long resource) {}
	
	public boolean allRestored() {
		return (restored.get() == toRestore.size());
	}

}
