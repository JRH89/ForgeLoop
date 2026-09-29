package io.forgeloop.runner;

import java.util.List;

/** Pure shape comparison used by the explicit CLI probe; tests can exercise it without network or credentials. */
public final class ProviderDriftCheck {
    public Result compare(String provider, String recordedResponseBody, String currentResponseBody) {
        ProviderShape expected = ProviderShape.of(provider, recordedResponseBody);
        ProviderShape actual = ProviderShape.of(provider, currentResponseBody);
        List<String> differences = expected.differences(actual);
        return new Result(differences.isEmpty(), differences);
    }

    public record Result(boolean matches, List<String> differences) {
        public Result { differences = List.copyOf(differences); }
    }
}
