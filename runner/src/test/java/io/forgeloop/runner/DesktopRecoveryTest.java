package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real child JVM + disposable HTTP server: recovery never needs a model account or live runner. */
class DesktopRecoveryTest {
    @TempDir Path directory;
    @Test void reconnectsAfterServerRestartAndCanPauseWhileOffline() throws Exception {
        List<String> logs=new CopyOnWriteArrayList<>();HttpServer server=server(0,200);
        int port=server.getAddress().getPort();DesktopWorker worker=start(port,logs);
        try {
            await(()->worker.status().phase()==WorkerStatus.Phase.IDLE);
            long heartbeat=worker.status().lastContactMillis();server.stop(0);
            await(()->worker.status().phase()==WorkerStatus.Phase.RECONNECTING);
            assertTrue(worker.running());assertTrue(worker.status().lastContactMillis()>=heartbeat);
            server=server(port,200);
            await(()->worker.status().phase()==WorkerStatus.Phase.IDLE);
            assertTrue(worker.status().lastContactMillis()>heartbeat);
            await(()->logs.contains("Control-plane connection restored."));
            server.stop(0);await(()->worker.status().phase()==WorkerStatus.Phase.RECONNECTING);
            worker.pause();await(()->!worker.running());
            assertEquals(WorkerStatus.Phase.STOPPED,worker.status().phase());
        } finally {worker.pause();server.stop(0);await(()->!worker.running());}
    }
    @Test void rejectedCredentialsStopInsteadOfRetryingForever() throws Exception {
        List<String> logs=new CopyOnWriteArrayList<>();HttpServer server=server(0,403);
        DesktopWorker worker=start(server.getAddress().getPort(),logs);
        try {
            await(()->!worker.running());assertEquals(WorkerStatus.Phase.ATTENTION,worker.status().phase());
            assertFalse(String.join("\n",logs).contains("secret-response-body"));
            assertFalse(String.join("\n",logs).contains("fake-provider-key"));
        } finally {worker.pause();server.stop(0);}
    }
    private DesktopWorker start(int port,List<String> logs)throws Exception {
        new RunnerIdentityStore().save(directory.resolve("identity"),new RunnerIdentity("test","fake-runner-credential"));
        var worker=new DesktopWorker(directory);
        worker.start(new DesktopConfiguration("http://127.0.0.1:"+port,"anthropic","unused-test-model",BigDecimal.ONE,BigDecimal.ONE),"fake-provider-key",logs::add);
        return worker;
    }
    private HttpServer server(int port,int code)throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/graphql",exchange->{
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            // Only idle polling is served; no task grants or provider endpoints exist in this fixture.
            String body=code!=200?"secret-response-body":request.contains("availableRunnerTasks")?"{\"data\":{\"availableRunnerTasks\":[]}}":"{\"data\":{\"runnerHeartbeat\":{\"id\":\"test\"}}}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });server.start();return server;
    }
    private static void await(BooleanSupplier condition)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertTrue(condition.getAsBoolean(),"Expected worker state within 25 seconds");
    }
}
