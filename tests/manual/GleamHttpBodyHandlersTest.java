package ru.orion.client.media;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import com.sun.net.httpserver.HttpServer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
public final class GleamHttpBodyHandlersTest {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/body", exchange -> {
            int size = Integer.parseInt(exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(200,0);
            try (var body = exchange.getResponseBody()) {
                for(int i=0;i<size;i++) body.write('x');
            } catch(java.io.IOException cancelled) { }
        });
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            for(int size:new int[]{0,1,64}) {
                byte[] data=client.sendAsync(request(server,size),GleamHttpBodyHandlers.bytes(64)).get(5,TimeUnit.SECONDS).body();
                if(data.length!=size)throw new AssertionError("Wrong body size");
                for(byte b:data)if(b!='x')throw new AssertionError("Changed body content");
            }
            for(int size:new int[]{65,8192}) {
                try { client.sendAsync(request(server,size),GleamHttpBodyHandlers.bytes(64)).get(5,TimeUnit.SECONDS);throw new AssertionError("Oversize accepted"); }
                catch(ExecutionException expected) {
                    if(!expected.getCause().getMessage().contains("exceeds 64 bytes"))throw expected;
                }
            }
            try { GleamHttpBodyHandlers.bytes(-1);throw new AssertionError("Negative limit accepted"); }
            catch(IllegalArgumentException expected) { }
            System.out.println("Bounded HTTP checks passed: 6");
        } finally {server.stop(0);}
    }
    private static HttpRequest request(HttpServer server,int size) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/body?"+size)).build();
    }
}
