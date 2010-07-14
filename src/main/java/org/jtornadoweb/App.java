package org.jtornadoweb;

import java.math.BigDecimal;

import org.jtornadoweb.Web.Application;
import org.jtornadoweb.Web.RequestHandler;

/**
 * Hello world!
 * 
 */
public class App {

	public static class MainHandler extends RequestHandler {

		@Override
		protected void get() {
			write("worked for 2first time :)\r\n");
			write("name is: " + getArgument("name", "default", false) + "\r\n");
		}

		@Override
		protected void post() {
			write("worked for POST too\r\n");
		}

	}

	public static void main(String[] args) throws Exception {
		Application application = new Application().add("/", MainHandler.class);

		HttpServer server = new HttpServer(application, false, null, false);
		server.listen(8089);
	}
}
