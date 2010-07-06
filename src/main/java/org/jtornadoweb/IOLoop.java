package org.jtornadoweb;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.HashMap;
import java.util.Map;

public class IOLoop {

	// private final ExecutorService pool;

	private Map<SelectionKey, EventHandler> handlers;

	public static interface EventHandler {
		public void handleEvents(SelectableChannel serverChannel,
				SelectionKey key) throws Exception;
	}

	private final Selector selector;

	public IOLoop() throws Exception {
		this.selector = Selector.open();
		this.handlers = new HashMap<SelectionKey, EventHandler>();
		// this.pool = Executors.newFixedThreadPool(3);
	}

	public void start() throws Exception {

		while (true) {

			int timeout = 10000;
			
			if (handlers != null && !handlers.isEmpty())
				timeout = 3;
			
				selector.select(3);

			for (final SelectionKey key : selector.selectedKeys()) {
				selector.selectedKeys().remove(key);

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
