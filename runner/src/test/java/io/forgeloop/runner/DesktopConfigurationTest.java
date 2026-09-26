package io.forgeloop.runner;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DesktopConfigurationTest {
    @Test void writesPolicyWithoutProviderCredentials()throws Exception{
        var config=new DesktopConfiguration("https://forge.example","anthropic","claude-sonnet-5",new BigDecimal("2"),new BigDecimal("10"));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config.policy());
        java.nio.file.Path file=java.nio.file.Files.createTempFile("forgeloop-policy-",".json");
        java.nio.file.Files.writeString(file,json);
        var policy=RunnerProviderPolicy.load(file);
        java.nio.file.Files.delete(file);
        assertNotNull(policy);assertTrue(json.contains("inputUsdPerMillion"));assertFalse(json.contains("API_KEY"));assertEquals("ANTHROPIC_API_KEY",config.keyVariable());
    }
    @Test void validatesBeforeSaving(){assertThrows(IllegalArgumentException.class,()->new DesktopConfiguration("http://unsafe.example","anthropic","model",BigDecimal.ONE,BigDecimal.ONE));assertThrows(IllegalArgumentException.class,()->new DesktopConfiguration("https://forge.example","unknown","model",BigDecimal.ONE,BigDecimal.ONE));assertThrows(IllegalArgumentException.class,()->new DesktopConfiguration("https://forge.example","anthropic","",BigDecimal.ONE,BigDecimal.ONE));}
}
