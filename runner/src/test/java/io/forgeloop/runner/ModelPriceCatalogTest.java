package io.forgeloop.runner;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelPriceCatalogTest {
    @Test void looksUpExactProviderAndModelAndCachesThePublicFeed() throws Exception {
        var hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/prices", exchange -> {
            hits.incrementAndGet();
            byte[] body = ("""
                {"claude-sonnet-5":{"litellm_provider":"anthropic","mode":"chat","input_cost_per_token":0.000002,"output_cost_per_token":0.00001,"source":"https://platform.claude.com/docs/en/about-claude/pricing"},
                 "gemini/gemini-test":{"litellm_provider":"gemini","mode":"chat","input_cost_per_token":0.000001,"output_cost_per_token":0.000004,"source":"https://ai.google.dev/gemini-api/docs/pricing"},
                 "gpt-test":{"litellm_provider":"reseller","mode":"chat","input_cost_per_token":0.000002,"output_cost_per_token":0.00001,"source":"https://developers.openai.com/api/docs/pricing"},
                 "gpt-bad-source":{"litellm_provider":"openai","mode":"chat","input_cost_per_token":0.000002,"output_cost_per_token":0.00001,"source":"https://example.com/prices"}}
                """).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var catalog = new ModelPriceCatalog(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/prices"), Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));
            var sonnet = catalog.find("anthropic", "claude-sonnet-5").orElseThrow();
            assertEquals("2", sonnet.inputUsdPerMillion().stripTrailingZeros().toPlainString());
            assertEquals("10", sonnet.outputUsdPerMillion().stripTrailingZeros().toPlainString());
            assertEquals("2026-09-27T12:00:00Z", sonnet.checkedAt());
            assertTrue(catalog.find("openai", "gpt-test").isEmpty());
            assertTrue(catalog.find("openai", "gpt-bad-source").isEmpty());
            assertTrue(catalog.find("gemini", "gemini-test").isPresent());
            assertEquals(1, hits.get());
        } finally { server.stop(0); }
    }
}
