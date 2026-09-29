package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultShaperTest {
    private final ResultShaper shaper = new ResultShaper();

    @Test
    void cutsPagedResultsAtLineBoundariesAndKeepsContinuationHint() {
        String content = "1: " + "x".repeat(8_000) + "\n2: next\n3: last";
        String shaped = shaper.shape("read_file", ToolOutcome.ok(content), 4_000);
        assertTrue(shaped.getBytes(StandardCharsets.UTF_8).length <= 4_000);
        assertTrue(shaped.contains("continue with startLine=2"));
    }

    @Test
    void gateResultsRetainHeadTailAndDigestMarkerWithinSixteenKib() {
        String output = "HEAD\n" + "a".repeat(30_000) + "\nTAIL";
        ToolOutcome gate = ToolOutcome.ok("exitCode=1, timedOut=false\n" + output,
                Map.of("outputSha256", Hashing.sha256(output)), java.util.List.of());
        String shaped = shaper.shape("run_gate", gate, ResultShaper.MAX_RESULT_BYTES);
        assertTrue(shaped.getBytes(StandardCharsets.UTF_8).length <= ResultShaper.MAX_RESULT_BYTES);
        assertTrue(shaped.startsWith("exitCode=1, timedOut=false\nHEAD"));
        assertTrue(shaped.endsWith("TAIL"));
        assertTrue(shaped.contains("full output kept in the run journal, sha256"));
    }
}
