package org.jtornadoweb;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

/**
 * 
 * IOLoop handles acceptance events from selection in the same thread (supposed
 * to be the thread main sin its invoked from there). All other events returned
 * from a poll will be executed in a thread available in the poll.
 * 
 * TODO Another test is to dispatch execution to the pool, only code handled by
 * the user app. All the JTornado code may run in a single thread. Need to test.
 * 
 * @author paulosuzart@gmail.com
 * 
 */
public class IOLoop {

	/**
	 * Default taimout before try to get selected keys from selector.
	 */
	public static int SELECT_TIMEOUT = 2;

	/**
	 * Receives a SelectionKey and executes its attachment. The attachment
	 * should be an EventHandler. no check is performed. A class cast exception
	 * may be raised. <br>
	 * Usage:
	 * <p>
	 * 
	 * <pre>
	 * EventHandlerTask task = new EventHandlerTask(key);
	 * pool.execute(task);
	 * </pre>
	 * 
	 * </p>
	 * TODO abstract for a general task executor. With generics we can create
	 * task executor of any kind, not only EventHandlers.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	private class EventHandlerTask implements Runnable {

		/**
		 * Key to be executed running run();
		 */
		private final SelectionKey key;

		public EventHandlerTask(SelectionKey key) {
			this.key = key;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			try {
				((EventHandler) key.attachment()).handleEvents(key);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Receives a Selection Key previously registered for a given selector. At
	 * the moment of receive, the key will be already canceled if it is not a
	 * OP_ACCEPT.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static interface EventHandler {
		/**
		 * Handles the Selectable Channel. TODO Consider passing only the
		 * channel. Provide a means to inform the user wich event it is.
		 * 
		 * @param key
		 * @throws Exception
		 */
		public void handleEvents(SelectionKey key) throws Exception;
	}

	/**
	 * Tracks all handlers added to selector.
	 */
	private HashSet<SelectionKey> handlers;

	/**
	 * Default system selector. <b>EPoll is highly recommended.</b>
	 */
	private final Selector selector;

	/**
	 * A pool received from the client of IOLoop.
	 */
	private final ExecutorService pool;

	public IOLoop(ExecutorService pool) throws Exception {
		this.pool = pool;
		this.selector = Selector.open();
		this.handlers = new HashSet<SelectionKey>();

	}

	/**
	 * Starts the event loop. The default timeout for wait SO response is 2 ms.
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {

		while (true) {

			selector.select(IOLoop.SELECT_TIMEOUT);

			Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

			while (iter.hasNext()) {
				SelectionKey key = iter.next();
				iter.remove();
				if (!key.isAcceptable()) {
					this.removeHandler(key);
					EventHandlerTask task = new EventHandlerTask(key);
					pool.execute(task);
				} else {
					// events other than accept is handled in another thred.
					((EventHandler) key.attachment()).handleEvents(key);
				}

			}

		}
	}

	/**
	 * Every handler will be invoked once. If the handler has the interest to
	 * keep informed about changes on its channels, thei mus register again see
	 * {@link IOLoop#addHandler(AbstractSelectableChannel, EventHandler, int)}
	 * 
	 * @param key
	 */
	public void removeHandler(SelectionKey key) {
		key.cancel();
		//this.handlers.remove(key);
	}

	/**
	 * Registers the given channel to the current selector.
	 * <p>
	 * <b>Note:</b> The channel is put in non block mode.
	 * </p>
	 * 
	 * @param channel
	 * @param eventHandler
	 * @param opts
	 * @throws Exception
	 */
	public void addHandler(AbstractSelectableChannel channel,
			EventHandler eventHandler, int opts) throws Exception {
		channel.configureBlocking(false);
		channel.register(selector, opts, eventHandler);
		// handlers.put(channpel.keyFor(selector), eventHandler);
		handlers.add(channel.keyFor(selector));
	}
}
