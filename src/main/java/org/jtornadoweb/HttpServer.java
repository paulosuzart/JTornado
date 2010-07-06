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

import javax.management.RuntimeErrorException;

import org.jtornadoweb.IOLoop.EventHandler;
import org.jtornadoweb.IOStream.StreamHandler;

public class HttpServer implements EventHandler {

	private final Object requestCallback;
	private final Boolean noKeepAlive;
	private final Object ioLoop;
	private final Boolean xHeaders;
	private ServerSocketChannel serverSocketChannel;
	private final ExecutorService pool;
	private final IOLoop loop;

	public HttpServer(Object requestCallback, Boolean noKeepAlive,
			Object ioLoop, Boolean xHeaders) throws Exception {
		this.requestCallback = requestCallback;
		this.noKeepAlive = noKeepAlive;
		this.ioLoop = ioLoop;
		this.xHeaders = xHeaders;
		this.serverSocketChannel = null;
		this.pool = Executors.newFixedThreadPool(3);
		this.loop = new IOLoop();
	}

	public void listen(int port) throws Exception {
		serverSocketChannel = ServerSocketChannel.open();
		final ServerSocket socket = serverSocketChannel.socket();
		socket.setReuseAddress(true);
		socket.bind(new InetSocketAddress(port), 128);
		this.loop.addHandler(serverSocketChannel, this,
				serverSocketChannel.validOps());
		this.loop.start();
	}

	@Override
	public void handleEvents(SelectableChannel serverChannel, SelectionKey key) {

		while (true) {
			try {
				SocketChannel clientChannel = ((ServerSocketChannel) serverChannel)
						.accept();
				if (clientChannel == null)
					return;
				IOStream stream = new IOStream(clientChannel, this.loop);
				pool.execute(new HttpConnection(stream, "", requestCallback,
						noKeepAlive, xHeaders));

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public static class HttpConnection implements Runnable {

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

		@Override
		public void run() {
			try {
				stream.readUntil("\r\n\r\n", new StreamHandler() {
					//HANDLE HEADERS
					@Override
					public void execute(String data) {
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
								if (line.equals("") || line.equals("\r")) continue;
								String[] header = line.split(": ");
								headers.put(header[0], header[1]);

							}

							int contentLength = 0; //Integer.valueOf(headers
									//.get("Content-Lenght"));
							if (contentLength > 0
									&& contentLength > stream.getMaxBufferSize()) {
								throw new RuntimeException(
										"Content-Length too long");
							}

							//if (headers.get("Expect").equals("100-continue")) {
							//	stream.write("HTTP/1.1 100 (Continue)\r\n\r\n");
						//	}
						// stream.readBytes(contentLen)
							stream.write(data);
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
