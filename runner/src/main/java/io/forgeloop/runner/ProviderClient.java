package io.forgeloop.runner;

/** A replaceable runner-local model provider. Implementations must not log prompts, source, or credentials. */
@FunctionalInterface
public interface ProviderClient {
    ProviderResult execute(ProviderRequest request) throws ProviderException;

    /** Returns the raw response for shape-only diagnostics; adapters should override to avoid parsing first. */
    default String executeRaw(ProviderRequest request) throws ProviderException {
        ProviderResult result = execute(request);
        return result.responseBody();
    }
}
