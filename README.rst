===========
Intro
===========
This project is intended to be the Java version of Tornado web.

By now its an extremaly ugly chunk of code that will get some shape soon replying you HTTP request in a 1k/s rate.
We do recommend run it on linux 2.6 to take advantage of `epoll`_.

.. _`epoll`: http://linux.die.net/man/4/epoll

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

  server = new HttpServer(null, false, null, null)
  server.listen(8089)

And then::
  
  groovy -cp JTornado-0.1-SNAPSHOT.jar myServer.groovy 

Access http://localhost:8089 and you get something like::
  
 Hello
  GET / HTTP/1.1
  Host: localhost:8089
  User-Agent: Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.6) Gecko/20100628 Ubuntu/10.04 (lucid) Firefox/3.6.6
  Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
  Accept-Language: en-us,en;q=0.5
  Accept-Encoding: gzip,deflate
  Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7
  Keep-Alive: 115
  Connection: keep-alive
  Cookie: user=bmFtZQ==|1278356972|f9d8cfe86a9716e838b25fd9d5e56834bacba78b
  Cache-Control: max-age=0

This sample can be found in the test folder. Try it!
