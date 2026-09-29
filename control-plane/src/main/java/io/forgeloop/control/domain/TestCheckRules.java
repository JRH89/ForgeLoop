package io.forgeloop.control.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Recomputes test-first check verdicts in the control plane from bounded runner observations. */
public final class TestCheckRules {
    public enum Verdict { PASS, FAIL, UNVERIFIABLE }
    public enum Reason {
        RED_CONFIRMED, NON_TEST_PATH_CHANGED, TIMED_OUT, NO_TEST_REPORT, REPORT_UNREADABLE,
        REPORT_CONTRADICTS_EXIT_CODE, BASELINE_NOT_GREEN, TESTS_DID_NOT_RUN,
        EXISTING_TEST_REMOVED, TEST_ERRORED, ADDED_TEST_PASSED, TEST_SKIPPED,
        NO_FAILING_TEST, TOO_MANY_TESTS, EXPECTED_TEST_MISSING,
        EXPECTED_TEST_NOT_PASSED, TEST_FAILED, TESTS_MISSING, GREEN_CONFIRMED
    }
    public enum ReportStatus { READ, MISSING, UNREADABLE }
    public enum Outcome { ERROR, FAILED, SKIPPED, PASSED }
    public enum Group { ADDED, CHANGED, REMOVED, BROKE_RULE }

    public record RunReport(ReportStatus status, int exitCode, boolean timedOut, Map<String, Outcome> outcomes) {
        public RunReport {
            if (status == null || outcomes == null || exitCode < -1) throw new IllegalArgumentException("Test run report is invalid");
            outcomes = Map.copyOf(new TreeMap<>(outcomes));
            if (status != ReportStatus.READ && !outcomes.isEmpty()) throw new IllegalArgumentException("Unread reports cannot include test outcomes");
        }

        public boolean hasFailureOrError() {
            return outcomes.values().stream().anyMatch(outcome -> outcome == Outcome.FAILED || outcome == Outcome.ERROR);
        }
    }

    public record ChangedFile(String path, String blobSha) {
        public ChangedFile {
            if (path == null || path.isBlank() || path.length() > 1000 || path.startsWith("/")
                    || path.contains("\\") || path.codePoints().anyMatch(Character::isISOControl)
                    || java.util.Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
                throw new IllegalArgumentException("Changed test path is invalid");
            }
            if (blobSha == null || !blobSha.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("Changed file blob SHA is invalid");
        }
    }

    public record ClassifiedTest(Group group, String identity, Outcome before, Outcome after) { }
    public record Decision(Verdict verdict, Reason reason, List<String> redSet, List<String> failingTests,
                          List<ClassifiedTest> classifiedTests) {
        public Decision {
            redSet = List.copyOf(redSet);
            failingTests = List.copyOf(failingTests);
            classifiedTests = List.copyOf(classifiedTests);
        }
    }

    private static final int MAX_RED_TESTS = 500;
    private static final int MAX_GREEN_EXPECTED_TESTS = 2_000;
    private TestCheckRules() { }

    public static Decision red(List<String> testGlobs, RunReport before, RunReport after, List<ChangedFile> changedFiles) {
        if (before == null || after == null || changedFiles == null || !TestPathGlobs.areValid(testGlobs)) {
            throw new IllegalArgumentException("RED check inputs are invalid");
        }
        if (changedFiles.stream().anyMatch(file -> !TestPathGlobs.matchesAny(file.path(), testGlobs))) return decision(Verdict.FAIL, Reason.NON_TEST_PATH_CHANGED);
        if (before.timedOut() || after.timedOut()) return decision(Verdict.UNVERIFIABLE, Reason.TIMED_OUT);
        if (before.status() == ReportStatus.MISSING) return decision(Verdict.UNVERIFIABLE, Reason.NO_TEST_REPORT);
        if (before.status() == ReportStatus.UNREADABLE || after.status() == ReportStatus.UNREADABLE) return decision(Verdict.UNVERIFIABLE, Reason.REPORT_UNREADABLE);
        if (contradictsExitCode(before)) return decision(Verdict.UNVERIFIABLE, Reason.REPORT_CONTRADICTS_EXIT_CODE);
        if (before.hasFailureOrError()) return decision(Verdict.UNVERIFIABLE, Reason.BASELINE_NOT_GREEN);
        if (after.status() == ReportStatus.MISSING) {
            return after.exitCode() == 0 ? decision(Verdict.UNVERIFIABLE, Reason.NO_TEST_REPORT)
                    : decision(Verdict.FAIL, Reason.TESTS_DID_NOT_RUN);
        }
        if (contradictsExitCode(after)) return decision(Verdict.UNVERIFIABLE, Reason.REPORT_CONTRADICTS_EXIT_CODE);

        Map<String, Outcome> beforeOutcomes = before.outcomes();
        Map<String, Outcome> afterOutcomes = after.outcomes();
        List<ClassifiedTest> classified = classify(beforeOutcomes, afterOutcomes);
        List<String> removed = classified.stream().filter(test -> test.group() == Group.REMOVED).map(ClassifiedTest::identity).toList();
        if (!removed.isEmpty()) return decision(Verdict.FAIL, Reason.EXISTING_TEST_REMOVED, List.of(), removed, classified);
        List<String> errored = identitiesWith(afterOutcomes, Outcome.ERROR);
        if (!errored.isEmpty()) return decision(Verdict.FAIL, Reason.TEST_ERRORED, List.of(), errored, classified);
        List<String> passedNew = classified.stream().filter(test -> test.group() == Group.ADDED && test.after() == Outcome.PASSED)
                .map(ClassifiedTest::identity).toList();
        if (!passedNew.isEmpty()) return decision(Verdict.FAIL, Reason.ADDED_TEST_PASSED, List.of(), passedNew, classified);
        List<String> skipped = classified.stream().filter(test -> test.after() == Outcome.SKIPPED
                        && (test.group() == Group.ADDED || test.before() == Outcome.PASSED))
                .map(ClassifiedTest::identity).toList();
        if (!skipped.isEmpty()) return decision(Verdict.FAIL, Reason.TEST_SKIPPED, List.of(), skipped, classified);

        List<String> redSet = classified.stream()
                .filter(test -> test.after() == Outcome.FAILED)
                .filter(test -> test.group() == Group.ADDED || (test.before() != Outcome.FAILED && test.before() != Outcome.ERROR))
                .map(ClassifiedTest::identity).sorted().toList();
        if (redSet.isEmpty()) return decision(Verdict.FAIL, Reason.NO_FAILING_TEST, List.of(), List.of(), classified);
        if (redSet.size() > MAX_RED_TESTS) return decision(Verdict.UNVERIFIABLE, Reason.TOO_MANY_TESTS);
        return new Decision(Verdict.PASS, Reason.RED_CONFIRMED, redSet, List.of(), classified);
    }

