package io.forgeloop.runner;
import java.nio.file.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class DesktopDiagnosticsTest {
    @TempDir Path directory;
    @Test void diagnosticsExportOnlyAllowlistedState()throws Exception{
        Files.writeString(directory.resolve("identity"),"sensitive-id\nsensitive-token");
        var config=new DesktopConfiguration("https://private.example.com","anthropic","private-model",BigDecimal.ONE,BigDecimal.ONE);
        String result=DesktopDiagnostics.summary(directory,config,false);
        assertTrue(result.contains("Connection saved: true"));
        assertTrue(result.contains("Worker running: false"));
        for(String secret:new String[]{"sensitive-id","sensitive-token","private.example.com","private-model",directory.toString()})assertFalse(result.contains(secret));
    }
    @Test void workerRedactsProviderAndEnrollmentCredentials(){
        assertEquals("[REDACTED] [REDACTED]",DesktopWorker.redact("provider-secret enrollment-secret","provider-secret","enrollment-secret"));
    }
}
