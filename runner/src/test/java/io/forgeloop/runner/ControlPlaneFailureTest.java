package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ControlPlaneFailureTest {
    @Test void distinguishesRetryableErrorsWithoutLoggingResponseBodies() throws Exception {
        for(int code:new int[]{401,403,400,408,429,503}) {
            var failure=call(code,"secret-response-body");
            assertEquals(code==408||code==429||code==503,failure.retryable());
            assertFalse(failure.getMessage().contains("secret-response-body"));
        }
        assertFalse(call(200,"{\"errors\":[{\"message\":\"secret-response-body\",\"extensions\":{\"classification\":\"FORBIDDEN\"}}]}").retryable());
        assertTrue(call(200,"{\"errors\":[{\"extensions\":{\"classification\":\"INTERNAL_ERROR\"}}]}").retryable());
        assertTrue(call(200,"<html>secret-response-body</html>").retryable());
    }
    private ControlPlaneFailure call(int code,String body)throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/graphql",exchange->{exchange.getRequestBody().readAllBytes();byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});
        server.start();
        try{return assertThrows(ControlPlaneFailure.class,()->new RunnerClient(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort())).heartbeat(new RunnerIdentity("test","fake-credential")));}
        finally{server.stop(0);}
    }
}
