package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerProviderPolicyTest {
    @TempDir Path directory;
    @Test void parsesPricesAndRejectsPartialNegativeOrSecretFields() throws Exception {
        Path policy=directory.resolve("priced.json");
        String base="{\"default\":{\"provider\":\"anthropic\",\"model\":\"model\",\"maxAttempts\":2%s}}";
        Files.writeString(policy,base.formatted(",\"inputUsdPerMillion\":3,\"outputUsdPerMillion\":15"));
        assertEquals(new java.math.BigDecimal("3"),RunnerProviderPolicy.load(policy).select("PLANNER").inputUsdPerMillion());
        for(String fields:java.util.List.of(",\"inputUsdPerMillion\":3",",\"inputUsdPerMillion\":-1,\"outputUsdPerMillion\":15",",\"key\":\"secret\",\"extra\":1")) {
            Files.writeString(policy,base.formatted(fields));
            assertThrows(IllegalArgumentException.class,()->RunnerProviderPolicy.load(policy));
        }
    }
    @Test void selectsProvidersDeterministicallyByRole() throws Exception {
        Path policy = directory.resolve("providers.json");
        Files.writeString(policy, "{\"IMPLEMENTATION\":{\"provider\":\"anthropic\",\"model\":\"claude\",\"maxAttempts\":2},\"default\":{\"provider\":\"local\",\"model\":\"qwen\",\"maxAttempts\":1}}");
        RunnerProviderPolicy loaded = RunnerProviderPolicy.load(policy);
        assertEquals("anthropic", loaded.select("IMPLEMENTATION").provider());
        assertEquals("local", loaded.select("REPAIR").provider());
    }
    @Test void rejectsUnknownPolicyShapes() throws Exception {
        Path policy = directory.resolve("bad.json"); Files.writeString(policy, "{\"IMPLEMENTATION\":{\"provider\":\"anthropic\",\"model\":\"claude\",\"maxAttempts\":2,\"key\":\"secret\"}}");
        assertThrows(IllegalArgumentException.class, () -> RunnerProviderPolicy.load(policy));
    }
}
