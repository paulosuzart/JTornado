import org.jtornadoweb.HttpServer
import org.jtornadoweb.Web.Application;
import org.jtornadoweb.Web.RequestHandler;

class MainHandler extends RequestHandler {

		
                void get() {
			write("worked for first time 'GET' :)\r\n")
		}

		
		void post() {
			write("worked for POST too :D\r\n")
		}

} 

server = new HttpServer(new Application().add("/", MainHandler.class), false, null, false)
server.listen(8089)

