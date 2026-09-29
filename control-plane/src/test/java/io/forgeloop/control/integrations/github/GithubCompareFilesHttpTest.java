package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the compare adapter's exact request and returned blob identities without GitHub credentials. */
class GithubCompareFilesHttpTest {
    @Test
    void adapterWithoutCompareSupportFailsClosed() {
        GithubApi adapter = mock(GithubApi.class, CALLS_REAL_METHODS);

        assertThrows(UnsupportedOperationException.class,
                () -> adapter.compareFiles(12, "acme/project", "main", "a".repeat(40)));
    }

    @Test
    void readsCompareFileBlobsWithInstallationAuthorizationAndEncodedBaseBranch() throws Exception {
        String head = "a".repeat(40);
        String blob = "b".repeat(40);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> request = new ArrayList<>();
        server.createContext("/", exchange -> {
            request.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            request.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"files\":[{\"filename\":\"tests/NewTest.java\",\"status\":\"modified\",\"sha\":\""
                    + blob + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            GithubHttpApi api = api(server);

            List<GithubChangedFile> changedFiles = api.compareFiles(12, "acme/project", "release/v1", head);

            assertEquals(List.of(new GithubChangedFile("tests/NewTest.java", "modified", blob)), changedFiles);
            assertEquals(List.of("GET /repos/acme/project/compare/release%2Fv1..." + head,
                    "Bearer test-installation-token"), request);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsIncompleteCompareResponsesAndMalformedFiles() throws Exception {
        HttpServer server = server("{\"status\":\"ahead\"}");
        server.start();
        try {
            GithubHttpApi api = api(server);
            assertThrows(IllegalStateException.class, () -> api.compareFiles(12, "acme/project", "main", "a".repeat(40)));
        } finally {
            server.stop(0);
        }
    }

    private static GithubHttpApi api(HttpServer server) {
        return new GithubHttpApi("http://127.0.0.1:" + server.getAddress().getPort(), "", "", new ObjectMapper()) {
            @Override public String issueInstallationToken(long installationId) {
                assertEquals(12, installationId);
                return "test-installation-token";
            }
        };
    }

    private static HttpServer server(String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        return server;
    }
}
