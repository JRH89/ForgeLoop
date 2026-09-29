package io.forgeloop.control.domain;

import static io.forgeloop.control.domain.TestCheckRules.Outcome.ERROR;
import static io.forgeloop.control.domain.TestCheckRules.Outcome.FAILED;
import static io.forgeloop.control.domain.TestCheckRules.Outcome.PASSED;
import static io.forgeloop.control.domain.TestCheckRules.Outcome.SKIPPED;
import static io.forgeloop.control.domain.TestCheckRules.ReportStatus.MISSING;
import static io.forgeloop.control.domain.TestCheckRules.ReportStatus.READ;
import static io.forgeloop.control.domain.TestCheckRules.ReportStatus.UNREADABLE;
import static io.forgeloop.control.domain.TestCheckRules.Reason.*;
import static io.forgeloop.control.domain.TestCheckRules.Verdict.FAIL;
import static io.forgeloop.control.domain.TestCheckRules.Verdict.PASS;
import static io.forgeloop.control.domain.TestCheckRules.Verdict.UNVERIFIABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestCheckRulesTest {
    private static final List<String> GLOBS = List.of("**/src/test/**", "tests/**");
    private static final TestCheckRules.ChangedFile TEST_FILE = changed("src/test/java/SampleTest.java");

    @Test void redAppliesNonTestPathRuleBeforeTimeoutAndReportRules() {
        var decision = TestCheckRules.red(GLOBS, report(READ, 0, true, Map.of()), report(MISSING, 1, true, Map.of()),
                List.of(TEST_FILE, changed("src/main/java/Sample.java")));
        assertDecision(decision, FAIL, NON_TEST_PATH_CHANGED);
    }

    @Test void redMarksEitherTimedOutRunUnverifiableBeforeOtherOutcomeRules() {
        var before = report(READ, 0, false, Map.of("S#old", PASSED));
        var after = report(READ, 1, true, Map.of("S#old", PASSED, "S#new", FAILED));
        assertDecision(TestCheckRules.red(GLOBS, before, after, List.of(TEST_FILE)), UNVERIFIABLE, TIMED_OUT);
    }

    @Test void redRequiresAReadableGreenBaselineAndAConsistentExitCode() {
        assertDecision(TestCheckRules.red(GLOBS, report(MISSING, 0, false, Map.of()), report(MISSING, 1, false, Map.of()), List.of(TEST_FILE)),
                UNVERIFIABLE, NO_TEST_REPORT);
        assertDecision(TestCheckRules.red(GLOBS, report(UNREADABLE, 0, false, Map.of()), report(READ, 0, false, Map.of()), List.of(TEST_FILE)),
                UNVERIFIABLE, REPORT_UNREADABLE);
        assertDecision(TestCheckRules.red(GLOBS, report(READ, 0, false, Map.of("S#old", FAILED)), report(READ, 1, false, Map.of()), List.of(TEST_FILE)),
                UNVERIFIABLE, REPORT_CONTRADICTS_EXIT_CODE);
        assertDecision(TestCheckRules.red(GLOBS, report(READ, 1, false, Map.of("S#old", FAILED)), report(READ, 0, false, Map.of()), List.of(TEST_FILE)),
                UNVERIFIABLE, BASELINE_NOT_GREEN);
    }

    @Test void redDistinguishesTestsThatDidNotRunFromMissingReportsWithAZeroExit() {
        var before = report(READ, 0, false, Map.of("S#old", PASSED));
        assertDecision(TestCheckRules.red(GLOBS, before, report(MISSING, 1, false, Map.of()), List.of(TEST_FILE)), FAIL, TESTS_DID_NOT_RUN);
        assertDecision(TestCheckRules.red(GLOBS, before, report(MISSING, 0, false, Map.of()), List.of(TEST_FILE)), UNVERIFIABLE, NO_TEST_REPORT);
        assertDecision(TestCheckRules.red(GLOBS, before, report(UNREADABLE, 1, false, Map.of()), List.of(TEST_FILE)), UNVERIFIABLE, REPORT_UNREADABLE);
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 0, false, Map.of("S#new", FAILED)), List.of(TEST_FILE)),
                UNVERIFIABLE, REPORT_CONTRADICTS_EXIT_CODE);
    }

    @Test void redRejectsRemovedErroredPassingOrSkippedTestsBeforeLookingForAValidRedSet() {
        var before = report(READ, 0, false, Map.of("S#old", PASSED));
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 1, false, Map.of("S#new", FAILED)), List.of(TEST_FILE)), FAIL, EXISTING_TEST_REMOVED);
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 1, false, Map.of("S#old", PASSED, "S#new", ERROR)), List.of(TEST_FILE)), FAIL, TEST_ERRORED);
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 0, false, Map.of("S#old", PASSED, "S#new", PASSED)), List.of(TEST_FILE)), FAIL, ADDED_TEST_PASSED);
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 1, false, Map.of("S#old", SKIPPED, "S#new", FAILED)), List.of(TEST_FILE)), FAIL, TEST_SKIPPED);
    }

    @Test void redRequiresAtLeastOneAssertionFailureAndCapsTheRedSet() {
        var before = report(READ, 0, false, Map.of("S#old", PASSED));
        assertDecision(TestCheckRules.red(GLOBS, before, report(READ, 0, false, Map.of("S#old", PASSED)), List.of(TEST_FILE)), FAIL, NO_FAILING_TEST);
        Map<String, TestCheckRules.Outcome> many = new java.util.HashMap<>();
        for (int index = 0; index < 501; index++) many.put("S#added" + index, FAILED);
        assertDecision(TestCheckRules.red(GLOBS, report(READ, 0, false, Map.of()), report(READ, 1, false, many), List.of(TEST_FILE)),
                UNVERIFIABLE, TOO_MANY_TESTS);
    }

    @Test void redCapturesAddedAndDeliberatelyChangedFailingTestsSeparately() {
        var before = report(READ, 0, false, Map.of("S#existing", PASSED));
        var after = report(READ, 1, false, Map.of("S#existing", FAILED, "S#added", FAILED));
        var decision = TestCheckRules.red(GLOBS, before, after, List.of(TEST_FILE));
        assertDecision(decision, PASS, RED_CONFIRMED);
        assertEquals(List.of("S#added", "S#existing"), decision.redSet());
        assertEquals(TestCheckRules.Group.ADDED, decision.classifiedTests().get(0).group());
        assertEquals(TestCheckRules.Group.CHANGED, decision.classifiedTests().get(1).group());
    }

    @Test void greenAppliesExpectedSetLimitBeforeExecutionResults() {
        Set<String> tooMany = new java.util.HashSet<>();
        for (int index = 0; index < 2_001; index++) tooMany.add("S#t" + index);
        assertDecision(TestCheckRules.green(tooMany, 0, report(READ, 0, false, Map.of())), UNVERIFIABLE, TOO_MANY_TESTS);
    }

    @Test void greenRequiresAReadableConsistentReport() {
        Set<String> expected = Set.of("S#added");
        assertDecision(TestCheckRules.green(expected, 1, report(READ, 0, true, Map.of())), UNVERIFIABLE, TIMED_OUT);
        assertDecision(TestCheckRules.green(expected, 1, report(MISSING, 0, false, Map.of())), UNVERIFIABLE, NO_TEST_REPORT);
        assertDecision(TestCheckRules.green(expected, 1, report(UNREADABLE, 0, false, Map.of())), UNVERIFIABLE, REPORT_UNREADABLE);
        assertDecision(TestCheckRules.green(expected, 1, report(READ, 0, false, Map.of("S#added", FAILED))), UNVERIFIABLE, REPORT_CONTRADICTS_EXIT_CODE);
    }

    @Test void greenChecksExpectedTestsThenOtherFailuresAndTheRedRunTestCountFloor() {
        Set<String> expected = Set.of("S#added");
        assertDecision(TestCheckRules.green(expected, 1, report(READ, 0, false, Map.of())), FAIL, EXPECTED_TEST_MISSING);
        assertDecision(TestCheckRules.green(expected, 2, report(READ, 1, false, Map.of("S#added", SKIPPED, "S#other", FAILED))),
                FAIL, EXPECTED_TEST_NOT_PASSED);
        assertDecision(TestCheckRules.green(expected, 2, report(READ, 1, false, Map.of("S#added", PASSED, "S#other", FAILED))),
                FAIL, TEST_FAILED);
        assertDecision(TestCheckRules.green(expected, 2, report(READ, 0, false, Map.of("S#added", PASSED))), FAIL, TESTS_MISSING);
        assertDecision(TestCheckRules.green(expected, 1, report(READ, 0, false, Map.of("S#added", PASSED))), PASS, GREEN_CONFIRMED);
    }

    private static TestCheckRules.RunReport report(TestCheckRules.ReportStatus status, int exitCode, boolean timedOut,
                                                    Map<String, TestCheckRules.Outcome> outcomes) {
        return new TestCheckRules.RunReport(status, exitCode, timedOut, outcomes);
    }

    private static TestCheckRules.ChangedFile changed(String path) {
        return new TestCheckRules.ChangedFile(path, "a".repeat(40));
    }

    private static void assertDecision(TestCheckRules.Decision actual, TestCheckRules.Verdict verdict, TestCheckRules.Reason reason) {
        assertEquals(verdict, actual.verdict());
        assertEquals(reason, actual.reason());
    }
}
