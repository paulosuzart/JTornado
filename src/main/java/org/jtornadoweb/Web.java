package org.jtornadoweb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jtornadoweb.HttpServer.HttpRequest;

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

		/**
		 * Returns unicode value from the givem argument name. If there is more
		 * than one value to the name, the last one is returner.
		 * 
		 * @param name
		 * @param defaultValue
		 * @param trim
		 * @return
		 */
		protected String getArgument(String name, String defaultValue,
				boolean trim) {

			List<String> values = getArguments(name, trim);
			if (values == null || values.isEmpty()) {
				if (defaultValue == null)
					throw new RuntimeException("404, Missing Argument " + name);
				return defaultValue;
			}
			// last item
			return values.get(values.size() - 1);
		}

		/**
		 * Returns a list of arguments with the given name.
		 * 
		 * @param name
		 * @param trim
		 * @return list of unicode values
		 */
		protected List<String> getArguments(String name, boolean trim) {

			List<String> _validValues = new ArrayList<String>();
			if (!request.arguments.containsKey(name))
				return _validValues;

			for (String s : request.arguments.get(name))
				_validValues.add(unicode(s.replaceAll(
						"[\u0000-\u0008\u000E-\u001F]", " ")));
			return _validValues;

		}

		private String unicode(String s) {
			return s; // TODO implement
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
			return "HTTP/1.1 200 OK\r\nContent-Length: "
					+ writeBuffer.getBytes().length + "\r\n\r\n";

		}
	}

	public static interface RequestCallback {
		public void execute(HttpRequest request);
	}

	/**
	 * Application is responsible for mapping requests to appropriate
	 * RequestHandler. Methods in this class may be used as a simple dsl: new
	 * Application().add("/", MyRequestHandler.class).add(...);
	 * 
	 * @author rafaelfelini
	 */
	public static class Application implements RequestCallback {

		private Map<Pattern, Class<? extends RequestHandler>> handlers;

		public Application() {
			this.handlers = new HashMap<Pattern, Class<? extends RequestHandler>>();
		}

		/**
		 * Maps the given path pattern to a request handler.
		 * 
		 * @param uri
		 * @param handler
		 * @return
		 */
		public Application add(String uri,
				Class<? extends RequestHandler> handler) {
			this.handlers.put(Pattern.compile(uri), handler);
			return this;
		}

		@Override
		public void execute(HttpRequest request) {
			String path = request.path;

			RequestHandler handler = null;

			for (Map.Entry<Pattern, Class<? extends RequestHandler>> entry : handlers
					.entrySet()) {
				if (entry.getKey().matcher(path).matches()) {
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

			if (handler == null) {
				// handle not found.
			}
			handler.execute();
		}

	}

}
