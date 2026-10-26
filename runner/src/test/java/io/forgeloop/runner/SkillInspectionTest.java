package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SkillInspectionTest {
    @Test void inspectionDoesNotNeedAnIdentityProviderCredentialOrControlPlane() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertDoesNotThrow(() -> RunnerMain.main(new String[]{"skills-inspect", "PLANNER", "example/inspection"}));
        } finally { System.setOut(original); }
        var metadata = new ObjectMapper().readTree(output.toString(StandardCharsets.UTF_8));
        assertTrue(metadata.path("enabled").isBoolean());
        assertTrue(metadata.path("skills").isArray());
    }
}
