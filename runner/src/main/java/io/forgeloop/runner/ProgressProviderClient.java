package io.forgeloop.runner;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Emits safe metadata while a provider request is outstanding, never prompts or model output. */
public final class ProgressProviderClient implements ProviderClient {
    private final ProviderClient delegate;
    private final RunnerEventReporter events;
    public ProgressProviderClient(ProviderClient delegate, RunnerEventReporter events) {
        this.delegate = delegate; this.events = events;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        events.info("PROVIDER_STARTED", "Model request started");
        long started = System.nanoTime();
        try (var ticker = Executors.newSingleThreadScheduledExecutor()) {
            var progress = ticker.scheduleAtFixedRate(() -> events.info("EXECUTION_PROGRESS",
                    "Waiting for model response; elapsed seconds=" + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)), 10, 10, TimeUnit.SECONDS);
            try {
                ProviderResult result = delegate.execute(request);
                progress.cancel(false);
                events.info("PROVIDER_COMPLETED", "Model response received; input tokens=" + result.inputTokens() + "; output tokens=" + result.outputTokens());
                return result;
            } catch (ProviderException failure) {
                events.info("PROVIDER_FAILED", "Model request failed; retryable=" + failure.retryable());
                throw failure;
            } finally { progress.cancel(false); ticker.shutdownNow(); }
        }
    }
}
