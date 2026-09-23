package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extracts bounded provider error metadata without logging request bodies, credentials, or arbitrary response fields. */
final class ProviderHttpErrors {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ProviderHttpErrors() { }

    static ProviderException from(String provider, int statusCode, String responseBody) {
        String detail = null;
        try {
            JsonNode error = JSON.readTree(responseBody).path("error");
            String type = clean(error.path("type").asText(null));
            String message = clean(error.path("message").asText(null));
            if (type != null || message != null) detail = String.join(": ", java.util.stream.Stream.of(type, message).filter(java.util.Objects::nonNull).toList());
        } catch (Exception ignored) {
            // Unknown response shapes remain intentionally opaque to avoid leaking provider-returned content.
        }
        String summary = provider + " provider request failed with HTTP " + statusCode;
        if (detail != null) summary += " (" + detail + ")";
        return new ProviderException(summary, statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').strip();
        return singleLine.substring(0, Math.min(singleLine.length(), 400));
    }
}
