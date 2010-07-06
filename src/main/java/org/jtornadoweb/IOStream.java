package org.jtornadoweb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.jtornadoweb.IOLoop.EventHandler;
import org.jtornadoweb.IOStream.StreamHandler;

public class IOStream implements EventHandler {

	static interface StreamHandler {
		public void execute(String data);
	}

	private class ReadHandler implements EventHandler {

		@Override
		public void handleEvents(SelectableChannel serverChannel,
				SelectionKey key) {

		}

	}

	private class WriteHandler implements EventHandler {

		@Override
		public void handleEvents(SelectableChannel serverChannel,
				SelectionKey key) {
			// TODO Auto-generated method stub

		}

	}

	private final SocketChannel client;
	private final int maxBufferSize;
	private final int readChunckSize;
	private final ByteBuffer readBuffer;
	private final ByteBuffer writeBuffer;
	private String readDelimiter;
	private final ReadHandler readHandler;
	private final WriteHandler writeHandler;
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
		this.writeBuffer = null;
		this.readHandler = new ReadHandler();
		this.writeHandler = new WriteHandler();
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
			this.client.write(ByteBuffer.wrap(string.getBytes()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void handleEvents(SelectableChannel channel, SelectionKey key)
			throws Exception {
		try {
			if (!key.isValid())
				throw new RuntimeException("Invalid SlectionKey");

			if (key.isReadable()) {
				this.handleRead();

			}
		} finally {
			key.cancel();
		}

	}
	

	private void handleRead() throws Exception {
		client.read(readBuffer);
		Charset set = Charset.forName("UTF-8");
		CharsetDecoder decoder = set.newDecoder();
		ByteBuffer duplicate = readBuffer.duplicate();
		duplicate.flip();
		CharBuffer out = decoder.decode(duplicate);
		decoder.flush(out);
		callback.execute(out.toString());
	}

	public void close() throws Exception {
		this.client.finishConnect();
		this.client.close();
		
	}

}
