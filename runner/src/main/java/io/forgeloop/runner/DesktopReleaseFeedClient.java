package io.forgeloop.runner;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Reads the same public, cached release feed as the website downloads page. */
public final class DesktopReleaseFeedClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private DesktopReleaseFeedClient() { }

    public static DesktopReleaseCatalog.Release fetch(URI endpoint, DesktopReleaseCatalog.Target target) throws Exception {
        PairingRequest.validateEndpoint(endpoint);
        URI feed = endpoint.resolve("/downloads/desktop-releases.json");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(feed).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("User-Agent", "ForgeLoop-Runner-Desktop")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("Public release information is temporarily unavailable.");
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("Public release information is larger than expected.");
            DesktopReleaseCatalog.Release release = DesktopReleaseCatalog.latest(new String(bytes, StandardCharsets.UTF_8), target);
            if (release == null) throw new IllegalStateException("No complete, verified desktop release is available for this computer.");
            return release;
        }
    }
}
