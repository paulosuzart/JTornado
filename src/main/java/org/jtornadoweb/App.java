package org.jtornadoweb;

import org.jtornadoweb.Web.Application;
import org.jtornadoweb.Web.RequestHandler;

/**
 * Hello world!
 * 
 */
public class App {

	/**
	 * Sample Handler
	 * 
	 * 
	 */
	public static class MainHandler extends RequestHandler {

		@Override
		public void get() {
			write("name is: " + getArgument("name", "default", false) + "\r\n");
		}

		@Override
		public void post() {
			write("worked for POST too\r\n");
		}

	}

	/**
	 * Request Handler assuming field with file being uploaded (POST) with field
	 * name == filename;
	 * 
	 * 
	 */
	public static class FileHandler extends RequestHandler {
		@Override
		public void post() {
			StringBuilder sb = new StringBuilder();
			sb.append("Filename is \r\n");
			sb.append(request.files.get("filename").get("filename"));
			sb.append("\r\n");
			sb.append(request.files.get("filename").get("body"));
			write(sb.toString());
		}
	}

	public static void main(String[] args) throws Exception {

		Application application = new Application().add("/", new MainHandler())
				.add("/upload", new FileHandler());

		HttpServer server = new HttpServer(application, false, null, false);
		server.listen(8089);
	}
}
