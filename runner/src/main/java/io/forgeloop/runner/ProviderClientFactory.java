package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;

/** Builds only allow-listed providers and reads credentials exclusively from the runner environment. */
public final class ProviderClientFactory {
    public ProviderClient create(ProviderExecutionPolicy policy) {
        HttpClient client = HttpClient.newHttpClient();
        return switch (policy.provider()) {
            case "anthropic" -> new AnthropicMessagesProviderClient(client, URI.create("https://api.anthropic.com/v1/messages"), required("ANTHROPIC_API_KEY"));
            case "openai" -> new OpenAiResponsesProviderClient(client, URI.create("https://api.openai.com/v1/responses"), required("OPENAI_API_KEY"));
            case "gemini" -> new GeminiGenerateContentProviderClient(client, URI.create("https://generativelanguage.googleapis.com/v1beta/"), required("GEMINI_API_KEY"));
            case "local" -> new OpenAiChatCompatibleProviderClient(client, localEndpoint(), optional("FORGELOOP_LOCAL_PROVIDER_API_KEY"));
            default -> throw new IllegalStateException("Unsupported provider policy");
        };
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not set in this runner process");
        return value;
    }
    private static String optional(String name) { String value = System.getenv(name); return value == null ? "" : value; }
    private static URI localEndpoint() {
        URI endpoint = URI.create(required("FORGELOOP_LOCAL_PROVIDER_URL"));
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme())) || (!loopback && !"true".equalsIgnoreCase(optional("FORGELOOP_LOCAL_PROVIDER_ALLOW_REMOTE")))) {
            throw new IllegalArgumentException("Local provider URL must be HTTP(S) loopback unless remote endpoints are explicitly allowed");
        }
        return endpoint;
    }
}
