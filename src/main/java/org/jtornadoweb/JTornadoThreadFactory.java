package org.jtornadoweb;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A custom thread factory. It will put every JTornado Thread in a
 * given group an set the uncaightException for every new thread.
 * @author paulo
 *
 */
class JTornadoThreadFactory implements ThreadFactory {

	private int count;

	private static final ThreadGroup TG = new ThreadGroup(
			"JTornado Thread Group");

	private static final UncaughtExceptionHandler exHandler = new UncaughtExceptionHandler() {
		protected final Logger logger = Logger
				.getLogger("org.jtornadoweb.HttpServer.ThreadFactory.exHandler");

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			logger.log(Level.SEVERE, "Exception on Thread " + t.getName(), e);
		}
	};

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(TG, r);
		thread.setName("JTornado Task-" + count++);
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.setUncaughtExceptionHandler(exHandler);
		return thread;
	}

}