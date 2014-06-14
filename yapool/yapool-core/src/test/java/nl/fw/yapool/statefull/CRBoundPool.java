package nl.fw.yapool.statefull;

import nl.fw.yapool.BoundPool;
import nl.fw.yapool.IPoolFactory;

/**
 * A {@link BoundPool} that must have a {@link CaptureRestoreFactory}
 * and automatically restores resources on {@link #open()} 
 * and captures resources on {@link #close()}. 
 * <p>
 * Technical note: ideally this would be done using PoolEvents 
 * but there are no "going to open pool" and "going to close pool" events.
 * Only events "pool has been opened" ({@link nl.fw.yapool.PoolEvent#OPENED}) 
 * and "pool has been closed" ({@link nl.fw.yapool.PoolEvent#CLOSED}) events are available.
 * When the "going to ... " events are available, these base-classes can move to the main source tree.
 * @author fred
 *
 * @param <T>
 */
public class CRBoundPool<T> extends BoundPool<T> {

	@Override
	public void setFactory(IPoolFactory<T> factory) {
		throw new IllegalArgumentException("CRPool can only be used with CaptureRestoreFactory.");
	}

	public void setFactory(CaptureRestoreFactory<T> factory) {
		super.setFactory(factory);
	}

	@Override
	public CaptureRestoreFactory<T> getFactory() {
		return (CaptureRestoreFactory<T>) super.getFactory();
	}
	
	/**
	 * Opens the pool and restores resources from the factory if they are available. 
	 */
	@Override
	public void open() {
		
		if (getFactory().getRestore() != null) {
			open(getFactory().getRestore().size());
		} else {
			super.open();
		}
		if (!getFactory().allRestored()) {
			if (log.isDebugEnabled()) {
				log.debug(getPoolName() + ": " + (getFactory().getRestore().size() - getFactory().getRestoreIndex()) 
					+ " of " +	getFactory().getRestore().size() + " resources were not restored.");
			}
		}
	}
	
	/**
	 * Closes the pool and captures any resources in the pool.
	 * Note that resources that are leased while the pool is being closed are not captured. 
	 */
	@Override 
	public void close() {
		
		getFactory().startCapture();
		int psize = getSize();
		super.close();
		int captured = getFactory().getCaptured().size();
		if (psize == captured) {
			if (log.isDebugEnabled()) {
				log.debug(getPoolName() + ": captured " + getFactory().getCaptured().size() + " resources.");
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug(getPoolName() + ": did not capture " + (psize - captured) + " out of " + psize + " resource(s) in pool.");
			}
		}
	}

}
