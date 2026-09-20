package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GithubAppInstallationControllerTest {
    @Test
    void redirectsToConfiguredGithubAppInstallation() {
        var response = new GithubAppInstallationController("forgeloop-dev").install();
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://github.com/apps/forgeloop-dev/installations/new", response.getHeaders().getLocation().toString());
    }

    @Test
    void refusesToRedirectWithoutAConfiguredApp() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, new GithubAppInstallationController("").install().getStatusCode());
    }
}
