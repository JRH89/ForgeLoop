package io.forgeloop.control.domain;

import io.forgeloop.control.application.TestCheckBundle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/** Append-only, checksummed result of one server-owned RED or GREEN task. */
@Entity
@Table(name = "test_check_evidence")
public class TestCheckEvidence {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) @JoinColumn(name = "task_id") private DeliveryTask task;
    @ManyToOne(optional = false) @JoinColumn(name = "runner_id") private Runner runner;
    @ManyToOne(optional = false) @JoinColumn(name = "gate_id") private VerificationGate gate;
    @Column(nullable = false, length = 36) private String leaseId;
    @Column(nullable = false, length = 8) private String checkKind;
    @Column(nullable = false, length = 255) private String image;
    @Column(nullable = false, length = 8000) private String command;
    @Column(nullable = false, length = 64) private String targetSha;
    @Column(length = 64) private String parentSha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TestCheckRules.Verdict verdict;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private TestCheckRules.Reason reason;
    @Column(length = 16) private String beforeStatus;
    @Column(length = 16) private String afterStatus;
    private Integer beforeExitCode;
    @Column(nullable = false) private int afterExitCode;
    @Column(nullable = false) private boolean beforeTimedOut;
    @Column(nullable = false) private boolean afterTimedOut;
    private Integer beforeTotal;
    private Integer beforePassed;
    private Integer beforeFailed;
    private Integer beforeErrored;
    private Integer beforeSkipped;
    @Column(nullable = false) private int afterTotal;
    @Column(nullable = false) private int afterPassed;
    @Column(nullable = false) private int afterFailed;
    @Column(nullable = false) private int afterErrored;
    @Column(nullable = false) private int afterSkipped;
    @Column(length = 262144) private String redTests;
    @Column(length = 4000) private String failingTests;
    @Column(length = 65536) private String classifiedTests;
    @Column(length = 65536) private String changedFiles;
    @Column(length = 64) private String beforeOutcomeDigest;
    @Column(nullable = false, length = 64) private String afterOutcomeDigest;
    @Column(length = 64) private String beforeOutputDigest;
    @Column(nullable = false, length = 64) private String afterOutputDigest;
    @Column(nullable = false, length = 1000) private String artifactReference;
    @Column(nullable = false, length = 64) private String bundleDigest;
    @Column(nullable = false, unique = true, length = 64) private String digest;
    @Column(nullable = false) private Instant recordedAt;

    protected TestCheckEvidence() { }

    public TestCheckEvidence(DeliveryTask task, Runner runner, String leaseId, TestCheckBundle bundle,
                             TestCheckRules.Decision decision, String artifactReference, String bundleDigest) {
        this.task = task;
        this.runner = runner;
        this.gate = task.getVerificationGate();
        this.leaseId = leaseId;
        this.checkKind = bundle.checkKind();
        this.image = bundle.image();
        this.command = String.join("\n", bundle.command());
        this.targetSha = bundle.targetSha();
        this.parentSha = bundle.parentSha();
        this.verdict = decision.verdict();
        this.reason = decision.reason();
        TestCheckBundle.TestCheckRunBundle before = bundle.before();
        TestCheckBundle.TestCheckRunBundle after = bundle.after();
        this.beforeStatus = before == null ? null : before.status().name();
        this.afterStatus = after.status().name();
        this.beforeExitCode = before == null ? null : before.exitCode();
        this.afterExitCode = after.exitCode();
        this.beforeTimedOut = before != null && before.timedOut();
        this.afterTimedOut = after.timedOut();
        this.beforeTotal = before == null ? null : before.outcomes().size();
        this.beforePassed = before == null ? null : count(before, TestCheckRules.Outcome.PASSED);
        this.beforeFailed = before == null ? null : count(before, TestCheckRules.Outcome.FAILED);
        this.beforeErrored = before == null ? null : count(before, TestCheckRules.Outcome.ERROR);
        this.beforeSkipped = before == null ? null : count(before, TestCheckRules.Outcome.SKIPPED);
        this.afterTotal = after.outcomes().size();
        this.afterPassed = count(after, TestCheckRules.Outcome.PASSED);
        this.afterFailed = count(after, TestCheckRules.Outcome.FAILED);
        this.afterErrored = count(after, TestCheckRules.Outcome.ERROR);
        this.afterSkipped = count(after, TestCheckRules.Outcome.SKIPPED);
        this.redTests = String.join("\n", decision.redSet());
        this.failingTests = boundedList(decision.failingTests(), 4_000);
        this.classifiedTests = boundedClassifications(decision.classifiedTests());
        this.changedFiles = boundedChangedFiles(bundle.changedFiles());
        this.beforeOutcomeDigest = before == null ? null : before.outcomeDigest();
        this.afterOutcomeDigest = after.outcomeDigest();
        this.beforeOutputDigest = before == null ? null : before.outputDigest();
        this.afterOutputDigest = after.outputDigest();
        this.artifactReference = artifactReference;
        this.bundleDigest = bundleDigest;
        this.recordedAt = Instant.now();
        this.digest = TestCheckBundle.sha256(task.getId() + "\u0000" + runner.getId() + "\u0000" + leaseId + "\u0000" + bundleDigest);
        task.attachTestCheckEvidence(this);
        if (this.classifiedTests.length() > 65_536 || this.changedFiles.length() > 65_536) {
            throw new IllegalArgumentException("Test check summary exceeds its storage bound");
        }
    }

    public boolean isCurrentRedFor(String changeSha) {
        return "RED".equals(checkKind) && verdict == TestCheckRules.Verdict.PASS && targetSha.equals(changeSha);
    }

    public boolean isPassingGreenFor(String integrationSha) {
        return "GREEN".equals(checkKind) && verdict == TestCheckRules.Verdict.PASS && targetSha.equals(integrationSha);
    }

    private static int count(TestCheckBundle.TestCheckRunBundle report, TestCheckRules.Outcome outcome) {
        return (int) report.outcomes().values().stream().filter(outcome::equals).count();
    }

    private static String boundedList(List<String> values, int limit) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            int addition = value.length() + (result.isEmpty() ? 0 : 1);
            if (result.length() + addition > limit) break;
            if (!result.isEmpty()) result.append('\n');
            result.append(value);
        }
        return result.toString();
    }

    private static String boundedClassifications(List<TestCheckRules.ClassifiedTest> tests) {
        return boundedList(tests.stream().map(test -> test.group().name() + "\t" + test.identity() + "\t"
                + (test.before() == null ? "-" : test.before().name()) + "\t"
                + (test.after() == null ? "-" : test.after().name())).toList(), 65_536);
    }

    private static String boundedChangedFiles(List<TestCheckRules.ChangedFile> files) {
        return boundedList(files.stream().map(file -> file.blobSha() + " " + file.path()).toList(), 65_536);
    }

    public String getId() { return id; }
    public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); }
    public String getKind() { return checkKind; }
    public String getGate() { return gate.getName(); }
    public String getImage() { return image; }
    public List<String> getCommand() { return command.lines().toList(); }
    public String getTargetSha() { return targetSha; }
    public String getParentSha() { return parentSha; }
    public String getVerdict() { return verdict.name(); }
    public String getReason() { return reason.name(); }
    public String getBeforeStatus() { return beforeStatus; }
    public String getAfterStatus() { return afterStatus; }
    public Integer getBeforeExitCode() { return beforeExitCode; }
    public int getAfterExitCode() { return afterExitCode; }
    public boolean isBeforeTimedOut() { return beforeTimedOut; }
    public boolean isAfterTimedOut() { return afterTimedOut; }
    public Integer getBeforeTotal() { return beforeTotal; }
    public Integer getBeforePassed() { return beforePassed; }
    public Integer getBeforeFailed() { return beforeFailed; }
    public Integer getBeforeErrored() { return beforeErrored; }
    public Integer getBeforeSkipped() { return beforeSkipped; }
    public int getAfterTotal() { return afterTotal; }
    public int getAfterPassed() { return afterPassed; }
    public int getAfterFailed() { return afterFailed; }
    public int getAfterErrored() { return afterErrored; }
    public int getAfterSkipped() { return afterSkipped; }
    public List<String> getRedTests() { return redTests == null || redTests.isBlank() ? List.of() : redTests.lines().toList(); }
    public List<String> getFailingTests() { return failingTests == null || failingTests.isBlank() ? List.of() : failingTests.lines().toList(); }
    public String getClassifiedTests() { return classifiedTests == null ? "" : classifiedTests; }
    public String getChangedFiles() { return changedFiles == null ? "" : changedFiles; }
    /** Rehydrates the bounded path/blob pairs used to compare test files against GitHub's published diff. */
    public List<TestCheckRules.ChangedFile> changedFilesEvidence() {
        if (changedFiles == null || changedFiles.isBlank()) return List.of();
        return changedFiles.lines().map(line -> {
            int separator = line.indexOf(' ');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException("Stored changed-file evidence is malformed");
            }
            return new TestCheckRules.ChangedFile(line.substring(separator + 1), line.substring(0, separator));
        }).toList();
    }
    public String getBeforeOutcomeDigest() { return beforeOutcomeDigest; }
    public String getAfterOutcomeDigest() { return afterOutcomeDigest; }
    public String getBeforeOutputDigest() { return beforeOutputDigest; }
    public String getAfterOutputDigest() { return afterOutputDigest; }
    public String getArtifactReference() { return artifactReference; }
    public String getBundleDigest() { return bundleDigest; }
    public String getDigest() { return digest; }
    public String getRecordedAt() { return recordedAt.toString(); }
    Instant recordedAtInstant() { return recordedAt; }
}
