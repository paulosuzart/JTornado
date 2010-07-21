package org.jtornadoweb;

import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.jtornadoweb.HttpServer.HttpRequest;

/**
 * The JTornado web framework.
 * 
 * @author rafael.felini
 */
public class Web {

	/**
	 * If you want to define a handler for one of the SUPPORTED_METHODS subclass
	 * this class.
	 * 
	 * 
	 * @author rafael.felini
	 */
	public static class RequestHandler {

		/**
		 * Override the class variable SUPPORTED_METHODS to support more or less
		 * methods.
		 */
		private static final String[] SUPPORTED_METHODS = { "GET", "HEAD",
				"POST", "DELETE", "PUT" };

		private Application application;
		
		private HttpRequest request;
		
		private boolean headersWritten;

		private boolean finished;
		
		private boolean autoFinish;

		//TODO change this type
		private String writeBuffer;
		
		//TODO private Transform transforms; to be implemented
		//TODO ui
		//TODO ui["modules"]
		
		private int statusCode;
		
		private Map<String, String> headers; //_headers in web.py

		public RequestHandler() {}
		RequestHandler(Application application, HttpRequest request) {
			this.application = application;
			this.request = request;
			this.autoFinish = true;
			this.headers = new HashMap<String, String>();
			
			this.clear();
		}

		protected void head() {
			throw new HttpError(405);
		}

		protected void get() {
			throw new HttpError(405);
		}

		protected void post() {
			throw new HttpError(405);
		}

		protected void delete() {
			throw new HttpError(405);
		}

		protected void put() {
			throw new HttpError(405);
		}

		/**
		 * Called before the actual handler method.
		 * 
		 * Useful to override in a handler if you want a common bottleneck for
		 * all of your request
		 */
		protected void prepare() {
		}
		
		/**
		 * Resets all headers and content for this response
		 */
		private void clear() {
			headers.put("Server", "JTornadoServer/0.1");
			headers.put("Content-Type", "text/html; charset=UTF-8");
			
			if (request.supportsHttp11()) {
				if (request.headers.get("Connection", "").equals("Keep-Alive")) {
					setHeader("Connection", "Keep-Alive");
				}
			}
			
			writeBuffer = "";
			statusCode = 200;
		}
		
		/**
		 * Set the status of a response.
		 * 
		 * @param statusCode
		 */
		protected final void setStatus(int statusCode) {
			assert HttpCode.codes.keySet().contains(statusCode);
			this.statusCode = statusCode;
		}
		
		protected void setHeader(String name, String value) {
			value = utf8(value);
			String saveValue = value.replaceAll("[\u0000-\u001F]", " ");
			if (saveValue.length() > 4000)
				saveValue = saveValue.substring(0, 4001);
			if (!value.equals(saveValue))
				throw new IllegalArgumentException("Unsafe header value "
						+ value);

			this.headers.put(name, value);
		}
		
		protected void setHeader(String name, Number value) {
			this.headers.put(name, String.valueOf(value));
		}
		
		/**
		 * If a date is given, it is formated according to the HTTP
		 * specification.
		 * 
		 * @param name
		 * @param value
		 */
		protected void setHeader(String name, Date value) {
			String format = "EEE, dd MMM yyyy HH:mm:ss z";
			DateFormat df = new SimpleDateFormat(format);
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			this.headers.put(name, df.format(value));
		}

		/**
		 * Returns unicode value from the given argument name. If there is more
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
					throw new HttpError(404, "Missing Argument " + name);
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
		
		private Object cookies() {
			
			List<HttpCookie> list = HttpCookie.parse(request.headers.get("Cookie"));
			
			
			return null;
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
			finished = true;
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
	public static class HttpError extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final int statusCode;
		private final String logMessage;
		private final String[] args;
		private final static Formatter formatter = new Formatter();

		public HttpError(int statusCode) {
			this.statusCode = statusCode;
			this.logMessage = null;
			this.args = null;
		}

		public HttpError(int statusCode, String logMessage, String... args) {
			this.statusCode = statusCode;
			this.logMessage = logMessage;
			this.args = args;
		}

		@Override
		public String toString() {

			String message = formatter.format("HTTP %d: %s ", statusCode,
					HttpCode.get(statusCode)).toString();

			if (logMessage != null && !logMessage.isEmpty()) {
				return message + "(" + logMessage + " " + Arrays.toString(args)
						+ ")";
			} else {
				return message;
			}

		}

	}
	
	private static String utf8(String s) {
		try {
			return new String(s.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}
	
	private static String unicode(String s) {
		try {
			return new String(s.getBytes(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new HttpError(400, "Non-utf8 argument", e.getMessage());
		}
	}

}
