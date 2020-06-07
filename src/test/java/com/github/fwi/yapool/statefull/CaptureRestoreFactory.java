package com.github.fwi.yapool.statefull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fwi.yapool.IPoolFactory;

/**
 * A pool factory capable of restoring and capturing resources when a pool is opened and closed.
 * Must be used together with {@link CRBoundPool} for example. 
 * @author fred
 */
public abstract class CaptureRestoreFactory<T> implements IPoolFactory<T> {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private AtomicInteger restoreIndex = new AtomicInteger();
	private List<T> restore;
	/** 
	 * Size of restore-list. Since the list can be nullified, 
	 * size must be kept separately to prevent null-pointer exceptions.
	 */
	private int maxRestore; 
	private List<T> captured;
	private volatile boolean capture;
	
	/**
	 * List of resources to restore.
	 * This list may NOT change in size once set via this method.
	 * Once all resources are restored, the reference to the list is removed.
	 */
	public void setRestore(List<T> restore) {
		this.restore = restore;
		maxRestore = (restore == null ? 0 : restore.size());
	}
	
	/**
	 * Unmodifiable list of resources to restore.
	 * Returns null when all resources have been restored.
	 */
	public List<T> getRestore() {
		return (restore == null ? null : Collections.unmodifiableList(restore));
	}

	public int getRestoreIndex() {
		return restoreIndex.get();
	}
	
	public boolean allRestored() {
		return (restore == null || getRestoreIndex() >= maxRestore);
	}
	
	public void startCapture() {

		captured = Collections.synchronizedList(new LinkedList<T>());
		capture = true;
	}
	
	public List<T> getCaptured() {
		return captured;
	}
	
	@Override
	public T create() {
		
		T resource = null;
		if (allRestored()) {
			resource =  createNew();
		} else {
			int i = restoreIndex.getAndIncrement();
			if (i < maxRestore) {
				resource = restore.get(i);
				if (log.isTraceEnabled()) {
					log.trace(getClass().getSimpleName() + " restoring resource " + i);
				}
			} else {
				resource = createNew();
			}
			if (i == maxRestore - 1) {
				restore = null;
				if (log.isDebugEnabled()) {
					log.debug(getClass().getSimpleName() + " all " + maxRestore + " resources restored to pool.");
				}
			}
		}
		return resource;
	}
	
	public abstract T createNew();

	@Override
	public abstract boolean isValid(T resource);

	@Override
	public void destroy(T resource) {
		
		if (capture) {
			captured.add(resource);
		} else {
			destroyNoCapture(resource);
		}
	}

	public abstract void destroyNoCapture(T resource);

}
