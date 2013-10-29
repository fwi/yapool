package nl.fw.yapool;

import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A LIFO pool that can be used to re-use resources.
 * The pool only keeps track of resources that are available for leasing (a.k.a. idle resources). 
 * @author Fred
 */
public class Pool<T> implements IPool<T> {

	//private static final long serialVersionUID = -6944749838095746859L;

	protected Logger log = LoggerFactory.getLogger(getClass());

	private String poolName = getClass().getSimpleName() + "[" + hashCode() + "]";

	/** 
	 * Manages permits for leasing resources. 
	 * Determines if permits are given in a fair manner or fast manner
	 * (see {@link #isFair()}. 
	 */ 
	protected Semaphore idle = new Semaphore(0, false);
	
	/** A LIFO queue containing resources ready to be leased. */
	protected LinkedBlockingDeque<T> idleQueue = new LinkedBlockingDeque<T>();
	
	private AtomicReference<IPoolEvents> events = new  AtomicReference<IPoolEvents>();
	private AtomicReference<IPoolFactory<T>> factory = new AtomicReference<IPoolFactory<T>>();
	private AtomicLong maxAcquireTimeMs = new AtomicLong();
	private volatile boolean closed;
	protected AtomicLong createdCount = new AtomicLong();
	
	public Pool() {
		super();
		events.set(new PoolEvents());
	}
	
	protected T create() {
		
		T t = factory.get().create();
		if (t == null) {
			throwFactoryCreateFailed();
		}
		createdCount.incrementAndGet();
		fireEvent(PoolEvent.CREATED, t);
		return t;
	}

	@Override
	public T acquire() {
		return acquire(getMaxAcquireTimeMs());
	}

	public T acquire(long acquireTimeOutMs) {
		
		if (isClosed()) {
			throw new IllegalStateException("Pool is closed.");
		}
		fireEvent(PoolEvent.ACQUIRING);
		T t = null;
		try {
			t = acquireIdle(acquireTimeOutMs);
			if (t == null) {
				t = create();
			}
		} finally {
			fireEvent(PoolEvent.ACQUIRED, t);
		}
		return t;
	}
	
	/**
	 * 
	 * @param acquireTimeOutMs If the time is less than or equal to zero, the method will not wait at all.
	 * @return the idle resource from the pool, or null if none could be acquired
	 */
	protected T acquireIdle(long acquireTimeOutMs) {
		
		T t = null;
		try {
			if (idle.tryAcquire(acquireTimeOutMs, TimeUnit.MILLISECONDS)) {
				t = idleQueue.poll();
			}
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
		//log.warn("Time-out: " + acquireTimeOutMs + ", acquired: " + (t != null));
		return t;
	}
	
	protected void addIdle(T t) {
		
		idleQueue.addFirst(t);
		idle.release();
	}

	@Override
	public T release(T t) {
		
		fireEvent(PoolEvent.RELEASING, t);
		addIdle(t);
		return t;
	}
	
	public boolean isClosed() { 
		return closed;
	}

	public void close() {
		
		closed = true;
		fireEvent(PoolEvent.CLOSED);
	}

	protected void fireEvent(String action) {
		fireEvent(action, null);
	}

	protected void fireEvent(String action, T t) {
		if (events.get().wantEventAction(action)) {
			fireEvent(new PoolEvent(this, action, t));
		}
	}

	protected void fireEvent(PoolEvent pe) {
		events.get().firePoolEvent(pe);
	}
	
	/**
	 * Throws a {@link NoSuchElementException} with message 
	 * "poolName factory did not create a new resource."
	 */
	public void throwFactoryCreateFailed() {
		throw new NoSuchElementException(getPoolName() + " pool factory did not create a new resource.");
	}

	
	/* *** bean methods *** */

	/**
	 * Short description of the pool.
	 * Default set to class-name[hashCode].
	 */
	public String getPoolName() {
		return poolName;
	}

	public void setPoolName(String name) {
		
		if (name != null && !name.isEmpty()) {
			this.poolName = name;
		}
	}

	@Override
	public void setFactory(IPoolFactory<T> f) {
		
		if (f == null) {
			throw new IllegalArgumentException("Pool factory cannot be null.");
		}
		factory.set(f);
	}
	
	@Override
	public IPoolFactory<T> getFactory() {
		return factory.get();
	}

	@Override
	public IPoolEvents getEvents() {
		return events.get();
	}

	@Override
	public void setEvents(IPoolEvents events) {
		
		if (events == null) {
			throw new IllegalArgumentException("Pool events cannot be null.");
		}
		this.events.set(events);
	}


	public long getMaxAcquireTimeMs() {
		return maxAcquireTimeMs.get();
	}

	public void setMaxAcquireTimeMs(long maxAcquireTimeMs) {
		if (maxAcquireTimeMs >= 0L) {
			this.maxAcquireTimeMs.set(maxAcquireTimeMs);
		}
	}
	
	/**
	 * If true, the thread waiting the longest for a resource will get the first available resource.
	 * If false, whichever thread is fastest to give the first avialable resource to will get the resource.
	 */
	public boolean isFair() {
		return idle.isFair();
	}

	public void setFair(boolean fair) {
		
		if (fair != isFair()) {
			idle = new Semaphore(0, fair);
		}
	}
	
	public long getCreatedCount() {
		return createdCount.get();
	}

	public int getIdleSize() { 
		return idle.availablePermits(); 
	}
	
	/** The number of threads waiting to acquire a resource from the pool. */
	public int getWaitingSize() {
		return idle.getQueueLength();
	}

	/**
	 * Returns {@link #getPoolName()}
	 */
	@Override
	public String toString() {
		return getPoolName();
	}

}
