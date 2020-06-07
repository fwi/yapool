package com.github.fwi.yapool.listener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import com.github.fwi.yapool.PoolEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolEventQueue extends PoolListener {

	protected Logger log = LoggerFactory.getLogger(getClass());

	public LinkedBlockingDeque<PoolEvent> queue = new LinkedBlockingDeque<PoolEvent>(); 
	public boolean register;
	public boolean logEvent;
	
	@Override
	public void onPoolEvent(PoolEvent poolEvent) {
		
		if (logEvent) {
			log.debug(poolEvent.getSource() + " " + poolEvent);
		}
		if (register) {
			queue.addLast(poolEvent);
		}
	}
	
	public int getCount(String action) {
		
		int count = 0;
		for (PoolEvent e : queue) {
			if (e.getAction().equals(action)) count++;
		}
		return count;
	}
	
	public String countByAction() {
		
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for (PoolEvent e : queue) {
			Integer amount = counts.get(e.getAction());
			counts.put(e.getAction(), (amount == null ? 1 : amount + 1));
		}
		StringBuilder sb = new StringBuilder("Pool event queue action count:");
		if (counts.keySet().size() == 0) {
			sb.append(" none.");
		} else {
			for (String action : counts.keySet()) {
				sb.append("\n").append(action).append(": ").append(counts.get(action));
			}
		}
		return sb.toString();
	}
	
	public String toString() {
		
		StringBuilder sb = new StringBuilder("Pool event queue:\n");
		for(PoolEvent p : queue) {
			sb.append(p).append("\n");
		}
		return sb.toString();
	}
	
}
