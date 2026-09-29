package io.forgeloop.runner;

import java.time.Instant;

/** Bounded, serializable result retained as verification evidence by a later runner protocol step. */
public record VerificationResult(int exitCode, boolean timedOut, String output, Instant startedAt, Instant finishedAt,
                                boolean outputTruncated) {
    public VerificationResult(int exitCode, boolean timedOut, String output, Instant startedAt, Instant finishedAt) {
        this(exitCode, timedOut, output, startedAt, finishedAt, false);
    }

    public boolean passed() { return !timedOut && exitCode == 0; }
}
