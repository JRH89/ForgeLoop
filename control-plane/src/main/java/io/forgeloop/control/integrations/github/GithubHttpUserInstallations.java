package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls GitHub's {@code GET /user/installations} with the operator's own user access token. GitHub only
 * lists installations of this App that the user can administer or access, which is the authoritative proof
 * that a setup-URL {@code installation_id} was not copied from another customer.
 */
@Component
public class GithubHttpUserInstallations implements GithubUserInstallations {
    /** GitHub's maximum page size; ten pages covers 1,000 installations per user. */
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private final String apiUrl;
    private final ObjectMapper json;
    private final HttpClient http;

    public GithubHttpUserInstallations(@Value("${forgeloop.github.api-url:https://api.github.com}") String apiUrl, ObjectMapper json) {
        this.apiUrl = apiUrl.replaceAll("/$", "");
        this.json = json;
        // Never follow redirects with a bearer token attached.
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public boolean canAccess(String userAccessToken, long installationId) {
        if (userAccessToken == null || userAccessToken.isBlank() || installationId < 1) return false;
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode installations = page(userAccessToken, page).path("installations");
                for (JsonNode installation : installations) {
                    if (installation.path("id").asLong() == installationId) return true;
                }
                if (installations.size() < PAGE_SIZE) return false;
            }
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub installation access could not be verified", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub installation access could not be verified", exception);
        }
    }

    private JsonNode page(String userAccessToken, int page) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/user/installations?per_page=" + PAGE_SIZE + "&page=" + page))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + userAccessToken).header("User-Agent", "ForgeLoop").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) throw new GithubUserTokenRejectedException();
        // The status alone is reported; GitHub bodies may echo request details and are never surfaced.
        if (response.statusCode() != 200) throw new IllegalStateException("GitHub installation lookup failed with HTTP " + response.statusCode());
        return json.readTree(response.body());
    }
}
