package org.jtornadoweb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.jtornadoweb.IOLoop.EventHandler;

public class IOStream implements EventHandler {

	static interface StreamHandler {
		public void execute(String data) throws Exception;
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
	boolean writing;
	boolean closing;
	boolean closed;
	private StreamHandler writeCallback;

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
		// TODO remove this additional buffer.
		this.streamRead = stream.duplicate();
		this.writeBuffer = ByteBuffer.allocateDirect(readChunckSize);
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
			try {

				callback.execute(found);
			} catch (Exception e) {
				close();
			}
			return;
		}
		checkClosed();
		this.delimiter = delimiter;
		this.callback = callback;
		this.loop.addHandler(client, this, SelectionKey.OP_READ);

	}

	public void readBytes(int amount, StreamHandler callback) throws Exception {
		// streamRead.reset();
		char[] _chars = new char[amount];
		streamRead.get(_chars);
		String data = new String(_chars);
		callback.execute(data);

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

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void write(byte[] bytes, StreamHandler handler) {
		checkClosed();
		writing = true;
		writeBuffer.mark();
		writeBuffer.put(bytes);
		try {
			loop.addHandler(client, this, SelectionKey.OP_WRITE);
			writeCallback = handler;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvents(int opts, SelectableChannel channel)
			throws Exception {
		if (SelectionKey.OP_READ == opts) {
			this.handleRead();
		} else if (SelectionKey.OP_WRITE == opts) {
			this.handleWrite();
		}

	}

	private void handleWrite() throws Exception {
		ByteBuffer tempWrite = writeBuffer.duplicate();
		tempWrite.reset();

		while (tempWrite.remaining() > 0) {
			try {
				client.write(tempWrite);
				writing = false;
			} catch (Exception e) {
				// How to handle would block?
				e.printStackTrace();
				close();
				return;
			}

		}
		writeBuffer.compact();
		if (this.closing) {
			this.close();
		} else {
			if (tempWrite.remaining() == 0 && writeCallback != null) {
				StreamHandler callback = writeCallback;
				writeCallback = null;
				try {
					callback.execute("");
				} catch (Exception e) {
					close();
				}
			}
		}

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
		stream.mark();
		int read;
		while ((read = client.read(readBuffer)) > 0) {

			CharsetDecoder decoder = charSet.newDecoder();
			ByteBuffer dupReadBuffer = readBuffer.duplicate();
			dupReadBuffer.reset();
			decoder.decode(dupReadBuffer, stream, true);
			decoder.flush(stream);
			if (stream.position() != stream.limit())
				stream.position(stream.position() + read);
			readBuffer.mark();
		}

		if (read == -1) {
			close();
			return;
		} else {

			streamRead.reset();
		}

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

		String sStream = streamRead.subSequence(streamRead.position(),
				stream.position()).toString();
		int index = sStream.indexOf(searchString);
		if (index > -1) {
			String found = sStream.substring(0, index + searchString.length());
			int forwardPosition = index + searchString.length();
			streamRead.position(forwardPosition);
			return found;
		}
		return "";

	}

	public void close() throws Exception {
		this.closing = true;
		if (!this.writing) {
			this.closed = true;
			this.stream.clear();
			this.readBuffer.clear();
			this.streamRead.clear();
			this.client.close();
		}
	}

}
