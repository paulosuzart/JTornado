package org.jtornadoweb;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
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
			SocketChannel clientChannel = ((ServerSocketChannel) channel)
					.accept();
			clientChannel.configureBlocking(false);
			onAccept(clientChannel);

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

	public static class AddHandler {
		EventHandler handler;
		SelectableChannel chann;
		int ops;

		public AddHandler(EventHandler handler, SelectableChannel chann,
				int opts) {
			this.handler = handler;
			this.chann = chann;
			ops = opts;
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

	private final ConcurrentLinkedQueue<AddHandler> toAdd = new ConcurrentLinkedQueue<AddHandler>();

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
		while (true) {

			//handlers registered from the previous interation or
			//any point in time are added before this interation.
			registerAddHandlers();

			Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

			while (iter.hasNext()) {
				SelectionKey key = iter.next();
				iter.remove();

				EventHandler attachment = (EventHandler) key.attachment();
				int readyOps = key.readyOps();
				SelectableChannel channel = key.channel();
				boolean acceptable = key.isAcceptable();
				removeHandler(key);

				if (!acceptable) {
					EventHandlerTask task = new EventHandlerTask(attachment,
							readyOps, channel);
					pool.execute(task);

				} else {
					//ACCEPT will be reattatcher to the ServerSocket channel
					attachment.handleEvents(readyOps, channel);
				}

			}

			selector.select();

		}
	}

	/**
	 * @throws IOException
	 * @throws ClosedChannelException
	 */
	private void registerAddHandlers() throws IOException,
			ClosedChannelException {
		Iterator<AddHandler> addIter = toAdd.iterator();

		while (addIter.hasNext()) {
			AddHandler item = addIter.next();
			addIter.remove();

			if (item.chann.isOpen()) {
				item.chann.configureBlocking(false);
				item.chann.register(selector, item.ops, item.handler);
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
	public void addHandler(SelectableChannel channel,
			EventHandler eventHandler, int opts) throws Exception {
		toAdd.offer(new AddHandler(eventHandler, channel, opts));
		selector.wakeup();

		// channel.configureBlocking(false);
		// if (channel.isRegistered()) {
		// if (channel.keyFor(selector).isValid()) {
		// selector.wakeup();
		// channel.register(selector, opts, eventHandler);
		// }
		//
		// } else {
		// channel.register(selector, opts, eventHandler);
		// }

	}
}
