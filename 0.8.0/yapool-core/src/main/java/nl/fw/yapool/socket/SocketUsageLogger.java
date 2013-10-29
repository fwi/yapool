package nl.fw.yapool.socket;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Reports the acitivity of a {@link SocketAcceptor} as a log-statement that looks like:
 * <br><code>SleepManyReporter - accepted: 22, processing: 5, processed: 15, workers: 5</code>
 * <br>
 * Usage is only reported when values have changed (i.e. if there is no activity, nothing is logged).
 * The amount of workers is only logged when the amount of workers has changed.
 * @author FWiers
 *
 */
public class SocketUsageLogger implements Runnable {

	protected Logger log = LoggerFactory.getLogger(getClass());

	protected long lastAccepted;
	protected long lastProcessing;
	protected long lastProcessed;
	
	private String reportId = "";
	private long reportIntervalMs = 10000L;
	private SocketAcceptor acceptor;
	private String threadName;

	private volatile boolean stop;
	private Semaphore sleepLock = new Semaphore(0);

	public void setSocketAcceptor(SocketAcceptor acceptor) {
		this.acceptor = acceptor;
	}
	
	public SocketAcceptor getSocketAcceptor() {
		return acceptor;
	}
	
	public long getReportIntervalMs() {
		return reportIntervalMs;
	}

	/** Default 10 seconds. */
	public void setReportInterval(long reportIntervalMs) {
		this.reportIntervalMs = reportIntervalMs;
	}

	public String getReportId() {
		return reportId;
	}
	
	public String getThreadName() {
		return threadName;
	}
	/** If not empty, sets the name thread running this acceptor to the given name. */ 
	public void setThreadName(String threadName) {
		this.threadName = threadName;
	}


	/** 
	 * An optional socket-reporter ID shown in the report-log statement.
	 * Useful in case there are multiple socket-reporters running. 
	 */
	public void setReportId(String reportId) {
		if (reportId != null) {
			this.reportId = reportId;
		}
	}

	public void start() {
		
		if (acceptor == null) {
			log.error("No socket acceptor set, cannot log usage.");
		} else {
			acceptor.getExecutor().execute(this);
		}
	}
	
	public void stop() {
		stop = true;
		sleepLock.release();
	}

	/**
	 * Reports in a loop, but only when there are changes in the reported numbers.
	 */
	@Override
	public void run() {

		String orgThreadName = null;
		if (threadName != null) {
			orgThreadName = Thread.currentThread().getName();
			Thread.currentThread().setName(threadName);
		}
		try {
			while (!stop) {
				boolean report = (acceptor.getAcceptedCount() != lastAccepted);
				if (!report) report = (acceptor.getTasksFinished() != lastProcessed);
				if (!report) report = (acceptor.getOpenTasks() != lastProcessing);
				if (report) log.info(getReport());
				try {
					if (sleepLock.tryAcquire(reportIntervalMs, TimeUnit.MILLISECONDS)) {
						stop = true;
					}
				} catch (Exception e) {
					log.debug(reportId + " socket reporter stopping after interruption: " + e);
					stop = true;
				}
			}
		} finally {
			log.info(getReport());
			log.info(reportId + " Socket connections reporter stopping.");
			if (orgThreadName != null) {
				Thread.currentThread().setName(orgThreadName);
			}
		}
	}

	/** Creates a report for the log and updates the "lastCount" values. */
	protected String getReport() {

		StringBuilder sb = new StringBuilder(128);

		if (reportId != null && !reportId.isEmpty()) sb.append(reportId).append(" - ");

		long count = acceptor.getAcceptedCount();
		sb.append("accepted: ").append(count - lastAccepted).append(", ");
		lastAccepted = count;

		count = acceptor.getOpenTasks();
		sb.append("processing: ").append(count).append(", ");
		lastProcessing = count;

		count = acceptor.getTasksFinished();
		sb.append("processed: ").append(count - lastProcessed);
		lastProcessed = count;

		return sb.toString();
	}
}
