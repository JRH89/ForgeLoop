package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerProviderPolicyTest {
    @TempDir Path directory;
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
