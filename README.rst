This project is intended to the Java version of Tornado web.

By now its an extremaly ugly chunk of code that will get some shape soon.

A simples groovy script starting a server would be:

::
    import org.jtornadoweb.HttpServer

    server = new HttpServer(null, false, null, null)
    server.listen(8089);

And then:
::
    groovy -cp JTornado-0.1-SNAPSHOT.jar myServer.groovy 



