package io.forgeloop.runner;

import java.util.List;
import java.util.function.Supplier;

/** Runs at most one bounded health request and compares only parser-relevant JSON value kinds. */
final class ProviderDriftProbe {
    private final ProviderClientFactory codecs = new ProviderClientFactory();

    Result run(String provider, String model, ProviderFixtureStore.Fixture fixture,
               Supplier<ProviderClient> clientFactory) {
        if (provider == null || model == null || model.isBlank() || clientFactory == null)
            return new Result(2, "Provider drift check inputs are invalid.", List.of());
        try { codecs.adapterId(provider); }
        catch (IllegalArgumentException unsupported) { return new Result(2, "Provider adapter is unsupported.", List.of()); }
        if (fixture == null) return new Result(2, "No live-recorded fixture exists for this adapter.", List.of());
        if (!codecs.adapterId(provider).equals(fixture.adapter())
                || !codecs.pinnedEndpoint(provider).equals(fixture.endpoint())
                || !codecs.pinnedApiVersion(provider).equals(fixture.apiVersion()))
            return new Result(1, "Provider fixture pins differ from this adapter.", List.of("adapter/endpoint/apiVersion"));
        if (!model.equals(fixture.model())) return new Result(2, "Fixture was recorded for a different model.", List.of());
        try { codecs.parse(provider, fixture.responseBody()); }
        catch (Exception invalidFixture) { return new Result(2, "Recorded provider fixture does not parse.", List.of()); }
        final String liveBody;
        try {
            ProviderClient client = clientFactory.get();
            liveBody = client.executeRaw(new ProviderRequest(model, "You are a credential health check.",
                    "Reply with exactly: ForgeLoop provider ready.", 128));
        } catch (Exception unavailable) {
            return new Result(2, "Could not run provider drift check; verify runner credentials and connectivity.", List.of());
        }
        try {
            ProviderDriftCheck.Result comparison = new ProviderDriftCheck().compare(provider, fixture.responseBody(), liveBody);
            return comparison.matches()
                    ? new Result(0, "Provider response shape matches the recorded fixture.", List.of())
                    : new Result(1, "Provider response shape drifted.", comparison.differences());
        } catch (IllegalArgumentException invalidResponse) {
            return new Result(1, "Provider response shape drifted.", List.of("response (expected a parseable JSON object)"));
        }
    }

    record Result(int exitCode, String message, List<String> differences) {
        Result { differences = List.copyOf(differences); }
    }
}
