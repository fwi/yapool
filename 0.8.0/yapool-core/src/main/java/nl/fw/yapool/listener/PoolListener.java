package nl.fw.yapool.listener;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.fw.yapool.IPoolListener;
import nl.fw.yapool.PoolEvent;

/**
 * Base class for a pool listener that listens to all events.
 * @author Fred
 *
 */
public class PoolListener implements IPoolListener {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	/** Default true which means this pool listener will receive all pool events. */
	protected boolean wantAllEventActions = true;
	
	/** Collections of pool-events to listen for. */
	protected Set<String> wantEventActions;
	
	/**
	 * Registers this pool listener for the given list of pool event actions.
	 * If needed, this method should be called from the contructor.
	 * Sets {@link #wantAllEventActions} to false.
	 * <br>Always use this method when possible: it can reduce pool-event (object creation) overhead considerably.
	 * @param events
	 */
	protected void addWantEvent(String... events) {
		
		if (events != null && events.length > 0) {
			wantAllEventActions = false;
			if (wantEventActions == null) {
				wantEventActions = new HashSet<String>();
			}
			for (String s : events) {
				wantEventActions.add(s);
			}
		}
	}
	
	public boolean wantsEventAction(String eventAction) {
		return (wantAllEventActions ? true : wantEventActions.contains(eventAction));
	}
	
	public boolean wantAllEventActions() {
		return wantAllEventActions;
	}
	
	public Collection<String> getWantEventActions() {
		return wantEventActions;
	}

	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		log.trace(poolEvent.getSource() + " " + poolEvent);
	}


}
