package org.jtornadoweb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;

import org.jtornadoweb.IOLoop.EventHandler;

public class IOStream implements EventHandler {

	static interface StreamHandler {
		public void execute(String data);
	}

	private static Charset charSet = Charset.forName("UTF-8");
	private final SocketChannel client;
	private final int maxBufferSize;
	private final int readChunckSize;
	private final ByteBuffer readBuffer;
	private final ByteBuffer writeBuffer;
	private final CharBuffer stream;
	private String delimiter;
	private StreamHandler callback;
	private IOLoop loop;

	public IOStream(SocketChannel client, IOLoop loop) {
		this.client = client;
		this.loop = loop;
		if (loop == null) {
			// implementar metodo singleton getInstance
		}
		this.maxBufferSize = 104857600;
		this.readChunckSize = 8192;
		this.readBuffer = ByteBuffer.allocate(readChunckSize);
		this.stream = CharBuffer.allocate(readChunckSize);
		this.writeBuffer = ByteBuffer.allocate(readChunckSize);
	}

	public void readUntil(String delimiter, StreamHandler callback)
			throws Exception {

		String found = find(delimiter);
		if (found.length() > 0) {
			callback.execute(found);
			return;
		}
		checkClosed();
		this.delimiter = delimiter;
		this.callback = callback;
		this.loop.addHandler(client, this, SelectionKey.OP_READ);

	}

	private void checkClosed() {
		if (!this.client.isOpen()) {
			throw new RuntimeException("Stream is closed");
		}

	}

	public int getMaxBufferSize() {
		return maxBufferSize;
	}

	public void write(String string) {

		try {
			writeBuffer.put(string.getBytes()).flip();
			client.write(writeBuffer);
			writeBuffer.clear();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvents(SelectionKey key) throws Exception {
		this.handleRead();

	}

	/**
	 * If this code is being executed, the SO guarantees that there is at least
	 * one byte to read. The channel is queried in slots of 8192 bytes. TODO
	 * 
	 * @throws Exception
	 */
	private void handleRead() throws Exception {

		client.read(readBuffer);

		CharsetDecoder decoder = charSet.newDecoder();

		ByteBuffer dupReadBuffer = readBuffer.duplicate();
		dupReadBuffer.flip();

		decoder.decode(dupReadBuffer, stream, true);
		decoder.flush(stream);

		if (delimiter != null) {
			callback.execute(find(delimiter));
			return;
		}

		// callback.execute(stringBuffer.toString());
	}

	/**
	 * Attempt to find the chars in the remaining chars of the current stream.
	 * If the stream is not ready to be used (null) an "" are returned.
	 * 
	 * 
	 * @param searchString
	 * @return "" or the found string.
	 */
	private String find(String searchString) {
		if (this.stream.position() < searchString.length() - 1)
			return "";

		char[] _find = searchString.toCharArray();
		char[] extract = new char[_find.length];
		CharBuffer searchStream = stream.duplicate();
		searchStream.flip();
		do {
			searchStream.get(extract);
		} while (!Arrays.equals(extract, _find));

		return searchStream.flip().toString();

	}

	public void close() throws Exception {
		this.client.close();

	}

}
