package io.forgeloop.runner;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressProviderClientTest {
    @Test void recordsMetadataOnlyAndPreservesProviderResult() throws Exception {
        var received=new ArrayList<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/runner/events",exchange->{
            received.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var client=new RunnerClient(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            var identity=new RunnerIdentity("runner","credential");
            var lease=new RunnerLease("lease","nonce");
            var reporter=new RunnerEventReporter(client,identity,lease);
            var result=new ProviderResult("private-response",12,3,"request");
            assertEquals(result,new ProgressProviderClient(request->result,reporter).execute(new ProviderRequest("model","private-instructions","private-source",128)));
            assertEquals(2,received.size());
            assertTrue(received.get(0).contains("PROVIDER_STARTED"));assertTrue(received.get(1).contains("PROVIDER_COMPLETED"));
            assertFalse(received.toString().contains("private-"));
        } finally {server.stop(0);}
    }
}
