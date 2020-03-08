package com.github.fwi.yapool;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic class for handling pool-events.
 * @author Fred
 */
public class PoolEvents implements IPoolEvents {

	protected Logger log = LoggerFactory.getLogger(getClass());

	/** Thread safe list containing the listeners. */
	private List<IPoolListener> poolListeners = new CopyOnWriteArrayList<IPoolListener>();
	private AtomicReference<Set<String>> wantEventActions = new AtomicReference<Set<String>>();
	private volatile boolean wantAllEventActions = true;
	
	public boolean wantEventAction(String poolEvent) {
		return (haveListeners() ? (wantAllEventActions ? true : wantEventActions.get().contains(poolEvent)) : false);
	}

	public boolean haveListeners() {
		return poolListeners.size() > 0;
	}

	@Override
	public void addPoolListener(IPoolListener listener) {
		
		if (!poolListeners.contains(listener)) {
			poolListeners.add(listener);
			updateWantEvents();
		}
	}

	@Override
	public void removePoolListener(IPoolListener listener) {
		poolListeners.remove(listener);
		updateWantEvents();
	}
	
	@Override
	public void clearListeners() {
		poolListeners.clear();
		updateWantEvents();
	}

	@Override
	public void firePoolEvent(PoolEvent poolEvent) {
		
		for (IPoolListener l : poolListeners) {
			if (l.wantsEventAction(poolEvent.getAction())) {
				try {
					l.onPoolEvent(poolEvent);
				} catch (Exception e) {
					log.error("Pool listener " + l + " could not handle pool-event " + poolEvent, e);
				}
			}
		}
	}
	
	protected void updateWantEvents() {
		
		Set<String> events = new HashSet<String>();
		boolean all = false;
		for (IPoolListener l : poolListeners) {
			if (l.wantAllEventActions()) {
				all = true;
				break;
			}
			events.addAll(l.getWantEventActions());
		}
		if (all) {
			wantAllEventActions = true;
			events.clear();
			wantEventActions.set(events);
		} else {
			wantEventActions.set(events);
			// custom events might be added, so we are never sure when all Pool Events are in the events-set.
			// therefor set the wantAllEvents always to false
			wantAllEventActions = false;
		}
	}
	
}
