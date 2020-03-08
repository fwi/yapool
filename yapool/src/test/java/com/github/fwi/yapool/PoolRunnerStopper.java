package com.github.fwi.yapool;

public class PoolRunnerStopper {

	private volatile boolean stop;

	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}
	
}
