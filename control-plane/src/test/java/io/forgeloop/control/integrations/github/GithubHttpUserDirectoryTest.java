package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GithubHttpUserDirectoryTest {
    @Test void resolvesCanonicalLoginAndImmutableId() throws Exception {
        HttpServer server = server(200, "{\"id\":42,\"login\":\"Octocat\",\"type\":\"User\"}");
        try {
            GithubUserDirectory.GithubUser user = directory(server).findByLogin("octocat");
            assertEquals(42L, user.id());
            assertEquals("Octocat", user.login());
        } finally { server.stop(0); }
    }

    @Test void rejectsMissingUsersBotsAndInvalidInput() throws Exception {
        HttpServer missing = server(404, "{}");
        HttpServer bot = server(200, "{\"id\":99,\"login\":\"a-bot\",\"type\":\"Bot\"}");
        try {
            assertThrows(IllegalArgumentException.class, () -> directory(missing).findByLogin("absent"));
            assertThrows(IllegalArgumentException.class, () -> directory(bot).findByLogin("a-bot"));
            assertThrows(IllegalArgumentException.class, () -> directory(bot).findByLogin("../admin"));
        } finally { missing.stop(0); bot.stop(0); }
    }

    private static GithubHttpUserDirectory directory(HttpServer server) {
        return new GithubHttpUserDirectory("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
    }

    private static HttpServer server(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/", exchange -> {
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, content.length);
            try (var output = exchange.getResponseBody()) { output.write(content); }
        });
        server.start();
        return server;
    }
}
