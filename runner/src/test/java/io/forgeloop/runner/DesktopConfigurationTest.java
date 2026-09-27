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
    @Test void unknownPublicPriceRemainsUnpricedWithoutBlockingRunnerSetup()throws Exception{
        var config=new DesktopConfiguration("https://forge.example","openai","custom-model",null,null,false,null,null);
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config.policy());
        assertFalse(json.contains("inputUsdPerMillion"));
        var file=java.nio.file.Files.createTempFile("forgeloop-unpriced-",".json");
        try{java.nio.file.Files.writeString(file,json);assertNull(RunnerProviderPolicy.load(file).select("BACKEND").inputUsdPerMillion());}
        finally{java.nio.file.Files.deleteIfExists(file);}
        assertThrows(IllegalArgumentException.class,()->new DesktopConfiguration("https://forge.example","openai","custom-model",BigDecimal.ONE,null));
    }
    @Test void existingDesktopConfigurationWithoutPriceMetadataStillLoads()throws Exception{
        String oldJson="""
            {"endpoint":"https://forge.example","provider":"anthropic","model":"claude-sonnet-5",
             "inputUsdPerMillion":2,"outputUsdPerMillion":10,"startAtLogin":false}
            """;
        var config=new com.fasterxml.jackson.databind.ObjectMapper().readValue(oldJson,DesktopConfiguration.class);
        assertNull(config.priceSource());
        assertEquals(new BigDecimal("2"),config.inputUsdPerMillion());
    }
}
