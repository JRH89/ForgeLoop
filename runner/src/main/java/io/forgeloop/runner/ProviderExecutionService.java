package io.forgeloop.runner;

/** Executes a provider under a small bounded retry budget; permanent failures never yield a result. */
public final class ProviderExecutionService {
    public ProviderResult execute(ProviderClient provider, ProviderRequest request, int maxAttempts) throws ProviderException {
        try { return executeDetailed(provider, request, maxAttempts).result(); }
        catch (ProviderExecutionFailure failure) { throw failure.providerFailure(); }
    }

    public ProviderExecutionResult executeDetailed(ProviderClient provider, ProviderRequest request, int maxAttempts) throws ProviderExecutionFailure {
        if (maxAttempts < 1 || maxAttempts > 3) throw new IllegalArgumentException("Provider attempt budget is invalid");
        java.util.List<ProviderResult> receipts = new java.util.ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ProviderResult result = provider.execute(request);
                receipts.add(result);
                return new ProviderExecutionResult(result, attempt, receipts);
            } catch (ProviderException failure) {
                if (failure.receipt() != null) receipts.add(failure.receipt());
                if (!failure.retryable() || attempt == maxAttempts) throw new ProviderExecutionFailure(failure, attempt, receipts);
            }
        }
        throw new IllegalStateException("Provider attempt loop exhausted unexpectedly");
    }
}
