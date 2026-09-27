package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the real REST adapter against a local stub; no GitHub or provider credentials. */
class GithubIssueReaderHttpTest {
    @Test void readsOnlyTheRequestedIssueWithInstallationAuthorization() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> receipts = new ArrayList<>();
        server.createContext("/", exchange -> {
            receipts.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            receipts.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"number\":7,\"state\":\"open\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var api = new GithubHttpApi("http://127.0.0.1:" + server.getAddress().getPort(), "", "", new ObjectMapper()) {
                @Override public String issueInstallationToken(long installationId) {
                    assertEquals(12, installationId);
                    return "test-installation-token";
                }
            };
            assertEquals(7, api.readIssue(12, "acme/project", 7).path("number").asInt());
            assertEquals(List.of("GET /repos/acme/project/issues/7", "Bearer test-installation-token"), receipts);
            assertThrows(IllegalArgumentException.class, () -> api.readIssue(12, "acme/project?redirect=bad", 7));
            assertThrows(IllegalArgumentException.class, () -> api.readIssue(12, "acme/project", 0));
            assertEquals(2, receipts.size());
        } finally { server.stop(0); }
    }
}