    public static Decision green(Set<String> expectedTests, int minimumTestCount, RunReport report) {
        if (expectedTests == null || report == null || minimumTestCount < 0) throw new IllegalArgumentException("GREEN check inputs are invalid");
        if (expectedTests.size() > MAX_GREEN_EXPECTED_TESTS) return decision(Verdict.UNVERIFIABLE, Reason.TOO_MANY_TESTS);
        if (report.timedOut()) return decision(Verdict.UNVERIFIABLE, Reason.TIMED_OUT);
        if (report.status() == ReportStatus.MISSING) return decision(Verdict.UNVERIFIABLE, Reason.NO_TEST_REPORT);
        if (report.status() == ReportStatus.UNREADABLE) return decision(Verdict.UNVERIFIABLE, Reason.REPORT_UNREADABLE);
        if (contradictsExitCode(report)) return decision(Verdict.UNVERIFIABLE, Reason.REPORT_CONTRADICTS_EXIT_CODE);
        List<String> missing = expectedTests.stream().filter(identity -> !report.outcomes().containsKey(identity)).sorted().toList();
        if (!missing.isEmpty()) return decision(Verdict.FAIL, Reason.EXPECTED_TEST_MISSING, List.of(), missing, List.of());
        List<String> notPassed = expectedTests.stream().filter(identity -> report.outcomes().get(identity) != Outcome.PASSED).sorted().toList();
        if (!notPassed.isEmpty()) return decision(Verdict.FAIL, Reason.EXPECTED_TEST_NOT_PASSED, List.of(), notPassed, List.of());
        List<String> failed = identitiesWith(report.outcomes(), Outcome.FAILED, Outcome.ERROR).stream()
                .filter(identity -> !expectedTests.contains(identity)).toList();
        if (!failed.isEmpty()) return decision(Verdict.FAIL, Reason.TEST_FAILED, List.of(), failed, List.of());
        if (report.outcomes().size() < minimumTestCount) return decision(Verdict.FAIL, Reason.TESTS_MISSING);
        return decision(Verdict.PASS, Reason.GREEN_CONFIRMED);
    }

    public static List<ClassifiedTest> classify(Map<String, Outcome> before, Map<String, Outcome> after) {
        Set<String> identities = new java.util.TreeSet<>(before.keySet());
        identities.addAll(after.keySet());
        List<ClassifiedTest> classified = new ArrayList<>();
        for (String identity : identities) {
            Outcome prior = before.get(identity);
            Outcome current = after.get(identity);
            Group group;
            if (prior == null) group = Group.ADDED;
            else if (current == null) group = Group.REMOVED;
            else if (prior != current) group = Group.CHANGED;
            else if (current == Outcome.ERROR || current == Outcome.SKIPPED) group = Group.BROKE_RULE;
            else continue; // Keep the bounded evidence focused on additions, changes, removals, and violations.
            classified.add(new ClassifiedTest(group, identity, prior, current));
        }
        return List.copyOf(classified);
    }

    private static boolean contradictsExitCode(RunReport report) {
        return (report.exitCode() == 0 && report.hasFailureOrError())
                || (report.exitCode() != 0 && !report.hasFailureOrError());
    }

    private static List<String> identitiesWith(Map<String, Outcome> outcomes, Outcome... expected) {
        Set<Outcome> expectedOutcomes = EnumSet.noneOf(Outcome.class);
        java.util.Collections.addAll(expectedOutcomes, expected);
        return outcomes.entrySet().stream().filter(entry -> expectedOutcomes.contains(entry.getValue()))
                .map(Map.Entry::getKey).sorted().toList();
    }

    private static Decision decision(Verdict verdict, Reason reason) {
        return decision(verdict, reason, List.of(), List.of(), List.of());
    }

    private static Decision decision(Verdict verdict, Reason reason, List<String> redSet, List<String> failingTests,
                                     List<ClassifiedTest> classifiedTests) {
        return new Decision(verdict, reason, redSet, failingTests, classifiedTests);
    }
}
