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
	private final CharBuffer streamRead;

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
		this.streamRead = stream.duplicate();
		this.writeBuffer = ByteBuffer.allocate(readChunckSize);
	}

	/**
	 * Invoke the callback if the given delimiter is found.
	 * 
	 * @param delimiter
	 * @param callback
	 * @throws Exception
	 */
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

		readBuffer.mark();
		streamRead.mark();
		while (client.read(readBuffer) > 0) {

			CharsetDecoder decoder = charSet.newDecoder();

			ByteBuffer dupReadBuffer = readBuffer.duplicate();
			dupReadBuffer.flip();

			decoder.decode(dupReadBuffer, streamRead, true);
			decoder.flush(streamRead);
		}
		readBuffer.reset();
		streamRead.reset();

		// If delimiter is still present, callback should be excecuted if the
		// content is found.
		if (delimiter != null) {

			String found = find(delimiter);
			if (found != "") {
				StreamHandler cback = callback;
				callback = null;
				delimiter = null;
				cback.execute(found);
			} else {
				// content not yet available. lets wait for it.
				 loop.addHandler(client, this, SelectionKey.OP_READ);
			}

		}

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

		String sStream = stream.toString();
		int index = sStream.indexOf(searchString);
		if (index > -1) {
			String found = sStream.substring(0, index + searchString.length());
			int forwardPosition = index + delimiter.length();
			stream.position(stream.position() + forwardPosition);
			return found;
		}
		return "";

	}

	public void close() throws Exception {
		this.client.close();

	}

}
