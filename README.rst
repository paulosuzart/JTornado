This project is intended to be the Java version of Tornado web.

By now its an extremaly ugly chunk of code that will get some shape soon replying you HTTP request in a 1k/s rate.

A simples groovy script starting a server would be (myServer.groovy):
::
    import org.jtornadoweb.HttpServer

    server = new HttpServer(null, false, null, null)
    server.listen(8089)

And then:
::
    groovy -cp JTornado-0.1-SNAPSHOT.jar myServer.groovy 

Access http://localhost:8980 and you get something like:
::
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

