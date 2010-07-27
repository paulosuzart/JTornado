package org.jtornadoweb;

import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jtornadoweb.HttpServer.HttpRequest;
import org.jtornadoweb.util.Base64Coder;

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

		private final Logger logger = Logger.getLogger(Web.class.toString());

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

		// TODO change this type
		private String writeBuffer;

		// TODO private Transform transforms; to be implemented
		// TODO ui
		// TODO ui["modules"]

		private int statusCode;

		private Map<String, String> headers; // _headers in web.py

		private Map<String, HttpCookie> cookies;

		private Map<String, HttpCookie> newCookies;

		public RequestHandler() {
			this.autoFinish = true;
			this.headers = new HashMap<String, String>();
		}

		public void head() {
			throw new HttpError(405);
		}

		public void get() {
			throw new HttpError(405);
		}

		public void post() {
			throw new HttpError(405);
		}

		public void delete() {
			throw new HttpError(405);
		}

		protected void put() {
			throw new HttpError(405);
		}

		/**
		 * Called before the actual handler method.
		 * 
		 * Useful to override in a handler if you want a common bottleneck for
		 * all of your request.
		 * 
		 * May be overriden by child class.
		 */
		public void prepare() {
		}

		/**
		 * Resets all headers and response content.
		 */
		private void clear() {
			headers.put("Server", "JTornadoServer/0.1");
			headers.put("Content-Type", "text/html; charset=UTF-8");

			if (!request.supportsHttp11())
				if (request.headers.get("Connection", "").equals("Keep-Alive"))
					setHeader("Connection", "Keep-Alive");

			writeBuffer = "";
			statusCode = 200;
		}

		/**
		 * Set the status of a response.
		 * 
		 * @param statusCode
		 */
		protected void setStatus(int statusCode) {
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
			this.headers.put(name, formatDate(value));
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

		/**
		 * Creates a Map of HttpCookie
		 */
		protected Map<String, HttpCookie> cookies() {
			if (this.cookies == null) {
				this.cookies = new HashMap<String, HttpCookie>();
				String cookie = request.headers.get("Cookie");
				if (cookie != null && !"".equals(cookie)) {
					try {
						List<HttpCookie> _cookies = HttpCookie.parse(cookie);
						for (HttpCookie c : _cookies) {
							this.cookies.put(c.getName(), c);
						}
					} catch (IllegalArgumentException e) {
						this.clearAllCookies();
					}
				}
			}

			return this.cookies;
		}

		/**
		 * Return the cookie value of a given name, else return the default
		 * value.
		 * 
		 * @param name
		 * @param defaultValue
		 * @return
		 */
		protected String getCookie(String name, String defaultValue) {
			if (cookies().keySet().contains(name))
				return cookies().get(name).getValue();
			return defaultValue;
		}

		/**
		 * Set a cookie with the given options.
		 * 
		 * @param name
		 * @param value
		 * @param domain
		 * @param expires
		 * @param path
		 * @param expiresDays
		 */
		protected void setCookie(String name, String value, String domain,
				Calendar expires, String path, Integer expiresDays) {
			name = utf8(name);
			value = utf8(value);

			if (name.concat(value).matches(".*[\u0000-\u0020].*"))
				// Don't let inject any bad stuff
				throw new IllegalArgumentException(String.format(
						"Invalid cookie %s : %s", name, value));

			if (this.newCookies == null)
				this.newCookies = new HashMap<String, HttpCookie>();

			HttpCookie newCookie = new HttpCookie(name, value);
			this.newCookies.put(name, newCookie);

			if (domain != null && !"".equals(domain))
				newCookie.setDomain(domain);

			if (expiresDays != null && expires == null) {
				expires = Calendar.getInstance();
				expires.add(Calendar.DAY_OF_YEAR, expiresDays);
			}

			if (expires != null)
				newCookie.setMaxAge(expires.getTimeInMillis() / 1000);

			if (path != null && !"".equals(path))
				newCookie.setPath(path);
		}

		/**
		 * Deletes the cookie
		 * 
		 * @param name
		 *            Cookie name
		 */
		protected void clearCookie(String name) {
			Calendar expires = Calendar.getInstance();
			expires.add(Calendar.DAY_OF_YEAR, -365);
			this.setCookie(name, "", null, expires, "/", null);
		}

		/**
		 * Removes all the cookies of this request sent by the user
		 */
		protected void clearAllCookies() {
			for (String name : this.cookies.keySet())
				this.clearCookie(name);
		}

		/**
		 * Signs and timestamps a cookie so it cannot be forged.
		 * 
		 * You must specify the 'cookie_secret' setting in your Application to
		 * use this method. It should be a long, random sequence of bytes to be
		 * used as the HMAC secret for the signature.
		 * 
		 * To read a cookie set with this method, use get_secure_cookie().
		 */
		protected void setSecureCookie(String name, String value,
				String domain, Calendar expires, String path,
				Integer expiresDays) {

			String timestamp = String.valueOf(System.currentTimeMillis());
			value = Base64Coder.encodeString(value);
			String signature = this.cookieSignature(value, timestamp);
			value = join("|", value, timestamp, signature);

			expiresDays = expiresDays == null ? 30 : expiresDays;

			this.setCookie(name, value, domain, expires, path, expiresDays);
		}

		/**
		 * If the cookie is valid return if otherwise return null.
		 * 
		 * @param name
		 * @return
		 */
		protected String getSecureCookie(String name) {
			String value = this.getCookie(name, null);
			if (value == null)
				return null;

			String[] parts = value.split("\\|");

			if (parts.length != 3)
				return null;

			if (!this.cookieSignature(parts[0], parts[1]).equals(parts[3])) {
				logger.warning("Invalid cookie signature " + value);
				return null;
			}

			long timestamp = Long.parseLong(parts[1]);
			if (timestamp < System.currentTimeMillis() - 31 * 86400) {
				logger.warning("Expired cookie " + value);
				return null;
			}

			try {
				byte[] buffer = Base64Coder.decodeLines(parts[0]);
				return new String(buffer);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		private String cookieSignature(String... parts) {
			this.requireSetting("cookie_secret", "secure cookies");
			String hexdigest = "";
			try {
				SecretKey key = new SecretKeySpec(application.settings.get(
						"cookie_secret").getBytes(), "HmacSHA1");
				Mac m = Mac.getInstance("HmacSHA1");
				m.init(key);
				for (String part : parts)
					m.update(part.getBytes());
				byte[] mac = m.doFinal();

				for (byte b : mac) {
					// TODO Maybe doesn't need this verifications, only
					// toHexString can resolve
					String hex = Integer.toHexString(0xFF & b);
					hexdigest += hex.length() == 1 ? "0" + hex : hex;
				}
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
			return hexdigest;
		}

		/**
		 * Throws an exception if a given setting is not defined.
		 * 
		 * @param name
		 * @param feature
		 */
		private void requireSetting(String name, String feature) {
			if (application.settings.get(name) == null) {
				String msg = "You must define the '%s' setting in your application to use %s";
				throw new IllegalArgumentException(String.format(msg, name,
						feature));
			}
		}

		/**
		 * Executes the http request dispatching the execution to the right
		 * method.
		 */
		public void execute() {
			// self._transforms = transforms ???
			try {
				if (Arrays.binarySearch(SUPPORTED_METHODS, request.method) < 0)
					throw new HttpError(405, request.method);

				if ("POST".equals(request.method))
					// && application.settings.get("xsrf_cookies")) TODO
					checkXsrfCookie();

				prepare();

				if (!finished) {
					getClass().getMethod(request.method.toLowerCase()).invoke(
							this);
					if (autoFinish && !finished)
						finish();
				}
			} catch (Exception e) {
				this.handleRequestException(e);
			}
		}

		private void checkXsrfCookie() {
			// TODO Auto-generated method stub

		}

		private void handleRequestException(Exception e) {
			if (e instanceof HttpError) {
				//TODO finish this method
			} else {
				logger.severe(String.format("Uncaught exception %s \n %s",
						requestSumary(), request));
				sendError(500);
			}
		}

		private String requestSumary() {
			return this.request.method + " " + this.request.uri + " ("
					+ this.request.remoteIp + ")";
		}

		protected void write(String buffer) {
			writeBuffer = buffer;
		}

		/**
		 * Finishes this response, ending the HTTP request.
		 * 
		 * @param chunk
		 */
		private void finish(String chunk) {
			
			assert !this.finished;
			if (chunk != null)
				this.write(chunk);
			
			if (!this.headersWritten) {
				if (this.statusCode == 200 && "GET".equals(this.request.method)) {
					
				}
			}
			
			flush();
			request.finish();
			finished = true;
		}
		
		/**
		 * Finishes this response, ending the HTTP request.
		 */
		private void finish() {
			flush();
			request.finish();
			finished = true;
		}

		/**
		 * Sends the HTTP error to the browser.
		 * 
		 * @param statusCode
		 */
		protected void sendError(Integer statusCode) {
			if (this.headersWritten) {
				String msg = "Cannot send error response after headers written";
				logger.severe(msg);
				if (!this.finished) {
					this.finish();
				}
				return;
			}
			this.clear();
			this.setStatus(statusCode);
			String message = this.getErrorHtml(statusCode);
			this.finish(message);
		}

		/**
		 * Override this method to implement custom error pages.
		 * 
		 * @param statusCode
		 * @return
		 */
		protected String getErrorHtml(Integer statusCode) {
			String page = "<html><title>%1$d: %2$s</title>"
					+ "<body>%1$d: %2$s</body></html>";
			return String.format(page, statusCode, HttpCode.get(statusCode));
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

		public Map<String, String> settings;
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

		/**
		 * Get a new Instance of RequestHandler and invokes the http method.
		 */
		@Override
		public void execute(HttpRequest request) {
			String path = request.path;

			RequestHandler handler = null;

			for (Map.Entry<Pattern, Class<? extends RequestHandler>> entry : handlers
					.entrySet()) {
				if (entry.getKey().matcher(path).matches()) {
					try {

						handler = (RequestHandler) entry.getValue()
								.newInstance();
						handler.application = this;
						handler.request = request;
						handler.clear();

					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
				}
			}

			if (handler == null) {
				// TODO handle not found.
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

	private static String formatDate(Date value) {
		String format = "EEE, dd MMM yyyy HH:mm:ss z";
		DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		return df.format(value);
	}

	public static String join(String delimiter, String... args) {
		Iterator<String> iter = Arrays.asList(args).iterator();
		if (!iter.hasNext())
			return "";
		StringBuffer buffer = new StringBuffer(iter.next());
		while (iter.hasNext())
			buffer.append(delimiter).append(iter.next());
		return buffer.toString();
	}

}
