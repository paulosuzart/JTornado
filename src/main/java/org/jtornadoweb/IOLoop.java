package org.jtornadoweb;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

	private static final int MIN_SELECT_TIMEOUT = 1;

	/**
	 * Default taimout before try to get selected keys from selector.
	 */
	public static int SELECT_TIMEOUT = 3000;

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
		private final EventHandler handler;
		private final int opts;
		private final SelectableChannel channel;

		public EventHandlerTask(EventHandler handler, int opts,
				SelectableChannel channel) {
			this.handler = handler;
			this.opts = opts;
			this.channel = channel;

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			try {
				handler.handleEvents(opts, channel);
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
		 * Handles the Selectable Channel. opts means the current ready
		 * operations.
		 * 
		 * @param opts
		 * @param channel
		 * @throws Exception
		 */
		public void handleEvents(int opts, SelectableChannel channel)
				throws Exception;

	}

	public static abstract class EventHandlerAdapter implements EventHandler {

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jtornadoweb.IOLoop.EventHandler#handleEvents(int,
		 * java.nio.channels.SelectableChannel)
		 */
		@Override
		public void handleEvents(int opts, SelectableChannel channel)
				throws Exception {
			switch (opts) {
			case SelectionKey.OP_READ:
				try {
					onRead(channel);
				} catch (Exception e) {
					onReadError(e, channel);
				}
				break;
			case SelectionKey.OP_WRITE:
				try {
					onWrite(channel);
				} catch (Exception e) {
					onWriteError(e, channel);
				}
				break;
			case SelectionKey.OP_ACCEPT:
				try {
					_onAccept(channel);
				} catch (Exception e) {
					onAcceptError(e, channel);
				}
				break;
			default:
				throw new UnsupportedOperationException();
			}

		}

		private void _onAccept(SelectableChannel channel) throws Exception {
			while (true) {
				SocketChannel clientChannel = ((ServerSocketChannel) channel)
						.accept();
				if (clientChannel == null)
					break;

				onAccept(clientChannel);

			}

		}

		protected void onAcceptError(Exception e, SelectableChannel channel) {
			onWriteError(e, channel);
		}

		protected void onAccept(SelectableChannel channel) throws Exception {
			throw new UnsupportedOperationException();
		}

		/**
		 * Default behavior is close the channel - if open - and print the
		 * stack.
		 * 
		 * @param e
		 * @param channel
		 */
		protected void onWriteError(Exception e, SelectableChannel channel) {
			try {
				if (channel.isOpen())
					channel.close();
				e.printStackTrace();
			} catch (Exception _e) {
				_e.printStackTrace();
			}
		}

		protected void onReadError(Exception e, SelectableChannel channel) {
			onWriteError(e, channel);
		}

		protected void onWrite(SelectableChannel channel) throws Exception {
			throw new UnsupportedOperationException();
		}

		protected void onRead(SelectableChannel channel) throws Exception {
			throw new UnsupportedOperationException();
		}

	}

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

		if (this.pool instanceof ThreadPoolExecutor) {
			ThreadPoolExecutor tpool = ((ThreadPoolExecutor) this.pool);
			tpool.setKeepAliveTime(10, TimeUnit.SECONDS);
		}

	}

	/**
	 * Starts the event loop. The default timeout for wait SO response is 2 ms.
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {
		int pollTimeout = SELECT_TIMEOUT;
		List<EventHandlerTask> pendingTasks = new LinkedList<IOLoop.EventHandlerTask>();
		while (true) {

			Iterator<EventHandlerTask> iterTask = pendingTasks.iterator();
			while (iterTask.hasNext()) {
				pool.execute(iterTask.next());
				iterTask.remove();
			}
			Thread.yield();

			if (!selector.selectedKeys().isEmpty() || !pool.isTerminated())
				pollTimeout = MIN_SELECT_TIMEOUT;

			Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

			while (iter.hasNext()) {
				SelectionKey key = iter.next();
				iter.remove();

				if (key.isValid() && !key.isAcceptable()) {
					EventHandlerTask task = new EventHandlerTask(
							(EventHandler) key.attachment(), key.readyOps(),
							key.channel());
					this.removeHandler(key);
					// Adds the task for the next iteration.
					pendingTasks.add(task);

				} else if (key.isValid()) {
					/* Events other than accept is handled in another thread. */
					((EventHandler) key.attachment()).handleEvents(
							key.readyOps(), key.channel());
				}

			}

			selector.select(pollTimeout);
			pollTimeout = IOLoop.SELECT_TIMEOUT;

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

		// key.attach(null);
		// this.handlers.remove(key);
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
		if (channel.isRegistered()) {
			if (channel.keyFor(selector).isValid()) {
				selector.wakeup();
				channel.register(selector, opts, eventHandler);
			}

		} else {
			channel.register(selector, opts, eventHandler);
		}

	}
}
