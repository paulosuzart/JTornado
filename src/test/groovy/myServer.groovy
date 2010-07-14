import org.jtornadoweb.HttpServer
import org.jtornadoweb.Web.Application;
import org.jtornadoweb.Web.RequestHandler;

class MainHandler extends RequestHandler {

		
        void get() {
			write("worked for first time :)\r\n")
			write("name is: " + getArgument("name", "default", false) + "\r\n")
		}

		
		void post() {
			write("worked for POST too :D\r\n")
		}

} 

server = new HttpServer(new Application().add("/", MainHandler.class), false, null, false)
server.listen(8089)

