package io.forgeloop.runner;

/** Executes a provider under a small bounded retry budget; permanent failures never yield a result. */
public final class ProviderExecutionService {
    public ProviderResult execute(ProviderClient provider, ProviderRequest request, int maxAttempts) throws ProviderException {
        try { return executeDetailed(provider, request, maxAttempts).result(); }
        catch (ProviderExecutionFailure failure) { throw failure.providerFailure(); }
    }

    public ProviderExecutionResult executeDetailed(ProviderClient provider, ProviderRequest request, int maxAttempts) throws ProviderExecutionFailure {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new ProviderExecutionResult(provider.execute(request), attempt);
            } catch (ProviderException failure) {
                if (!failure.retryable() || attempt == maxAttempts) throw new ProviderExecutionFailure(failure, attempt);
            }
        }
        throw new IllegalStateException("Provider attempt loop exhausted unexpectedly");
    }
}
