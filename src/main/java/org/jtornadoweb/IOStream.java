package org.jtornadoweb;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.jtornadoweb.IOLoop.EventHandler;

public class IOStream implements EventHandler {

	static interface StreamHandler {
		public void execute(String data);
	}

	private final SocketChannel client;
	private final int maxBufferSize;
	private final int readChunckSize;
	private final ByteBuffer readBuffer;
	private final ByteBuffer writeBuffer;
	private String readDelimiter;

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
		this.readBuffer = MappedByteBuffer.allocate(readChunckSize);
		this.writeBuffer = ByteBuffer.allocate(readChunckSize);
	}

	public void readUntil(String delimiter, StreamHandler streamHandler)
			throws Exception {
		String sStream = readBuffer.asCharBuffer().flip().toString();
		int loc = sStream.indexOf(delimiter);
		if (loc != -1) {
			streamHandler.execute(sStream);
			return;
		}
		checkClosed();
		this.delimiter = delimiter;
		this.callback = streamHandler;
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
			this.client.write(writeBuffer);
			writeBuffer.clear();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvents(SelectionKey key) throws Exception {
		// if (!key.isValid()) {
		// throw new Exception("Invlid Key");
		// }

		// if (key.isReadable()) {
		// key.cancel();
		// loop.removeHandler(key);
		this.handleRead();
		// }

	}

	/**
	 * If this code is being executed, the SO guarantees that there is at least
	 * one byte to read. The channel is queried in slots of 4096bytes.
	 * 
	 * @throws Exception
	 */
	private void handleRead() throws Exception {

		int read = client.read(readBuffer);
		StringBuffer sb = new StringBuffer(read);
		CharBuffer out = CharBuffer.allocate(readChunckSize);
		do {
			Charset set = Charset.forName("UTF-8");
			CharsetDecoder decoder = set.newDecoder();
			readBuffer.flip();
			decoder.decode(readBuffer, out, true);
			//decoder.flush(out);
			sb.append(out.flip().toString());
			readBuffer.clear();
			out.clear();
		} while (client.read(readBuffer) > 0);

		callback.execute(sb.toString());
	}

	public void close() throws Exception {
		this.client.finishConnect();
		this.client.close();

	}

}
