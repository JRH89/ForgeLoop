package io.forgeloop.control.application;

import io.forgeloop.control.domain.Runner;

/** One-time enrollment response. The raw credential must be retained only by the runner. */
public record RunnerEnrollment(Runner runner, String credential) {
}
