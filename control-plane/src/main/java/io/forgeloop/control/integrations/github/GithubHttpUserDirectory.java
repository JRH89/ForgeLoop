package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Uses GitHub's public user endpoint; no customer OAuth token is stored or forwarded. */
@Component
public class GithubHttpUserDirectory implements GithubUserDirectory {
    private final String apiUrl;
    private final ObjectMapper json;
    private final HttpClient http;

    public GithubHttpUserDirectory(@Value("${forgeloop.github.api-url:https://api.github.com}") String apiUrl, ObjectMapper json) {
        this.apiUrl = apiUrl.replaceAll("/$", "");
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override public GithubUser findByLogin(String login) {
        if (login == null || !login.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Enter a valid GitHub username");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/users/" + login))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ForgeLoop").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new IllegalArgumentException("GitHub user was not found");
            if (response.statusCode() != 200) throw new IllegalStateException("GitHub user lookup failed with HTTP " + response.statusCode());
            JsonNode user = json.readTree(response.body());
            long id = user.path("id").asLong();
            String canonicalLogin = user.path("login").asText();
            if (id < 1 || canonicalLogin.isBlank() || !"User".equals(user.path("type").asText())) {
                throw new IllegalArgumentException("Invite a GitHub user account, not an organization or bot");
            }
            return new GithubUser(canonicalLogin, id);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub user lookup could not complete", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub user lookup could not complete", exception);
        }
    }
}
