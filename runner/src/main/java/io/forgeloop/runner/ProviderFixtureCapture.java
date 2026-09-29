package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/** Captures one real, bounded provider response as a reviewable canonical fixture. */
final class ProviderFixtureCapture {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Map<String, String> TEXT_PATHS = Map.of(
            "anthropic", "content[].text", "openai", "output[].content[].text",
            "gemini", "candidates[].content.parts[].text", "local", "choices[].message.content");
    private static final ObjectMapper JSON = new ObjectMapper();

    Captured capture(String provider, String model, Supplier<ProviderClient> clientFactory,
                     Path fixtureRoot, Instant recordedAt) throws Exception {
        ProviderClientFactory codecs = new ProviderClientFactory();
        String adapter = codecs.adapterId(provider);
        if (model == null || model.isBlank() || model.length() > 200 || clientFactory == null
                || fixtureRoot == null || recordedAt == null) {
            throw new IllegalArgumentException("Provider fixture capture inputs are invalid");
        }

        Path root = fixtureRoot.toAbsolutePath().normalize();
        rejectSymlinkAncestors(root);
        Files.createDirectories(root);
        rejectSymlinkAncestors(root);
        Path destination = root.resolve(provider).normalize();
        if (!destination.getParent().equals(root) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Provider fixture already exists or has an unsafe path");
        }

        // Keep the prompt fixed and the request to one call so checked-in captures contain no user data.
        ProviderRequest request = new ProviderRequest(model, "You are a credential health check.",
                "Reply with exactly: ForgeLoop provider ready.", 128);
        ProviderClient client = clientFactory.get();
        if (client == null) throw new IllegalArgumentException("Provider client is unavailable");
        String response = client.executeRaw(request);
        if (response == null || response.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IOException("Provider response is empty or exceeds the fixture safety limit");
        }

        ProviderResult parsed = codecs.parse(provider, response);
        String textPath = TEXT_PATHS.get(provider);
        String textKind = ProviderShape.of(provider, response).kinds().get(textPath);
        if (parsed.output().isBlank() || textKind == null || !textKind.contains("string")) {
            throw new IOException("Provider response does not contain parser-consumed text");
        }

        Path staging = Files.createTempDirectory(root, "." + provider + "-capture-");
        try {
            Files.writeString(staging.resolve("response.json"), response, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, String> metadata = Map.of(
                    "adapter", adapter,
                    "endpoint", codecs.pinnedEndpoint(provider),
                    "apiVersion", codecs.pinnedApiVersion(provider),
                    "model", model,
                    "recordedAt", recordedAt.toString());
            Files.writeString(staging.resolve("meta.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
                    + System.lineSeparator(), java.nio.charset.StandardCharsets.UTF_8);
            // A plain move deliberately keeps the no-replace contract if another capture races us.
            Files.move(staging, destination);
        } catch (Exception failure) {
            deleteStagingDirectory(staging, root);
            throw failure;
        }

        return new Captured(destination, parsed.inputTokens(), parsed.outputTokens(), parsed.providerRequestId());
    }

    private static void deleteStagingDirectory(Path staging, Path root) throws IOException {
        Path safeStaging = staging.toAbsolutePath().normalize();
        if (!safeStaging.getParent().equals(root) || !safeStaging.getFileName().toString().startsWith(".")) return;
        try (var paths = Files.walk(safeStaging)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void rejectSymlinkAncestors(Path root) throws IOException {
        for (Path path = root; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Provider fixture path cannot contain a symbolic link");
        }
    }

    record Captured(Path directory, long inputTokens, long outputTokens, String requestId) { }
}
