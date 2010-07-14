package org.jtornadoweb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
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
	
	public static class HttpCode {
		
		public static final Map<Integer, String> codes = new HashMap<Integer, String>();
		
		static {
		    codes.put(100, "Continue");
		    codes.put(101, "Switching Protocols");

		    codes.put(200, "OK");
		    codes.put(201, "Created");
		    codes.put(202, "Accepted");
		    codes.put(203, "Non-Authoritative Information");
		    codes.put(204, "No Content");
		    codes.put(205, "Reset Content");
		    codes.put(206, "Partial Content");

		    codes.put(300, "Multiple Choices");
		    codes.put(301, "Moved Permanently");
		    codes.put(302, "Found");
		    codes.put(303, "See Other");
		    codes.put(304, "Not Modified");
		    codes.put(305, "Use Proxy");
		    codes.put(306, "(Unused);");
		    codes.put(307, "Temporary Redirect");

		    codes.put(400, "Bad Request");
		    codes.put(401, "Unauthorized");
		    codes.put(402, "Payment Required");
		    codes.put(403, "Forbidden");
		    codes.put(404, "Not Found");
		    codes.put(405, "Method Not Allowed");
		    codes.put(406, "Not Acceptable");
		    codes.put(407, "Proxy Authentication Required");
		    codes.put(408, "Request Timeout");
		    codes.put(409, "Conflict");
		    codes.put(410, "Gone");
		    codes.put(411, "Length Required");
		    codes.put(412, "Precondition Failed");
		    codes.put(413, "Request Entity Too Large");
		    codes.put(414, "Request-URI Too Long");
		    codes.put(415, "Unsupported Media Type");
		    codes.put(416, "Requested Range Not Satisfiable");
		    codes.put(417, "Expectation Failed");

		    codes.put(500, "Internal Server Error");
		    codes.put(501, "Not Implemented");
		    codes.put(502, "Bad Gateway");
		    codes.put(503, "Service Unavailable");
		    codes.put(504, "Gateway Timeout");
		    codes.put(505, "HTTP Version Not Supported");

		}
		
		public static String get(int code) {
			return codes.get(code);
		}
		
	}
	
	/**
	 * An exception that will turn into an HTTP error response.
	 * 
	 * @author rafael.felini@gmail.com
	 */
	public static class HttpError {
		
		private final int statusCode;
		private final String logMessage;
		private final String[] args;
		private final static Formatter formatter = new Formatter();
		
		public HttpError (int statusCode, String logMessage, String... args) {
			this.statusCode = statusCode;
			this.logMessage = logMessage;
			this.args = args;
		}
		
		@Override
		public String toString() {
			
			String message = formatter.format("HTTP %d: %s ", statusCode, HttpCode.get(statusCode)).toString();
			
			if (logMessage != null && !logMessage.isEmpty()) {
				return message + "(" + logMessage + " " + Arrays.toString(args) + ")";
			} else {
				return message;
			}

		}
		
	}

}
