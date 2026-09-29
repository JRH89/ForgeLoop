package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Locates only checked-in provider fixtures; it never records or synthesizes a canonical response. */
final class ProviderFixtureStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_FILE_BYTES = 4 * 1024 * 1024;

    Fixture load(String provider) throws IOException {
        // Validate before using the provider as a resource or filesystem path component.
        new ProviderClientFactory().adapterId(provider);
        String root = "/provider-fixtures/" + provider + "/";
        byte[] response = readResourceOrRepositoryFile(root + "response.json", provider, "response.json");
        byte[] metadata = readResourceOrRepositoryFile(root + "meta.json", provider, "meta.json");
        if (response == null || metadata == null) return null;
        try {
            JsonNode meta = JSON.readTree(metadata);
            String body = new String(response, java.nio.charset.StandardCharsets.UTF_8);
            ProviderShape.of(provider, body);
            return new Fixture(meta.path("adapter").asText(""), meta.path("endpoint").asText(""),
                    meta.path("apiVersion").asText(""), meta.path("model").asText(""),
                    meta.path("recordedAt").asText(""), body);
        } catch (Exception malformed) { throw new IOException("Recorded provider fixture is invalid", malformed); }
    }

    private static byte[] readResourceOrRepositoryFile(String resource, String provider, String name) throws IOException {
        try (InputStream input = ProviderFixtureStore.class.getResourceAsStream(resource)) {
            if (input != null) return bounded(input.readNBytes((int) MAX_FILE_BYTES + 1));
        }
        Path[] candidates = {
                Path.of("provider-fixtures", provider, name),
                Path.of("src", "test", "resources", "provider-fixtures", provider, name),
                Path.of("runner", "src", "test", "resources", "provider-fixtures", provider, name)
        };
        for (Path candidate : candidates) {
            if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
            long size = Files.size(candidate);
            if (size > MAX_FILE_BYTES) throw new IOException("Provider fixture exceeds the safe read limit");
            return Files.readAllBytes(candidate);
        }
        return null;
    }

    private static byte[] bounded(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Provider fixture exceeds the safe read limit");
        return bytes;
    }

    record Fixture(String adapter, String endpoint, String apiVersion, String model, String recordedAt, String responseBody) { }
}
