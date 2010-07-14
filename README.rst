===========
Intro
===========
This project strongly based on `Tornado`_, a HTTP server with a simple yet powerfull webframework.

By now its an extremaly ugly chunk of code that will get some shape soon replying you what you specify in you RequestHandler methods.
We do recommend run it on linux 2.6 to take advantage of `epoll`_.

.. _`epoll`: http://linux.die.net/man/4/epoll
.. _`Tornado`: http://www.tornadoweb.org/

==============
Implementation
==============
JTornado uses a single thread to accept connections from clients using the default enviroment Selector to register its interests on the SelectableChannels
(ServerSocketChannel or SocketChannel).
Since its not possible to "fork()" the HttpServer like in python, any task - other than accepting connections - is executed in a thread pool (virtually one thread per processor). 

We should move from simple ByteBuffers to Memory Mapped.

**Changes in the implementation happens all the time in this early stage.**

==============
Sample
==============

A simple groovy script starting a server would be (myServer.groovy)::

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



And then::
  
 curl -d "user=paulo&pass=hohoho" http://localhost:8089
 curl  http://localhost:8089/?name=paulo

Will get (for each curl)::
 
 worked for POST too :D 
 name is: paulo

This sample can be found in the test folder. Try it!
