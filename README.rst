===========
Intro
===========
.. image:: http://github.com/paulosuzart/JTornado/raw/master/img/logo.png 

This project is strongly based on `Tornado`_, a non-blocking HTTP server with a simple yet powerfull webframework.

We do recommend run it on linux 2.6 to take advantage of `epoll`_.

.. _`epoll`: http://linux.die.net/man/4/epoll
.. _`Tornado`: http://www.tornadoweb.org/

==============
Implementation
==============
JTornado uses a single thread to accept connections from clients, and uses the default enviroment Selector to register its interests on the SelectableChannels
(ServerSocketChannel or SocketChannel).
Since its not possible to "fork()" the HttpServer like in python, any task - other than accepting connections - is executed in a thread pool (virtually one thread per processor). It means that threads execute Http parse until an IO operation is need. If so, the thread simply register the operation interest and become free for processing another event. The same happens to writes.

In JTornado, threads never wait for IO complete, it is designed to keep its threads busy doing what an HTTP Server should do, serve HTTP requests rather than waiting for IO.


**Changes in the implementation happens all the time in this early stage (Alpha).**

===============
Sample (Groovy)
===============

A simple groovy script starting a server would be (myServer.groovy)::

 import org.jtornadoweb.HttpServer
 import org.jtornadoweb.Web.Application;
 import org.jtornadoweb.Web.RequestHandler;

 class MainHandler extends RequestHandler {

        void get() {
		write("name is: " + getArgument("name", "default", false) + "\r\n")
	}


        void post() {
                write("worked for POST too :D\r\n")
        }

 }

 server = new HttpServer(new Application().add("/", MainHandler.class), false, null, false)
 server.listen(8089)



And then::
  
 curl -d "user=paulo&pass=hohoho" http://localhost:8089
 curl  http://localhost:8089/?name=paulo

Will get (for each curl)::
 
 worked for POST too :D 
 name is: paulo

This sample can be found in the test folder. Try it!

==========================================
Simple (Java) FileHandler for file upload
==========================================
File upload handler::

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
  
Try::

  curl -v -F filename=@./pom.xml http://localhost:8089/upload

And you'll get the file name and the file content in response.

