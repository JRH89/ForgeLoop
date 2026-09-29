package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.RepairPackage;
import io.forgeloop.control.domain.RepairPackageRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.RunnerRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckEvidenceRepository;
import io.forgeloop.control.domain.TestCheckRules;
import io.forgeloop.control.domain.VerificationGate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TestCheckEvidenceServiceTest {
    private static final String LEASE_ID = "lease-1";
    private static final String RUNNER_ID = "runner-1";
    private static final String ARTIFACT_REFERENCE = "artifact://org/run/task/lease/test-check.json";
    private static final String RED_SHA = "b".repeat(40);
    private static final String RED_TEST = "Suite#newBehavior";

    @Test
    void recordsServerVerifiedGreenEvidenceAndReplaysAfterLeaseClosure() throws Exception {
        Fixture fixture = fixture(greenBundle(TestCheckRules.Outcome.PASSED));

        TestCheckEvidence recorded = fixture.service().record(LEASE_ID, RUNNER_ID, "nonce", fixture.submission());
        TestCheckEvidence replayed = fixture.service().record(LEASE_ID, RUNNER_ID, "nonce", fixture.submission());

        assertSame(recorded, replayed, "a lost mutation response replays the same evidence after lease closure");
        verify(fixture.check()).transition(TaskState.VERIFIED);
        verify(fixture.lease()).closeForTestCheck();
        verify(fixture.run()).evaluateReviewReadiness();
    }

    @Test
    void failedRedCheckRequeuesItsTestWriterAndCarriesTheOffendingIdentity() throws Exception {
        Fixture fixture = fixture(redBundle(TestCheckRules.Outcome.PASSED));

        fixture.service().record(LEASE_ID, RUNNER_ID, "nonce", fixture.submission());

        verify(fixture.writer()).transition(TaskState.REPAIR_QUEUED);
        verify(fixture.check()).requeueRedCheckAfterWriterRepair();
        verify(fixture.lease()).closeForTestCheck();
        ArgumentCaptor<RepairPackage> repair = ArgumentCaptor.forClass(RepairPackage.class);
        verify(fixture.repairs()).save(repair.capture());
        assertEquals(List.of(RED_TEST), repair.getValue().getFailingTests());
    }

    @Test
    void unverifiableCheckIsHeldAndEscalatedInsteadOfBeingTreatedAsPass() throws Exception {
        Fixture fixture = fixture(greenBundle(TestCheckRules.Outcome.PASSED, TestCheckRules.ReportStatus.UNREADABLE));

        fixture.service().record(LEASE_ID, RUNNER_ID, "nonce", fixture.submission());

        verify(fixture.check()).transition(TaskState.HELD);
        verify(fixture.run()).block();
        verify(fixture.lease()).closeForTestCheck();
        verify(fixture.escalations()).escalate(fixture.check(), "TEST_CHECK_UNVERIFIABLE",
                "Test-first check evidence is unverifiable: REPORT_UNREADABLE");
    }

    @Test
    void failedGreenCheckSchedulesARepairWithTheFailingRedIdentity() throws Exception {
        Fixture fixture = fixture(greenBundle(TestCheckRules.Outcome.FAILED));
        RepairPackage repair = mock(RepairPackage.class);
        when(fixture.run().scheduleQualityRepair(eq(fixture.check()), eq("GREEN_FAILED:EXPECTED_TEST_NOT_PASSED"),
                anyString(), eq(List.of(RED_TEST)))).thenReturn(repair);

        fixture.service().record(LEASE_ID, RUNNER_ID, "nonce", fixture.submission());

        verify(fixture.run()).scheduleQualityRepair(eq(fixture.check()), eq("GREEN_FAILED:EXPECTED_TEST_NOT_PASSED"),
                anyString(), eq(List.of(RED_TEST)));
        verify(fixture.repairs()).save(repair);
        verify(fixture.lease()).closeForTestCheck();
    }

    private Fixture fixture(TestCheckBundle bundle) throws Exception {
        byte[] bytes = mapper().writeValueAsBytes(bundle);
        String digest = TestCheckBundle.sha256(bytes);
        TaskLeaseService leases = mock(TaskLeaseService.class);
        TestCheckEvidenceRepository evidence = mock(TestCheckEvidenceRepository.class);
        RunnerRepository runners = mock(RunnerRepository.class);
        ArtifactMetadataRepository metadata = mock(ArtifactMetadataRepository.class);
        ArtifactStore store = mock(ArtifactStore.class);
        HumanEscalationService escalations = mock(HumanEscalationService.class);
        RepairPackageRepository repairs = mock(RepairPackageRepository.class);
        TaskLease lease = mock(TaskLease.class);
        DeliveryTask check = mock(DeliveryTask.class);
        FeatureRun run = mock(FeatureRun.class);
        VerificationGate gate = mock(VerificationGate.class);
        DeliveryTask writer = mock(DeliveryTask.class);
        Runner runner = mock(Runner.class);
        ArtifactMetadata artifact = mock(ArtifactMetadata.class);
        when(leases.requireLeaseCredentials(LEASE_ID, RUNNER_ID, "nonce")).thenReturn(lease);
        when(lease.getTask()).thenReturn(check);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task-1");
        when(check.getId()).thenReturn("task-1");
        when(check.getRole()).thenReturn("RED".equals(bundle.checkKind()) ? "RED_CHECK" : "GREEN_CHECK");
        when(check.getRun()).thenReturn(run);
        when(check.getVerificationGate()).thenReturn(gate);
        when(check.getTestReportFormat()).thenReturn("JUNIT_XML");
        when(run.isTestFirst()).thenReturn(true);
        when(gate.matches(bundle.gate())).thenReturn(true);
        when(gate.getImageDigest()).thenReturn(bundle.image());
        when(gate.getCommand()).thenReturn(bundle.command());
        when(gate.getName()).thenReturn(bundle.gate());
        when(runners.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
        when(runner.getId()).thenReturn(RUNNER_ID);
        when(metadata.findByLeaseIdAndStorageReference(LEASE_ID, ARTIFACT_REFERENCE)).thenReturn(Optional.of(artifact));
        when(artifact.getTaskId()).thenReturn("task-1");
        when(artifact.getArtifactType()).thenReturn("VERIFICATION_BUNDLE");
        when(artifact.getContentType()).thenReturn("application/json");
        when(artifact.getSha256()).thenReturn(digest);
        when(artifact.getSizeBytes()).thenReturn((long) bytes.length);
        when(store.getVerified(anyString(), anyString(), anyLong())).thenReturn(bytes);
        AtomicReference<TestCheckEvidence> saved = new AtomicReference<>();
        when(evidence.findByDigest(anyString())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(evidence.save(any(TestCheckEvidence.class))).thenAnswer(invocation -> {
            TestCheckEvidence recorded = invocation.getArgument(0);
            saved.set(recorded);
            return recorded;
        });

        if ("RED".equals(bundle.checkKind())) {
            when(check.getWritingDependency()).thenReturn(Optional.of(writer));
            when(writer.getRole()).thenReturn("INDEPENDENT_TEST");
            when(writer.getChangeSha()).thenReturn(bundle.targetSha());
            when(writer.getAttempts()).thenReturn(0);
            when(writer.getOwnedPaths()).thenReturn(List.of("tests"));
            when(writer.getRun()).thenReturn(run);
            when(run.getTestPathGlobs()).thenReturn(List.of("tests/**"));
            when(run.getCriteria()).thenReturn(List.of());
        } else {
            DeliveryTask integration = mock(DeliveryTask.class);
            TestCheckEvidence redEvidence = mock(TestCheckEvidence.class);
            when(check.getDependencies()).thenReturn(List.of(integration));
            when(integration.getRole()).thenReturn("INTEGRATION");
            when(integration.getChangeSha()).thenReturn(bundle.targetSha());
            when(integration.getDependencyChangeShas()).thenReturn(List.of(RED_SHA));
            when(run.getTasks()).thenReturn(List.of(writer));
            when(writer.getRole()).thenReturn("INDEPENDENT_TEST");
            when(redEvidence.getTargetSha()).thenReturn(RED_SHA);
            when(run.currentRedEvidence()).thenReturn(List.of(redEvidence));
            when(run.currentRedTests()).thenReturn(List.of(RED_TEST));
            when(run.minimumRedTestCount()).thenReturn(1);
        }

        TestCheckEvidenceService service = new TestCheckEvidenceService(leases, evidence, runners, metadata, store,
                escalations, repairs, mapper());
        return new Fixture(service, check, lease, run, writer, repairs, escalations,
                new TestCheckEvidenceSubmission(ARTIFACT_REFERENCE, digest));
    }

    private static TestCheckBundle greenBundle(TestCheckRules.Outcome outcome) {
        return greenBundle(outcome, TestCheckRules.ReportStatus.READ);
    }

    private static TestCheckBundle greenBundle(TestCheckRules.Outcome outcome, TestCheckRules.ReportStatus status) {
        Map<String, TestCheckRules.Outcome> outcomes = status == TestCheckRules.ReportStatus.READ
                ? Map.of(RED_TEST, outcome) : Map.of();
        int exitCode = status != TestCheckRules.ReportStatus.READ || outcome == TestCheckRules.Outcome.PASSED ? 0 : 1;
        var report = report(status, exitCode, outcomes);
        return new TestCheckBundle("GREEN", "unit", "runner@sha256:" + "c".repeat(64), List.of("./gradlew", "test"),
                "a".repeat(40), null, null, report, List.of());
    }

    private static TestCheckBundle redBundle(TestCheckRules.Outcome afterOutcome) {
        var before = report(TestCheckRules.ReportStatus.READ, 0, Map.of("Suite#existing", TestCheckRules.Outcome.PASSED));
        var afterOutcomes = Map.of("Suite#existing", TestCheckRules.Outcome.PASSED, RED_TEST, afterOutcome);
        int exitCode = afterOutcome == TestCheckRules.Outcome.FAILED || afterOutcome == TestCheckRules.Outcome.ERROR ? 1 : 0;
        var after = report(TestCheckRules.ReportStatus.READ, exitCode, afterOutcomes);
        return new TestCheckBundle("RED", "unit", "runner@sha256:" + "c".repeat(64), List.of("./gradlew", "test"),
                "c".repeat(40), "b".repeat(40), before, after,
                List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", "d".repeat(40))));
    }

    private static TestCheckBundle.TestCheckRunBundle report(TestCheckRules.ReportStatus status, int exitCode,
                                                              Map<String, TestCheckRules.Outcome> outcomes) {
        Instant now = Instant.parse("2026-09-28T20:00:00Z");
        return new TestCheckBundle.TestCheckRunBundle(status, exitCode, false, outcomes,
                TestCheckBundle.sha256("safe output"), TestCheckBundle.outcomeDigest(outcomes), now, now.plusSeconds(1));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private record Fixture(TestCheckEvidenceService service, DeliveryTask check, TaskLease lease, FeatureRun run,
                           DeliveryTask writer, RepairPackageRepository repairs, HumanEscalationService escalations,
                           TestCheckEvidenceSubmission submission) { }
}
