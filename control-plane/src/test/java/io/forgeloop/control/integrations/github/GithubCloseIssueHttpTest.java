package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ensures source issue closure uses the narrowly scoped installation-token API call. */
class GithubCloseIssueHttpTest {
    @Test
    void closesTheExpectedIssueAsCompletedWithInstallationAuthorization() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> request = new ArrayList<>();
        server.createContext("/", exchange -> {
            request.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            request.add(exchange.getRequestHeaders().getFirst("Authorization"));
            request.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"number\":9,\"state\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            GithubHttpApi api = new GithubHttpApi("http://127.0.0.1:" + server.getAddress().getPort(), "", "", new ObjectMapper()) {
                @Override public String issueInstallationToken(long installationId) {
                    assertEquals(12, installationId);
                    return "test-installation-token";
                }
            };

            api.closeIssue(12, "acme/ticketly", 9);

            assertEquals("PATCH /repos/acme/ticketly/issues/9", request.get(0));
            assertEquals("Bearer test-installation-token", request.get(1));
            var body = new ObjectMapper().readTree(request.get(2));
            assertEquals("closed", body.path("state").asText());
            assertEquals("completed", body.path("state_reason").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidIssueTargetsBeforeIssuingARequest() {
        GithubHttpApi api = new GithubHttpApi("https://api.github.com", "", "", new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> api.closeIssue(12, "acme/ticketly/issues", 9));
        assertThrows(IllegalArgumentException.class, () -> api.closeIssue(12, "acme/ticketly", 0));
    }
}
