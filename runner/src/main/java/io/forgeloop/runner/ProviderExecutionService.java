package io.forgeloop.runner;

/** Executes a provider under a small bounded retry budget; permanent failures never yield a result. */
public final class ProviderExecutionService {
    public ProviderResult execute(ProviderClient provider, ProviderRequest request, int maxAttempts) throws ProviderException {
        ProviderException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return provider.execute(request);
            } catch (ProviderException failure) {
                lastFailure = failure;
                if (!failure.retryable() || attempt == maxAttempts) throw failure;
            }
        }
        throw lastFailure;
    }
}
