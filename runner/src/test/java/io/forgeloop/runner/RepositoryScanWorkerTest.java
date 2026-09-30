package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RepositoryScanWorkerTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String CONTEXT = "Repository manifest:\n- src/Validator.java\n\n--- src/Validator.java ---\nReject empty input.\n";
    private static final String FINDING = "{\"findings\":[{\"severity\":\"HIGH\",\"title\":\"Reject empty input\",\"description\":\"Empty values pass validation.\",\"impact\":\"Invalid records enter the workflow.\",\"evidence\":\"Validator returns success on an empty string.\",\"affectedFiles\":[\"src/Validator.java\"],\"acceptanceCriteria\":[\"Add a test proving empty values are rejected.\"]}]}";
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 1,
            java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);

    @Test void acceptsOnlyEvidenceBackedManifestPathsAndTracksUsage() throws Exception {
        ProviderClient provider = ignored -> new ProviderResult(FINDING, 15, 10, "request-1");
        RepositoryScanResult result = new RepositoryScanWorker().execute(policy, provider, CONTEXT, COMMIT, "scan-1");
        assertEquals(COMMIT, result.commitSha());
        assertEquals("Reject empty input", result.findings().getFirst().title());
        assertEquals("src/Validator.java", result.findings().getFirst().affectedFiles().getFirst());
        assertEquals(25, result.usage().estimatedCostMicros());
    }

    @Test void rejectsHallucinatedPathsAndMalformedOutput() {
        ProviderClient hallucinated = ignored -> new ProviderResult(FINDING.replace("src/Validator.java", "src/Missing.java"), 2, 1, "request-2");
        assertThrows(GuardedPatchFailure.class, () -> new RepositoryScanWorker().execute(policy, hallucinated, CONTEXT, COMMIT, "scan-2"));
        ProviderClient malformed = ignored -> new ProviderResult("not json", 2, 1, "request-3");
        assertThrows(GuardedPatchFailure.class, () -> new RepositoryScanWorker().execute(policy, malformed, CONTEXT, COMMIT, "scan-3"));
    }

    @Test void keepsLocalOutputBoundsAfterProviderSchemaIsMadePortable() throws Exception {
        assertRejected(13, 1, 1);
        assertRejected(1, 11, 1);
        assertRejected(1, 1, 7);
    }

    private void assertRejected(int findingCount, int fileCount, int criterionCount) throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode template = json.readTree(FINDING).path("findings").get(0);
        ArrayNode findings = json.createArrayNode();
        for (int findingIndex = 0; findingIndex < findingCount; findingIndex++) {
            ObjectNode finding = (ObjectNode) template.deepCopy();
            ArrayNode files = json.createArrayNode();
            for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) files.add("src/Validator.java");
            ArrayNode criteria = json.createArrayNode();
            for (int criterionIndex = 0; criterionIndex < criterionCount; criterionIndex++) criteria.add("Check case " + criterionIndex);
            finding.set("affectedFiles", files);
            finding.set("acceptanceCriteria", criteria);
            findings.add(finding);
        }
        ObjectNode root = json.createObjectNode();
        root.set("findings", findings);
        String output = json.writeValueAsString(root);
        ProviderClient provider = ignored -> new ProviderResult(output, 5, 3, "bounded-scan");

        assertThrows(GuardedPatchFailure.class,
                () -> new RepositoryScanWorker().execute(policy, provider, CONTEXT, COMMIT, "bounded-scan"));
    }
}
