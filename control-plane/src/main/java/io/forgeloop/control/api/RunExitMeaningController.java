package io.forgeloop.control.api;

import io.forgeloop.control.application.RunExitMeaning;
import io.forgeloop.control.application.RunExitMeaningService;
import io.forgeloop.control.domain.AttemptOutcome;
import io.forgeloop.control.domain.FeatureRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

/** Resolves exit fields in batches so run tables do not issue one query per row. */
@Controller
public class RunExitMeaningController {
    private final RunExitMeaningService meanings;

    public RunExitMeaningController(RunExitMeaningService meanings) { this.meanings = meanings; }

    @BatchMapping(typeName = "FeatureRun", field = "exitMeaning")
    public Map<FeatureRun, AttemptOutcome> exitMeaning(List<FeatureRun> runs) {
        Map<String, RunExitMeaning> derived = meanings.derive(runs);
        Map<FeatureRun, AttemptOutcome> result = new LinkedHashMap<>();
        runs.forEach(run -> {
            RunExitMeaning meaning = derived.get(run.getId());
            result.put(run, meaning == null ? null : meaning.outcome());
        });
        return result;
    }

    @BatchMapping(typeName = "FeatureRun", field = "exitReason")
    public Map<FeatureRun, String> exitReason(List<FeatureRun> runs) {
        Map<String, RunExitMeaning> derived = meanings.derive(runs);
        Map<FeatureRun, String> result = new LinkedHashMap<>();
        runs.forEach(run -> {
            RunExitMeaning meaning = derived.get(run.getId());
            result.put(run, meaning == null ? null : meaning.reason());
        });
        return result;
    }
}
