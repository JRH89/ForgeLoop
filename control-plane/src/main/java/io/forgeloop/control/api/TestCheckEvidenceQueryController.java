package io.forgeloop.control.api;

import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckEvidenceRepository;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Tenant-checks a run before exposing its immutable RED/GREEN evidence. */
@Controller
public class TestCheckEvidenceQueryController {
    private final FeatureRunService runs;
    private final TestCheckEvidenceRepository evidence;

    public TestCheckEvidenceQueryController(FeatureRunService runs, TestCheckEvidenceRepository evidence) {
        this.runs = runs;
        this.evidence = evidence;
    }

    @QueryMapping
    public List<TestCheckEvidence> featureRunTestEvidence(@Argument String runId) {
        runs.get(runId);
        return evidence.findByTask_Run_IdOrderByRecordedAtAsc(runId);
    }
}
