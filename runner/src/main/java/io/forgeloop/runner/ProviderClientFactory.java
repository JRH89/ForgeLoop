package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;

/** Builds only allow-listed providers and reads credentials exclusively from the runner environment. */
public final class ProviderClientFactory {
    static final String ANTHROPIC_API_VERSION = "2023-06-01";
    static final String ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/responses";
    static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/";
    static final String LOCAL_ENDPOINT_PATH = "/v1/chat/completions";
    /** Stable adapter identifier for execution records; contains no credential or endpoint data. */
    public String adapterId(String provider) {
        return switch (provider) {
            case "anthropic" -> "anthropic-messages/1";
            case "openai" -> "openai-responses/1";
            case "gemini" -> "gemini-generate-content/1";
            case "local" -> "openai-chat-compatible/1";
            default -> throw new IllegalArgumentException("Unsupported provider policy");
        };
    }

    /** Rebuilds the exact provider HTTP payload without creating a client or reading credentials. */
    public String requestBody(String provider, ProviderRequest request) throws Exception {
        if (request == null) throw new IllegalArgumentException("Provider request is required");
        return switch (provider) {
            case "anthropic" -> AnthropicMessagesProviderClient.requestBody(request);
            case "openai" -> OpenAiResponsesProviderClient.requestBody(request);
            case "gemini" -> GeminiGenerateContentProviderClient.requestBody(request);
            case "local" -> OpenAiChatCompatibleProviderClient.requestBody(request);
            default -> throw new IllegalArgumentException("Unsupported provider policy");
        };
    }

    /** Parses a captured one-call response without constructing a credentialed client. */
    ProviderResult parse(String provider, String body) throws Exception {
        return switch (provider) {
            case "anthropic" -> AnthropicMessagesProviderClient.parse(body);
            case "openai" -> OpenAiResponsesProviderClient.parse(body);
            case "gemini" -> GeminiGenerateContentProviderClient.parse(body);
            case "local" -> OpenAiChatCompatibleProviderClient.parse(body);
            default -> throw new IllegalArgumentException("Unsupported provider policy");
        };
    }

    /** Serializes conversation turns exactly as the selected adapter would, without reading credentials. */
    String conversationBody(String provider, ConversationRequest request) {
        try {
            return switch (provider) {
                case "anthropic" -> AnthropicMessagesProviderClient.conversationBody(request);
                case "openai" -> OpenAiResponsesProviderClient.conversationBody(request);
                case "gemini" -> GeminiGenerateContentProviderClient.conversationBody(request);
                case "local" -> OpenAiChatCompatibleProviderClient.conversationBody(request);
                default -> throw new IllegalArgumentException("Unsupported provider policy");
            };
        } catch (IllegalArgumentException failure) { throw failure;
        } catch (Exception failure) { throw new IllegalArgumentException("Conversation request could not be serialized", failure); }
    }

    /** Parses a captured conversation response through the selected adapter's production parser. */
    ConversationTurn parseConversation(String provider, String body, ConversationRequest request) throws Exception {
        long turnNumber = request.items().stream().filter(AssistantTurn.class::isInstance).count() + 1;
        return switch (provider) {
            case "anthropic" -> AnthropicMessagesProviderClient.parseConversation(body);
            case "openai" -> OpenAiResponsesProviderClient.parseConversation(body);
            case "gemini" -> GeminiGenerateContentProviderClient.parseConversation(body, turnNumber);
            case "local" -> OpenAiChatCompatibleProviderClient.parseConversation(body);
            default -> throw new IllegalArgumentException("Unsupported provider policy");
        };
    }

    public ProviderClient create(ProviderExecutionPolicy policy) {
        HttpClient client = HttpClient.newHttpClient();
        return switch (policy.provider()) {
            case "anthropic" -> new AnthropicMessagesProviderClient(client, URI.create(ANTHROPIC_ENDPOINT), required("ANTHROPIC_API_KEY"));
            case "openai" -> new OpenAiResponsesProviderClient(client, URI.create(OPENAI_ENDPOINT), required("OPENAI_API_KEY"));
            case "gemini" -> new GeminiGenerateContentProviderClient(client, URI.create(GEMINI_ENDPOINT), required("GEMINI_API_KEY"));
            case "local" -> new OpenAiChatCompatibleProviderClient(client, localEndpoint(), optional("FORGELOOP_LOCAL_PROVIDER_API_KEY"));
            default -> throw new IllegalStateException("Unsupported provider policy");
        };
    }

    String pinnedEndpoint(String provider) {
        return switch (provider) {
            case "anthropic" -> ANTHROPIC_ENDPOINT;
            case "openai" -> OPENAI_ENDPOINT;
            case "gemini" -> GEMINI_ENDPOINT;
            case "local" -> LOCAL_ENDPOINT_PATH;
            default -> throw new IllegalArgumentException("Unsupported provider policy");
        };
    }

    String pinnedApiVersion(String provider) {
        return switch (provider) {
            case "anthropic" -> ANTHROPIC_API_VERSION;
            case "openai" -> "v1";
            case "gemini" -> "v1beta";
            case "local" -> "openai-chat-compatible";
            default -> throw new IllegalArgumentException("Unsupported provider policy");
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
