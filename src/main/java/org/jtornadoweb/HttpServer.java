package org.jtornadoweb;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.jtornadoweb.IOLoop.EventHandler;
import org.jtornadoweb.IOStream.StreamHandler;

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
public class HttpServer implements EventHandler {
	private final Logger logger = Logger
			.getLogger("org.jtornadoweb.HttpServer");

	private final Object requestCallback;
	private final Boolean noKeepAlive;
	private final Boolean xHeaders;
	private ServerSocketChannel serverSocketChannel;
	private final ExecutorService pool;

	private final IOLoop loop;

	public HttpServer(Object requestCallback, Boolean noKeepAlive, IOLoop loop,
			Boolean xHeaders) throws Exception {
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
				serverSocketChannel.validOps());
		this.getLoop().start();
	}

	private IOLoop getLoop() {
		return this.loop;
	}

	/**
	 * Handles the events from selector. Actually accepts the connection and
	 * After instantiate an HttpConnection - in a pooled Thread - tries to
	 * accept (non-Blocking) an eventually new connection. Otherwise returns.
	 */
	@Override
	public void handleEvents(SelectionKey key) throws Exception {
		int accepted = 0;
		while (true) {
			accepted++;
			try {
				SocketChannel clientChannel = ((ServerSocketChannel) key
						.channel()).accept();
				if (clientChannel == null) {
					return;
				}

				IOStream stream = new IOStream(clientChannel, this.getLoop());
				new HttpConnection(stream, "", requestCallback, noKeepAlive,
						xHeaders);

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	/**
	 * Holds a set of http headers. Incomming headers should not be modified.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpHeaders {

		private Map<String, String> _headers = new HashMap<String, String>();

		public static HttpHeaders parse(String header) {

			HttpHeaders newHeaders = new HttpHeaders();

			for (String line : header.split("\r\n")) {
				if (line.equals("") || line.equals("\r"))
					continue;
				String[] h = line.split(": ");
				newHeaders.put(h[0], h[1]);
			}
			return newHeaders;
		}

		public void put(String key, String value) {
			_headers.put(key, value);
		}

		public String get(String key, String defualt) {
			return _headers.containsKey(key) ? _headers.get(key) : defualt;
		}
	}

	/**
	 * Http connection responsible to parse HTTP content and call the
	 * application.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpConnection implements StreamHandler {
		private final Logger logger = Logger
				.getLogger("org.jtornadoweb.HttpServer.HttpConnection");
		private final IOStream stream;
		private final String address;
		private final Object requestCallback;
		private final Boolean noKeepAlive;
		private final Boolean xHeaders;

		public HttpConnection(IOStream stream, String address,
				Object requestCallback, Boolean noKeepAlive, Boolean xHeaders)
				throws Exception {
			this.stream = stream;
			this.address = address;
			this.requestCallback = requestCallback;
			this.noKeepAlive = noKeepAlive;
			this.xHeaders = xHeaders;
			stream.readUntil("\r\n\r\n", this);
		}

		/**
		 * Extracts the reader and reply to the client. <br>
		 * TODO complete the implementation
		 */
		@Override
		public void execute(String data) {
			try {

				int eol = data.indexOf("\r\n");
				String[] startLine = data.substring(0, eol).split(" ");
				String method = startLine[0];
				String uri = startLine[1];
				String version = startLine[2];

				if (!version.startsWith("HTTP/"))
					throw new RuntimeException(
							"Malformed HTTP version in HTTP Request-Line");

				HttpHeaders headers = HttpHeaders.parse(data.substring(eol,
						data.length() - 1));
				// TODO instantiate HttpRequest

				int contentLength = 0;
				Integer.valueOf(headers.get("Content-Lenght", "0"));

				if (contentLength > 0
						&& contentLength > stream.getMaxBufferSize()) {
					throw new RuntimeException("Content-Length too long");
				}

				if (contentLength > 0
						&& headers.get("Expect", "").equals("100-continue")) {
					stream.write("HTTP/1.1 100 (Continue)\r\n\r\n");
				}
				// stream.readBytes(contentLen)

				stream.write("HTTP/1.1 200 OK\r\nContent-Length: "
						+ "Hello\n".getBytes().length + data.getBytes().length
						+ "\r\n\r\n" + "Hello\n");
				stream.write(data);

				try {
					stream.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

}
