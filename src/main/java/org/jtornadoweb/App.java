package org.jtornadoweb;

/**
 * Hello world!
 * 
 */
public class App {
	public static void main(String[] args) throws Exception {
		HttpServer server = new HttpServer(null, false, null, null);
		server.listen(8089);
	}
}
