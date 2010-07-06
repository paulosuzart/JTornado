package org.jtornadoweb;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.management.RuntimeErrorException;

import org.jtornadoweb.IOLoop.EventHandler;
import org.jtornadoweb.IOStream.StreamHandler;

import sun.util.logging.resources.logging;

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
	private final Logger logger = Logger.getLogger("org.jtornadoweb.HttpServer");

	private final Object requestCallback;
	private final Boolean noKeepAlive;
	private final Object ioLoop;
	private final Boolean xHeaders;
	private ServerSocketChannel serverSocketChannel;
	private final ExecutorService pool;
	
	private static ThreadLocal<IOLoop> loop = new ThreadLocal<IOLoop>();

	public HttpServer(Object requestCallback, Boolean noKeepAlive,
			Object ioLoop, Boolean xHeaders) throws Exception {
		logger.info("Starting Http Server");
		this.requestCallback = requestCallback;
		logger.info("noKeepAlive: " + noKeepAlive);
		this.noKeepAlive = noKeepAlive;
		this.ioLoop = ioLoop;
		this.xHeaders = xHeaders;
		this.serverSocketChannel = null;
		logger.info("Thread poll fixed in 2 threads");
		this.pool = Executors.newFixedThreadPool(2);
		loop.set(new IOLoop(pool));
		//this.loop = new IOLoop(pool);
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
		return this.loop.get();
	}
	/**
	 * Handles the events from selector. Actually accepts the connection and
	 * After instantiate an HttpConnection - in a pooled Thread - tries to
	 * accept (non-Blocking) an eventually new connection. Otherwise returns.
	 */
	@Override
	public void handleEvents(SelectableChannel serverChannel, SelectionKey key) {

		while (true) {
			try {
				SocketChannel clientChannel = ((ServerSocketChannel) serverChannel)
						.accept();
				if (clientChannel == null)
					return;
				logger.info("Conn Accepted from "  + clientChannel.socket().getInetAddress().getHostAddress());
				IOStream stream = new IOStream(clientChannel, this.getLoop());
				pool.execute(new HttpConnection(stream, "", requestCallback,
						noKeepAlive, xHeaders));
				logger.info("HttpConnection started");
				logger.info(Thread.currentThread().getName());
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	/**
	 * Http connection responsible to parse HTTP content and call the
	 * application.
	 * 
	 * @author paulosuzart@gmail.com
	 * 
	 */
	public static class HttpConnection implements Runnable {
		private final Logger logger = Logger.getLogger("org.jtornadoweb.HttpServer.HttpConnection");
		private final IOStream stream;
		private final String address;
		private final Object requestCallback;
		private final Boolean noKeepAlive;
		private final Boolean xHeaders;
		private Map<String, String> headers = new HashMap<String, String>();

		public HttpConnection(IOStream stream, String address,
				Object requestCallback, Boolean noKeepAlive, Boolean xHeaders) {
			this.stream = stream;
			this.address = address;
			this.requestCallback = requestCallback;
			this.noKeepAlive = noKeepAlive;
			this.xHeaders = xHeaders;
			
		}

		/**
		 * Extracts the reader and reply to the client. <br>
		 * TODO complete the implementation
		 */
		@Override
		public void run() {
			try {
				stream.readUntil("\r\n\r\n", new StreamHandler() {
					
					// HANDLE HEADERS
					@Override
					public void execute(String data) {
						
						logger.info("Starting request serving");
						logger.info("HEADER"  + Thread.currentThread().getName());
						int eol = data.indexOf("\r\n");
						String[] startLine = data.substring(0, eol).split(" ");
						String method = startLine[0];
						String uri = startLine[1];
						String version = startLine[2];
						
						if (!version.startsWith("HTTP/"))
							throw new RuntimeException(
									"Malformed HTTP version in HTTP Request-Line");

						for (String line : data.substring(eol,
								data.length() - 1).split("\r\n")) {
							if (line.equals("") || line.equals("\r"))
								continue;
							String[] header = line.split(": ");
							headers.put(header[0], header[1]);

						}

						int contentLength = 0; // Integer.valueOf(headers
						// .get("Content-Lenght"));
						if (contentLength > 0
								&& contentLength > stream.getMaxBufferSize()) {
							throw new RuntimeException(
									"Content-Length too long");
						}

						// if (headers.get("Expect").equals("100-continue")) {
						// stream.write("HTTP/1.1 100 (Continue)\r\n\r\n");
						// }
						// stream.readBytes(contentLen)
						stream.write(data);
						logger.info("Request finished");
						try {
							stream.close();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	public static void main(String[] args) throws Exception {
		HttpServer server = new HttpServer(null, false, null, null);
		server.listen(8089);
	}

}
