package io.forgeloop.runner;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopWorkerTest {
    @TempDir Path directory;
    @Test void launchesRealChildAndPausesWithoutProviderCalls()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger requests=new AtomicInteger();
        server.createContext("/graphql",exchange->{String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.incrementAndGet();String response=request.contains("availableRunnerTasks")?"{\"data\":{\"availableRunnerTasks\":[]}}":"{\"data\":{\"runnerHeartbeat\":{\"id\":\"test\"}}}";byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var body=exchange.getResponseBody()){body.write(bytes);}});
        server.start();DesktopWorker worker=new DesktopWorker(directory);
        try {
            new RunnerIdentityStore().save(directory.resolve("identity"),new RunnerIdentity("test","fake-runner-credential"));
            var config=new DesktopConfiguration("http://127.0.0.1:"+server.getAddress().getPort(),"anthropic","unused-test-model",BigDecimal.ONE,BigDecimal.ONE);
            worker.start(config,"fake-provider-key",line->{});
            assertThrows(IllegalStateException.class,()->worker.start(config,"fake-provider-key",line->{}));
            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(15).toNanos();
            while(requests.get()<2&&System.nanoTime()<deadline)Thread.sleep(50);
            assertTrue(requests.get()>=2,"Child should heartbeat and request eligible tasks");
            worker.pause();deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
            while(worker.running()&&System.nanoTime()<deadline)Thread.sleep(50);
            assertFalse(worker.running(),"Idle child should honor the graceful pause marker");
            int before=requests.get();
            worker.start(config,"fake-provider-key",line->{});
            deadline=System.nanoTime()+java.time.Duration.ofSeconds(15).toNanos();
            while(requests.get()<before+2&&System.nanoTime()<deadline)Thread.sleep(50);
            assertTrue(requests.get()>=before+2,"Restart must remove the old pause marker and poll again");
            worker.pause();deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
            while(worker.running()&&System.nanoTime()<deadline)Thread.sleep(50);
            assertFalse(worker.running());
        } finally {worker.pause();server.stop(0);}
    }
}
