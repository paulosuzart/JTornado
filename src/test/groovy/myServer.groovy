import org.jtornadoweb.HttpServer
import org.jtornadoweb.Web.Application;
import org.jtornadoweb.Web.RequestHandler;

class MainHandler extends RequestHandler {

		def get() {
			write("worked for first time 'GET' :)\r\n")
		}

		
		def post() {
			write("worked for POST too :D\r\n")
		}

} 

server = new HttpServer(new Application().add("/", MainHandler.class), false, null, false)
server.listen(8089)

