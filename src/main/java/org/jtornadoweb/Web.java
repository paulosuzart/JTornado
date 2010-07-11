package org.jtornadoweb;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.jtornadoweb.HttpServer.HttpRequest;

import quicktime.streaming.Stream;

public class Web {

	public static class RequestHandler {

		public HttpRequest request;

		private String writeBuffer;

		private boolean finished = false;

		protected void get() {
			// TODO throw HTTPError 500
		}

		protected void post() {
			// TODO throw HTTPError 500
		}

		public void execute() {
			if ("get".equals(request.method.toLowerCase())) {
				get();
			} else if ("post".equals(request.method.toLowerCase())) {
				post();
			}

			finish();
		}

		protected void write(String buffer) {
			writeBuffer = buffer;
		}

		private void finish() {
			flush();
			request.finish();
		}

		private void flush() {
			String headers = generateHeaders(false);
			request.write((headers + writeBuffer).getBytes());

		}

		private String generateHeaders(boolean includeFooters) {
			// FIXED 200 OK
			String fixedResponse = "\r\nHello\r\n";
			return "HTTP/1.1 200 OK\r\nContent-Length: "
					+ writeBuffer.getBytes().length + "\r\n\r\n";

		}
	}

	public static interface RequestCallback {
		public void execute(HttpRequest request);
	}

	/**
	 * new Application(){{ add("/", RequestHandler.class); add("/main",
	 * MainHandler.class); }};
	 * 
	 * @author rafaelfelini
	 */
	public static class Application implements RequestCallback {

		private Map<Pattern, Class<? extends RequestHandler>> handlers;

		public Application() {
			this.handlers = new HashMap<Pattern, Class<? extends RequestHandler>>();
		}

		public void add(String uri, Class<? extends RequestHandler> handler) {
			this.handlers.put(Pattern.compile(uri), handler);
		}

		@Override
		/**
		 * __call__ in Application of web.py
		 */
		public void execute(HttpRequest request) {
			String uri = request.uri;

			RequestHandler handler = null;

			for (Map.Entry<Pattern, Class<? extends RequestHandler>> entry : handlers
					.entrySet()) {
				if (entry.getKey().matcher(uri).matches()) {
					try {
						// TODO think something better later
						handler = entry.getValue().newInstance();
						handler.request = request;
					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
				}
			}

			handler.execute();
		}

	}

}
