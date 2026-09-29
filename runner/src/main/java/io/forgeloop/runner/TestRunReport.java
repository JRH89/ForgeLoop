package io.forgeloop.runner;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic per-test outcomes parsed from one isolated verification execution. */
public record TestRunReport(Status status, Map<String, Outcome> outcomes) {
    public enum Status { READ, MISSING, UNREADABLE }

    /** Outcome order intentionally places the most severe result first for duplicate identities. */
    public enum Outcome {
        ERROR(4), FAILED(3), SKIPPED(2), PASSED(1);

        private final int severity;

        Outcome(int severity) { this.severity = severity; }

        public static Outcome worst(Outcome left, Outcome right) {
            return left.severity >= right.severity ? left : right;
        }
    }

    public TestRunReport {
        if (status == null || outcomes == null) throw new IllegalArgumentException("Test report is invalid");
        outcomes = Collections.unmodifiableMap(new TreeMap<>(outcomes));
        if (status != Status.READ && !outcomes.isEmpty()) throw new IllegalArgumentException("Unavailable test reports cannot contain outcomes");
    }

    public int passed() { return count(Outcome.PASSED); }
    public int failed() { return count(Outcome.FAILED); }
    public int errored() { return count(Outcome.ERROR); }
    public int skipped() { return count(Outcome.SKIPPED); }

    private int count(Outcome outcome) {
        return (int) outcomes.values().stream().filter(outcome::equals).count();
    }
}
