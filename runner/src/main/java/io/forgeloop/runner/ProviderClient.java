package io.forgeloop.runner;

/** A replaceable runner-local model provider. Implementations must not log prompts, source, or credentials. */
@FunctionalInterface
public interface ProviderClient {
    ProviderResult execute(ProviderRequest request) throws ProviderException;
}
