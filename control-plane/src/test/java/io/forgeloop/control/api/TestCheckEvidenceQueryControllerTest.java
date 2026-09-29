package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckEvidenceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCheckEvidenceQueryControllerTest {
    @Test
    void checksOrganizationAccessBeforeListingRunEvidence() {
        FeatureRunService runs = mock(FeatureRunService.class);
        TestCheckEvidenceRepository evidence = mock(TestCheckEvidenceRepository.class);
        List<TestCheckEvidence> expected = List.of(mock(TestCheckEvidence.class));
        when(evidence.findByTask_Run_IdOrderByRecordedAtAsc("run-1")).thenReturn(expected);
        TestCheckEvidenceQueryController controller = new TestCheckEvidenceQueryController(runs, evidence);

        List<TestCheckEvidence> result = controller.featureRunTestEvidence("run-1");

        org.junit.jupiter.api.Assertions.assertSame(expected, result);
        verify(runs).get("run-1");
        verify(evidence).findByTask_Run_IdOrderByRecordedAtAsc("run-1");
    }
}
