package com.the_qa_company.q_endpoint.utils;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * a utility class to create an iterator from the value returned by another Thread
 *
 * @param <T> the iterator type
 * @author Antoine Willerval
 */
public class PipedIterator<T> implements Iterator<T> {
	/**
	 * RuntimeException generated by the PipedIterator
	 * @author Antoine Willerval
	 */
	public static class PipedIteratorException extends RuntimeException {
		public PipedIteratorException(String message, Throwable t) {
			super(message, t);
		}
	}

	/**
	 * Callback for the {@link #createOfCallback(com.the_qa_company.q_endpoint.utils.PipedIterator.PipeCallBack)} method
	 *
	 * @param <T> the iterator type
	 * @author Antoine Willerval
	 */
	@FunctionalInterface
	public interface PipeCallBack<T> {
		/**
		 * method called from the new thread to generate the new data, at the end of the callback, the pipe is closed
		 * with or without exception
		 *
		 * @param pipe the pipe to fill
		 * @throws Exception any exception returned by the generator
		 */
		void createPipe(PipedIterator<T> pipe) throws Exception;
	}

	/**
	 * create a piped iterator from a callback runner, the call to the callback should be made in the callbackRunner
	 *
	 * @param callbackRunner the callback runner
	 * @param <T>            type of the iterator
	 * @return the iterator
	 */
	public static <T> PipedIterator<T> createOfCallback(PipeCallBack<T> callbackRunner) {
		PipedIterator<T> pipe = new PipedIterator<>(10000);

		Thread thread = new Thread(() -> {
			try {
				callbackRunner.createPipe(pipe);
				pipe.closePipe();
			} catch (Throwable e) {
				pipe.closePipe(e);
			}
		}, "PipeIterator");
		thread.start();

		return pipe;
	}

	private class PipedNode {
		T t;

		public PipedNode(T t) {
			this.t = t;
		}

		boolean end() {
			return false;
		}
	}

	private class PipedNodeEnd extends PipedNode {
		private final Throwable exception;

		public PipedNodeEnd(Throwable exception) {
			super(null);
			this.exception = exception;
		}

		boolean end() {
			if (exception != null) {
				throw new PipedIteratorException("Crash while creating pipe", exception);
			}
			return true;
		}
	}

	private final ArrayBlockingQueue<PipedNode> queue;
	private PipedNode next;

	public PipedIterator(int bufferSize) {
		queue = new ArrayBlockingQueue<>(bufferSize);
	}

	/**
	 * add an element to the piped iterator
	 *
	 * @param element the element to pipe
	 * @throws PipedIteratorException in case of Interruption
	 */
	public void addElement(T element) throws PipedIteratorException {
		try {
			queue.put(new PipedNode(element));
		} catch (InterruptedException e) {
			throw new PipedIteratorException("Can't add element", e);
		}
	}

	/**
	 * close the pipe after the last added element
	 *
	 * @throws PipedIteratorException in case of Interruption
	 */
	public void closePipe() throws PipedIteratorException {
		closePipe(null);
	}

	/**
	 * close the pipe after the last added element
	 *
	 * @param e exception to call at the {@link #hasNext()} next call
	 * @throws PipedIteratorException in case of Interruption
	 */
	public void closePipe(Throwable e) throws PipedIteratorException {
		try {
			queue.put(new PipedNodeEnd(e));
		} catch (InterruptedException ie) {
			throw new PipedIteratorException("Can't close pipe", ie);
		}
	}

	/**
	 * @return if the iterator has a next element, or block until it found it or the pipe is closed
	 * @throws PipedIteratorException if the pipe got an error while closing or in case of Interruption
	 */
	@Override
	public boolean hasNext() throws PipedIteratorException {
		if (next == null) {
			try {
				next = queue.take();
			} catch (InterruptedException e) {
				throw new PipedIteratorException("Can't get next element", e);
			}
		}
		return !next.end();
	}

	/**
	 * @return the next iterator element
	 * @throws PipedIteratorException if the pipe got an error while closing or in case of Interruption
	 */
	@Override
	public T next() throws PipedIteratorException {
		if (!hasNext())
			return null;
		T next = this.next.t;
		this.next = null;
		return next;
	}
}
