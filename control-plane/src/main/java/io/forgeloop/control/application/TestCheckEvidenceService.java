package io.forgeloop.control.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.AttemptOutcome;
import io.forgeloop.control.domain.DeliveryTask;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revalidates lease-bound test reports, recomputes verdicts, and routes the check lifecycle transactionally. */
@Service
public class TestCheckEvidenceService {
    private static final int MAX_ARTIFACT_BYTES = 1024 * 1024;
    private static final String ARTIFACT_PREFIX = "artifact://";

    private final TaskLeaseService leases;
    private final TestCheckEvidenceRepository evidence;
    private final RunnerRepository runners;
    private final ArtifactMetadataRepository artifactMetadata;
    private final ArtifactStore artifactStore;
    private final HumanEscalationService escalations;
    private final RepairPackageRepository repairPackages;
    private final ObjectMapper mapper;

    public TestCheckEvidenceService(TaskLeaseService leases, TestCheckEvidenceRepository evidence,
                                    RunnerRepository runners, ArtifactMetadataRepository artifactMetadata,
                                    ArtifactStore artifactStore, HumanEscalationService escalations,
                                    RepairPackageRepository repairPackages, ObjectMapper mapper) {
        this.leases = leases;
        this.evidence = evidence;
        this.runners = runners;
        this.artifactMetadata = artifactMetadata;
        this.artifactStore = artifactStore;
        this.escalations = escalations;
        this.repairPackages = repairPackages;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Transactional
    public TestCheckEvidence record(String leaseId, String runnerId, String nonce, TestCheckEvidenceSubmission submission) {
        TaskLease lease = leases.requireLeaseCredentials(leaseId, runnerId, nonce);
        DeliveryTask task = lease.getTask();
        String digest = TestCheckBundle.sha256(task.getId() + "\u0000" + runnerId + "\u0000" + leaseId + "\u0000" + submission.bundleDigest());
        TestCheckEvidence prior = evidence.findByDigest(digest).orElse(null);
        if (prior != null) return prior;
        requireActiveAcknowledged(lease);

        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        ArtifactMetadata artifact = artifactMetadata.findByLeaseIdAndStorageReference(leaseId, submission.artifactReference())
                .orElseThrow(() -> new IllegalArgumentException("Test-check bundle is not attached to this lease"));
        if (!task.getId().equals(artifact.getTaskId()) || !"VERIFICATION_BUNDLE".equals(artifact.getArtifactType())
                || !"application/json".equals(artifact.getContentType()) || !submission.bundleDigest().equals(artifact.getSha256())
                || artifact.getSizeBytes() > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("Test-check bundle metadata does not match its lease");
        }
        byte[] bytes = artifactStore.getVerified(submission.artifactReference().substring(ARTIFACT_PREFIX.length()),
                submission.bundleDigest(), MAX_ARTIFACT_BYTES);
        if (bytes == null || bytes.length == 0 || bytes.length != artifact.getSizeBytes()
                || !TestCheckBundle.sha256(bytes).equals(submission.bundleDigest())) {
            throw new IllegalArgumentException("Test-check bundle could not be verified");
        }
        TestCheckBundle bundle = readBundle(bytes);
        requireRedactedIdentities(bundle);
        TestCheckRules.Decision decision = validateAndDecide(task, bundle);
        TestCheckEvidence recorded = evidence.save(new TestCheckEvidence(task, runner, leaseId, bundle, decision,
                submission.artifactReference(), submission.bundleDigest()));
        route(lease, task, recorded, decision);
        return recorded;
    }

    @Transactional(readOnly = true)
    public List<TestCheckEvidence> listForRun(String runId) {
        return evidence.findByTask_Run_IdOrderByRecordedAtAsc(runId);
    }

    private TestCheckBundle readBundle(byte[] bytes) {
        try {
            return mapper.readValue(bytes, TestCheckBundle.class);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Test-check bundle is invalid", invalid);
        }
    }

    private static TestCheckRules.Decision validateAndDecide(DeliveryTask task, TestCheckBundle bundle) {
        if (!task.getRun().isTestFirst() || task.getVerificationGate() == null
                || !"JUNIT_XML".equals(task.getTestReportFormat())) {
            throw new IllegalStateException("Task is not bound to a test-first JUnit gate");
        }
        VerificationGate gate = task.getVerificationGate();
        if (!gate.matches(bundle.gate()) || !gate.getImageDigest().equals(bundle.image())
                || !gate.getCommand().equals(bundle.command())) {
            throw new IllegalArgumentException("Test-check policy metadata does not match the run snapshot");
        }
        if ("RED_CHECK".equals(task.getRole()) && "RED".equals(bundle.checkKind())) {
            DeliveryTask testWriter = task.getWritingDependency()
                    .orElseThrow(() -> new IllegalStateException("RED check has no test writer"));
            if (!"INDEPENDENT_TEST".equals(testWriter.getRole()) || !bundle.targetSha().equals(testWriter.getChangeSha())) {
                throw new IllegalArgumentException("RED check target does not match the current test commit");
            }
            return TestCheckRules.red(task.getRun().getTestPathGlobs(), bundle.before().toRulesReport(),
                    bundle.after().toRulesReport(), bundle.changedFiles());
        }
        if ("GREEN_CHECK".equals(task.getRole()) && "GREEN".equals(bundle.checkKind())) {
            DeliveryTask integration = task.getDependencies().stream().filter(dependency -> "INTEGRATION".equals(dependency.getRole()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("GREEN check has no integration task"));
            if (integration.getChangeSha() == null || !bundle.targetSha().equals(integration.getChangeSha())) {
                throw new IllegalArgumentException("GREEN check target does not match the integration head");
            }
            List<TestCheckEvidence> redEvidence = task.getRun().currentRedEvidence();
            long testWriterCount = task.getRun().getTasks().stream().filter(candidate -> "INDEPENDENT_TEST".equals(candidate.getRole())).count();
            if (redEvidence.size() != testWriterCount || redEvidence.stream()
                    .anyMatch(red -> !integration.getDependencyChangeShas().contains(red.getTargetSha()))) {
                throw new IllegalStateException("GREEN check integration does not contain every current RED-checked commit");
            }
            Set<String> expected = new HashSet<>(task.getRun().currentRedTests());
            return TestCheckRules.green(expected, task.getRun().minimumRedTestCount(), bundle.after().toRulesReport());
        }
        throw new IllegalArgumentException("Test-check kind does not match its server-owned task role");
    }

    private static void requireRedactedIdentities(TestCheckBundle bundle) {
        java.util.stream.Stream.of(bundle.before(), bundle.after()).filter(java.util.Objects::nonNull)
                .flatMap(report -> report.outcomes().keySet().stream())
                .forEach(EvidenceSecretPolicy::requireRedacted);
        bundle.changedFiles().forEach(file -> EvidenceSecretPolicy.requireRedacted(file.path()));
    }

    private void route(TaskLease lease, DeliveryTask task, TestCheckEvidence recorded, TestCheckRules.Decision decision) {
        if (decision.verdict() == TestCheckRules.Verdict.PASS) {
            task.transition(TaskState.VERIFIED);
            lease.closeForTestCheck(AttemptOutcome.CLEAN, "COMPLETED");
            task.getRun().evaluateReviewReadiness();
            return;
        }
        if (decision.verdict() == TestCheckRules.Verdict.UNVERIFIABLE) {
            task.transition(TaskState.HELD);
            task.getRun().block();
            lease.closeForTestCheck(AttemptOutcome.STOPPED, category(task.getRole(), decision));
            escalations.escalate(task, "TEST_CHECK_UNVERIFIABLE", "Test-first check evidence is unverifiable: " + decision.reason());
            return;
        }
        if ("RED_CHECK".equals(task.getRole())) {
            routeRedFailure(lease, task, recorded, decision);
        } else {
            routeGreenFailure(lease, task, recorded, decision);
        }
    }

    private void routeRedFailure(TaskLease lease, DeliveryTask redCheck, TestCheckEvidence recorded,
                                 TestCheckRules.Decision decision) {
        DeliveryTask testWriter = redCheck.getWritingDependency()
                .orElseThrow(() -> new IllegalStateException("RED check has no test writer"));
        testWriter.transition(TaskState.REPAIR_QUEUED);
        lease.closeForTestCheck(AttemptOutcome.FINDINGS, category("RED_CHECK", decision));
        if (testWriter.getState() == TaskState.FAILED) {
            redCheck.hold();
            testWriter.getRun().block();
            escalations.escalate(testWriter, "ATTEMPT_BUDGET_EXHAUSTED", "Test writer exhausted its RED-check repair attempts");
            return;
        }
        redCheck.requeueRedCheckAfterWriterRepair();
        repairPackages.save(new RepairPackage(testWriter, "RED_FAILED:" + decision.reason(), recorded.getDigest(), decision.failingTests()));
    }

    private void routeGreenFailure(TaskLease lease, DeliveryTask greenCheck, TestCheckEvidence recorded,
                                   TestCheckRules.Decision decision) {
        lease.closeForTestCheck(AttemptOutcome.FINDINGS, category("GREEN_CHECK", decision));
        RepairPackage repair = greenCheck.getRun().scheduleQualityRepair(greenCheck,
                "GREEN_FAILED:" + decision.reason(), recorded.getDigest(), decision.failingTests());
        if (repair != null) {
            repairPackages.save(repair);
            return;
        }
        Set<String> redTests = new HashSet<>(greenCheck.getRun().currentRedTests());
        boolean onlyNewTestsRemain = !decision.failingTests().isEmpty() && redTests.containsAll(decision.failingTests());
        String reason = onlyNewTestsRemain ? "NEW_TESTS_NOT_SATISFIED" : "ATTEMPT_BUDGET_EXHAUSTED";
        escalations.escalate(greenCheck, reason, "GREEN check repair budget is exhausted: " + decision.reason());
    }

    private static void requireActiveAcknowledged(TaskLease lease) {
        if (!lease.active() || !lease.isAcknowledged()) throw new IllegalStateException("Test-check evidence requires an active acknowledged lease");
    }

    private static String category(String role, TestCheckRules.Decision decision) {
        String prefix = "RED_CHECK".equals(role) ? "RED_" : "GREEN_";
        String reason = decision.reason().name();
        return reason.startsWith(prefix) ? reason : prefix + reason;
    }
}
