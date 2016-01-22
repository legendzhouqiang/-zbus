package org.zbus.mq;

import java.io.IOException;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.zbus.mq.disk.DiskQueuePool;

public class DiskQueue extends AbstractQueue<byte[]> implements BlockingQueue<byte[]> {
	private final org.zbus.mq.disk.DiskQueue support;
	private final String name;
	/** Main lock guarding all access */
	final ReentrantLock lock;
	/** Condition for waiting takes */
	private final Condition notEmpty;
	/** Condition for waiting puts */
	private final Condition notFull;

	public DiskQueue(String name) {
		this.name = name;
		this.support = DiskQueuePool.getDiskQueue(name);
		lock = new ReentrantLock();
		notEmpty = lock.newCondition();
		notFull = lock.newCondition();
	}

	public static void init(String deployPath) {
		DiskQueuePool.init(deployPath);
	}

	public static void release() throws IOException {
		DiskQueuePool.release();
	}

	@Override
	public boolean offer(byte[] e) {
		final ReentrantLock lock = this.lock;
		lock.lock();
		try {
			boolean res = support.offer(e);
			notEmpty.signal();
			return res;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public byte[] poll() {
		final ReentrantLock lock = this.lock;
		lock.lock();
		try {
			byte[] res = support.poll();
			notFull.signal();
			return res;
		} finally {
			lock.unlock();
		}
	}

	public byte[] take() throws InterruptedException {
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try {
			while (support.size() == 0){
				support.sync();
				notEmpty.await();
			}
			return poll();
		} finally {
			lock.unlock();
		}
	} 
	
	@Override
	public int size() {
		final ReentrantLock lock = this.lock;
		lock.lock();
		try {
			return support.size();
		} finally {
			lock.unlock();
		}
	}

	public String getName() {
		return name;
	}

	@Override
	public void put(byte[] e) throws InterruptedException {
		offer(e);
	}

	@Override
	public boolean offer(byte[] e, long timeout, TimeUnit unit) throws InterruptedException {
		offer(e);
		return true;
	}

	@Override
	public byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
		return poll();
	}

	@Override
	public int remainingCapacity() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int drainTo(Collection<? super byte[]> c) { 
		throw new UnsupportedOperationException();
	}

	@Override
	public int drainTo(Collection<? super byte[]> c, int maxElements) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public byte[] peek() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<byte[]> iterator() {
		throw new UnsupportedOperationException();
	} 
 
}