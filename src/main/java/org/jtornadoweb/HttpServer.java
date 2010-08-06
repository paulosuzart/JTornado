package org.jtornadoweb;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import org.jtornadoweb.IOLoop.EventHandlerAdapter;
import org.jtornadoweb.IOStream.StreamHandler;
import org.jtornadoweb.Web.RequestCallback;
import org.jtornadoweb.util.CollectionUtils;
import org.jtornadoweb.util.HttpUtils;
import org.jtornadoweb.util.StringUtils;

/**
 * A primary Http Server that simply replies the HTTP content requested.<br>
 * <p>
 * 
 * <pre>
 * Usage: HttpServer server = new HttpServer(null, false, null, null);
 * server.listen(8089);
 * </pre>
 * 
 * </p>
 * <p>
 * Objects as instances of java.lang.Object are not defined yet and will be
 * created with the same meaning for Tornado.
 * 
 * Each HttpConnection uses a thread available in the pool. The Http Server
 * accepts connections in the main thread.
 * </p>
 * 
 * 
 * @author paulosuzart@gmail.com
 * 
 */
public class HttpServer extends EventHandlerAdapter {
	private final Logger logger = Logger
			.getLogger("org.jtornadoweb.HttpServer");

	private final RequestCallback requestCallback;
	private final boolean noKeepAlive;
	private final boolean xHeaders;
	private ServerSocketChannel serverSocketChannel;
	private final ExecutorService pool;

	private final IOLoop loop;

