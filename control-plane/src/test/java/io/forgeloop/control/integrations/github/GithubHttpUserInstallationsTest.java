package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GithubHttpUserInstallationsTest {
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    @Test void findsAnInstallationOnALaterPageUsingTheUsersBearerToken() throws Exception {
        HttpServer server = server(200, page -> page == 1 ? installations(1, 100) : installations(101, 3));
        try {
            assertTrue(client(server).canAccess("user-token", 102L));
            assertEquals(List.of("Bearer user-token", "Bearer user-token"), authorizations);
        } finally { server.stop(0); }
    }

    @Test void reportsInstallationsTheUserCannotSee() throws Exception {
        HttpServer server = server(200, page -> installations(1, 2));
        try {
            assertFalse(client(server).canAccess("user-token", 99L));
            assertFalse(client(server).canAccess("", 1L), "a blank token is never sent");
            assertEquals(1, authorizations.size());
        } finally { server.stop(0); }
    }

    @Test void distinguishesARejectedTokenFromOtherFailures() throws Exception {
        HttpServer expired = server(401, page -> "{}");
        HttpServer outage = server(503, page -> "{}");
        try {
            assertThrows(GithubUserInstallations.GithubUserTokenRejectedException.class, () -> client(expired).canAccess("user-token", 1L));
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client(outage).canAccess("user-token", 1L));
            assertFalse(failure instanceof GithubUserInstallations.GithubUserTokenRejectedException);
        } finally { expired.stop(0); outage.stop(0); }
    }

    private static String installations(int firstId, int count) {
        return IntStream.range(firstId, firstId + count).mapToObj(id -> "{\"id\":" + id + "}")
                .collect(Collectors.joining(",", "{\"total_count\":" + count + ",\"installations\":[", "]}"));
    }

    private static GithubHttpUserInstallations client(HttpServer server) {
        return new GithubHttpUserInstallations("http://127.0.0.1:" + server.getAddress().getPort() + "/", new ObjectMapper());
    }

    private HttpServer server(int status, java.util.function.IntFunction<String> pages) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user/installations", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int page = Integer.parseInt(exchange.getRequestURI().getQuery().replaceAll(".*page=(\\d+).*", "$1"));
            byte[] content = pages.apply(page).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, content.length);
            try (var output = exchange.getResponseBody()) { output.write(content); }
        });
        server.start();
        return server;
    }
}
