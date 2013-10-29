package nl.fw.yapool;

import java.util.concurrent.Executor;

/**
 * An executor-service that creates and starts a new thread for each new task.
 * @author Fred
 *
 */
public class ThreadExecutor implements Executor {
	
	protected boolean asDaemon;

	/**
	 * Starts all runnables in a new daemon-thread.
	 */
	public ThreadExecutor() {
		this(true);
	}

	/**
	 * Starts all runnables in a new (daemon) thread.
	 */
	public ThreadExecutor(boolean asDaemeon) {
		this.asDaemon = asDaemeon;
	}

	@Override
	public void execute(Runnable command) {
		
		Thread t = new Thread(command);
		t.setDaemon(asDaemon);
		t.start();
	}

}
