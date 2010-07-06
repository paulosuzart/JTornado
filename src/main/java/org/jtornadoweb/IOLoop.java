package org.jtornadoweb;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class IOLoop {

	private class EventHandlerTask implements Runnable {

		private final EventHandler handler;
		private final SelectionKey key;

		public EventHandlerTask(EventHandler ev, SelectionKey key) {
			this.handler = ev;
			this.key = key;
		}

		@Override
		public void run() {
			try {
				handler.handleEvents(key.channel(), key);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// key.cancel();
			}
		}

	}

	private Map<SelectionKey, EventHandler> handlers;

	public static interface EventHandler {
		public void handleEvents(SelectableChannel serverChannel,
				SelectionKey key) throws Exception;
	}

	private final Selector selector;
	private final ExecutorService pool;

	public IOLoop(ExecutorService pool) throws Exception {
		this.pool = pool;
		this.selector = Selector.open();
		this.handlers = new HashMap<SelectionKey, EventHandler>();

	}

	public void start() throws Exception {

		while (true) {

			selector.select(1);

			for (final SelectionKey key : selector.selectedKeys()) {
				selector.selectedKeys().remove(key);
				// EventHandlerTask task = new EventHandlerTask(
				// (EventHandler) key.attachment(), key);
				// pool.execute(task);
				((EventHandler) key.attachment()).handleEvents(key.channel(),
						key);
			}

		}
	}

	public void removeHandler(SelectionKey key) {
		this.handlers.remove(key);
	}

	public void addHandler(AbstractSelectableChannel channel,
			EventHandler eventHandler, int opts) throws Exception {
		channel.configureBlocking(false);
		channel.register(selector, opts, eventHandler);
		handlers.put(channel.keyFor(selector), eventHandler);

	}
}
