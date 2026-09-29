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

    @Test void toolCallingIsOptionalWithVendorDefaultsAndExplicitOverrides() throws Exception {
        Path policy = directory.resolve("tool-calling.json");
        Files.writeString(policy, "{\"IMPLEMENTATION\":{\"provider\":\"local\",\"model\":\"qwen\",\"maxAttempts\":1},"
                + "\"PLANNER\":{\"provider\":\"anthropic\",\"model\":\"claude\",\"maxAttempts\":2,\"toolCalling\":false},"
                + "\"REPAIR\":{\"provider\":\"local\",\"model\":\"qwen\",\"maxAttempts\":1,\"toolCalling\":true},"
                + "\"default\":{\"provider\":\"openai\",\"model\":\"gpt\",\"maxAttempts\":1,\"inputUsdPerMillion\":3,\"outputUsdPerMillion\":15,\"toolCalling\":true}}");
        RunnerProviderPolicy loaded = RunnerProviderPolicy.load(policy);
        assertEquals(false, loaded.toolCalling("IMPLEMENTATION"));
        assertEquals(false, loaded.toolCalling("PLANNER"));
        assertEquals(true, loaded.toolCalling("REPAIR"));
        assertEquals(true, loaded.toolCalling("REPOSITORY_SCAN"));
    }

    @Test void rejectsNonBooleanToolCallingAndStillAcceptsLegacyThreeAndFiveFieldPolicies() throws Exception {
        Path policy = directory.resolve("compatibility.json");
        Files.writeString(policy, "{\"default\":{\"provider\":\"gemini\",\"model\":\"gemini\",\"maxAttempts\":1}}");
        RunnerProviderPolicy legacy = RunnerProviderPolicy.load(policy);
        assertEquals(true, legacy.toolCalling("PLANNER"));
        Files.writeString(policy, "{\"default\":{\"provider\":\"local\",\"model\":\"qwen\",\"maxAttempts\":1,\"inputUsdPerMillion\":0,\"outputUsdPerMillion\":0}}");
        assertEquals(false, RunnerProviderPolicy.load(policy).toolCalling("PLANNER"));
        Files.writeString(policy, "{\"default\":{\"provider\":\"openai\",\"model\":\"gpt\",\"maxAttempts\":1,\"toolCalling\":\"yes\"}}");
        assertThrows(IllegalArgumentException.class, () -> RunnerProviderPolicy.load(policy));
    }
}