	@SuppressWarnings("unused")
	private static class TFactory implements ThreadFactory {
		private int count;

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread();
			thread.setName("JTornado Task-" + count++);
			thread.setPriority(Thread.NORM_PRIORITY);
			return thread;
		}

	}

	public HttpServer(RequestCallback requestCallback, boolean noKeepAlive,
			IOLoop loop, boolean xHeaders) throws Exception {
		if (requestCallback == null) {
			throw new Exception("RequestCallback required");
		}

		logger.info("Starting Http Server");
		this.requestCallback = requestCallback;
		logger.info("noKeepAlive: " + noKeepAlive);
		this.noKeepAlive = noKeepAlive;
		this.xHeaders = xHeaders;
		this.serverSocketChannel = null;
		logger.info("Thread poll fixed in "
				+ Runtime.getRuntime().availableProcessors() + " threads");
		this.pool = Executors.newFixedThreadPool(Runtime.getRuntime()
				.availableProcessors());

		this.loop = (loop == null ? new IOLoop(pool) : loop);
	}

	/**
	 * Binds the socket provided by the channel to a port. The backlog for the
	 * bind is 128 connections just like in Tornado. Starts the IOLoop.
	 * 
	 * @param port
	 * @throws Exception
	 */
	public void listen(int port) throws Exception {
		serverSocketChannel = ServerSocketChannel.open();
		final ServerSocket socket = serverSocketChannel.socket();
		socket.setReuseAddress(true);
		socket.bind(new InetSocketAddress(port), 128);
		this.getLoop().addHandler(serverSocketChannel, this,
				SelectionKey.OP_ACCEPT);
		this.getLoop().start();
	}

	private IOLoop getLoop() {
		return this.loop;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.jtornadoweb.IOLoop.EventHandlerAddapter#onAccept(java.nio.channels
	 * .SelectableChannel)
	 */
	@Override
	protected void onAccept(SelectableChannel channel) throws Exception {
		IOStream stream = new IOStream((SocketChannel) channel, this.getLoop());
		new HttpConnection(stream, ((SocketChannel) channel).socket()
				.getInetAddress(), requestCallback, noKeepAlive, xHeaders);
	}

	/**
	 * Holds a set of http headers. Incoming headers should not be modified.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpHeaders {

		private Map<String, String> _headers = new HashMap<String, String>();

		public static HttpHeaders parse(String header) {

			HttpHeaders newHeaders = new HttpHeaders();
			if (header == null || header.trim().length() == 0)
				return newHeaders;

			for (String line : header.split("\r\n")) {
				if (line.equals("") || line.equals("\r"))
					continue;

				String[] h = line.split(":");
				if (h.length == 1)
					continue;
				newHeaders.put(h[0], h[1].trim());
			}
			return newHeaders;
		}

		public void put(String key, String value) {
			_headers.put(key, value);
		}

		public String get(String key, String defualt) {
			return _headers.containsKey(key) ? _headers.get(key) : defualt;
		}

		public String get(String name) {
			return _headers.get(name);
		}

		/**
		 * Returns true if the given name is a keu in _headers.
		 * 
		 * @param name
		 * @return
		 */
		public boolean contains(String name) {
			return _headers.containsKey(name);
		}
	}

	/**
	 * Http connection responsible to parse HTTP content and call the
	 * application.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpConnection {
		private final Logger logger = Logger
				.getLogger("org.jtornadoweb.HttpServer.HttpConnection");

		private final IOStream stream;
		private final InetAddress address;
		private final RequestCallback requestCallback;
		private final boolean noKeepAlive;
		private final boolean xHeaders;
		private HttpRequest request;
		private boolean requestFinished;

		private StreamHandler onHeaders = new StreamHandler() {

			@Override
			public void execute(String data) throws Exception {
				onHeaders(data);
			}
		};

		public HttpConnection(IOStream stream, InetAddress inetAddress,
				RequestCallback requestCallback, boolean noKeepAlive,
				boolean xHeaders) throws Exception {
			this.stream = stream;
			this.address = inetAddress;
			this.requestCallback = requestCallback;
			this.noKeepAlive = noKeepAlive;
			this.xHeaders = xHeaders;
			stream.readUntil("\r\n\r\n", onHeaders);
		}

		void finishRequest() throws Exception {
			boolean disconnect;
			if (noKeepAlive)
				disconnect = true;
			else {
				String connectionHeader = request.headers.get("Connection", "");
				if (request.supportsHttp11())
					disconnect = connectionHeader.equals("close");
				else if (request.headers.contains("Content-Length")
						|| request.method.equals("GET")
						|| request.method.equals("POST"))
					disconnect = connectionHeader
							.equalsIgnoreCase("Keep-Alive");
				else
					disconnect = true;
			}
			request = null;
			requestFinished = false;
			if (disconnect) {
				stream.close();
				return;
			}
			stream.readUntil("\r\n\r\n", onHeaders);

		}

		/**
		 * Extracts the reader and reply to the client. <br>
		 * TODO complete the implementation
		 */
		public void onHeaders(String data) {// HTTPConnection._on_headers
			try {

				int eol = data.indexOf("\r\n");
				String[] startLine = data.substring(0, eol).split(" ");
				String method = startLine[0];
				String uri = startLine[1];
				String version = startLine[2];

				if (!version.startsWith("HTTP/"))
					throw new RuntimeException(
							"Malformed HTTP version in HTTP Request-Line");

				HttpHeaders headers = HttpHeaders.parse(data.substring(eol + 2,
						data.length() - 1));

				request = new HttpRequest(method, uri, version, headers,
						address.getHostAddress(), this);

				int contentLength = Integer.valueOf(headers.get(
						"Content-Length", "0"));

				if (contentLength > 0) {

					if (contentLength > stream.getMaxBufferSize()) {
						throw new RuntimeException("Content-Length too long");
					}

					if (headers.get("Expect", "").equals("100-continue")) {
						stream.write("HTTP/1.1 100 (Continue)\r\n\r\n");
					}

					StreamHandler onBody = new StreamHandler() {

						@Override
						public void execute(String data) throws Exception {
							onRequestBody(data);
						}
					};
					stream.readBytes(contentLength, onBody);
					return;
				}

				requestCallback.execute(request);

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		private void onRequestBody(String data) throws Exception {
			request.body = data;
			String contentType = request.headers.get("Content-Type", "");
			if ("POST".equals(request.method)) {
				if (contentType.startsWith("application/x-www-form-urlencoded")) {
					Map<String, List<String>> arguments = HttpUtils
							.parseQueryString(request.body);
					for (Entry<String, List<String>> e : arguments.entrySet()) {
						String name = e.getKey();
						List<String> values = e.getValue();
						// TODO line above must check if each the value is not empty
						if (values != null && !values.isEmpty())
							CollectionUtils.setDefault(request.arguments, name,
									new ArrayList<String>()).addAll(values);

					}

				} else if (contentType.startsWith("multipart/form-data")) {
					if (contentType.contains("boundary=")) {
						String boundary = contentType.split("boundary=", 2)[1];
						if (boundary != null)
							parseMimeBody(boundary, data);
					} else {
						logger.warning("Invalid multipart/form-data");
					}
				}
			}
			requestCallback.execute(request);
		}

		/**
		 * TODO Mutability sux! make this code immutable.
		 * 
		 * @param boundary
		 * @param data
		 */
		@SuppressWarnings({ "unchecked", "serial" })
		private void parseMimeBody(String boundary, String data) {
			int footerLen = 0;
			if (boundary.startsWith("\"") && boundary.endsWith("\""))
				boundary = StringUtils.substring(boundary, "1:-1");

			footerLen = data.endsWith("\r\n") ? boundary.length() + 6
					: boundary.length() + 4;

			String[] parts = StringUtils.substring(data, ":-" + footerLen)
					.split("--" + boundary + "\r\n");
			for (String part : parts) {
				if ("".equals(part))
					continue;
				int eoh = part.indexOf("\r\n\r\n");
				if (eoh == -1) {
					logger.warning("multipart/form-data missing headers");
					continue;
				}
				HttpHeaders headers = HttpHeaders.parse(StringUtils.substring(
						part, ":" + eoh));
				String nameHeader = headers.get("Content-Disposition", "");
				if (!nameHeader.startsWith("form-data;")
						|| !part.endsWith("\r\n")) {
					logger.warning("Invalid multipart/form-data");
					continue;
				}
				final String value = StringUtils.substring(part, eoh + 4
						+ ":-2");
				final Map<String, String> nameValues = new HashMap<String, String>();
				for (String namePart : StringUtils.substring(nameHeader, "10:")
						.split(";")) {
					final String[] _split = namePart.trim().split("=", 2);
					String name = _split[0];
					String nameValue = _split[1];
					try {
						nameValues.put(name, URLDecoder.decode(
								nameValue.replace("\"", ""), "utf-8"));

					} catch (UnsupportedEncodingException e) {
						logger.finest(e.getMessage());
						continue;
					}

				}

				if (!nameValues.containsKey("name")) {
					logger.warning("multipart/form-data value missing name");
					continue;
				}

				String name = nameValues.get("name");
				if (nameValues.containsKey("filename")) {
					final String contentType = headers.get("Content-Type",
							"application/unknown");
					CollectionUtils.setDefault(request.files, name,
							new HashMap<String, Map<String, String>>()).putAll(
							new HashMap<String, String>() {
								{
									put("filename", nameValues.get("filename"));
									put("body", value);
									put("contet_type", contentType);
								}
							});
				} else {
					CollectionUtils.setDefault(request.arguments, name,
							new ArrayList<String>()).add(value);
				}

			}

		}

		public void write(byte[] bytes) {
			assert (request == null);
			if (stream.closed)
				return;
			StreamHandler handler = new StreamHandler() {

				@Override
				public void execute(String data) throws Exception {
					onWriteComplete();
				}
			};
			stream.write(bytes, handler);
		}

		private void onWriteComplete() throws Exception {
			if (requestFinished)
				finishRequest();
		}

		public void finish() throws Exception {
			assert (request == null);
			requestFinished = true;
			if (!stream.writing)
				finishRequest();

		}

	}

	/**
	 * Represents a single HttpConnection.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpRequest {

		boolean requestFinished;
		String method;
		String uri;
		String version = "HTTP/1.0";
		HttpHeaders headers;
		String body; // TODO which type?
		String remoteIp;
		String protocol;
		String host;
		Map<String, Map> files; // TODO which type?
		HttpConnection connection;
		long startTime;
		long finishTime;
		Map<String, List<String>> arguments;
		String query;
		String path;

		public HttpRequest(String method, String uri, String version,
				HttpHeaders headers, String remoteIp, HttpConnection connection)
				throws Exception {
			this.method = method;
			this.uri = uri;
			this.version = version;
			this.headers = headers;
			this.files = new HashMap<String, Map>(); 

			if (connection.xHeaders) {
				// this.remoteIp = headers.get("X-Real-Ip", remoteIp);
				this.protocol = headers.get("X-Scheme", protocol);
			} else {
				this.remoteIp = remoteIp;
			}
			protocol = protocol == null ? "http" : protocol;

			this.connection = connection;
			this.startTime = System.currentTimeMillis();

			URI url = URI.create(uri);

			query = url.getQuery();
			String fragment = url.getFragment();
			path = url.getPath();
			String netloc = url.getHost();
			String sheme = url.getScheme();

			arguments = HttpUtils.parseQueryString(uri);

			// arguments = cgi.parse_qs(query)
			// self.arguments = {}
			// for name, values in arguments.iteritems():
			// values = [v for v in values if v]
			// if values: self.arguments[name] = values
		}

		/**
		 * Check the version and returns true if its HTTP/1.1
		 * 
		 * @return
		 */
		public boolean supportsHttp11() {
			return version.equalsIgnoreCase(version);
		}

		public void write(byte[] bytes) {
			connection.write(bytes);
		}

		public void finish() {
			try {
				connection.finish();
				finishTime = System.currentTimeMillis();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

}
