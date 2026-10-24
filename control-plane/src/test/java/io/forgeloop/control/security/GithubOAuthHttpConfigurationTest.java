package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

class GithubOAuthHttpConfigurationTest {
    @Test void exchangesCodeWithLargeGithubHeadersAndPreservesOAuthErrors() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            var body = "{\"access_token\":\"test-token\",\"token_type\":\"bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Security-Policy", "default-src 'none'; " + "x".repeat(20000));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/error", exchange -> {
            var body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var client = new GithubOAuthHttpConfiguration().githubTokenResponseClient();
            assertEquals("test-token", client.getTokenResponse(request(server, "/token")).getAccessToken().getTokenValue());
            assertThrows(OAuth2AuthorizationException.class, () -> client.getTokenResponse(request(server, "/error")));
        } finally { server.stop(0); }
    }

    private OAuth2AuthorizationCodeGrantRequest request(HttpServer server, String path) {
        var registration = ClientRegistration.withRegistrationId("github").clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/authorize").tokenUri("http://127.0.0.1:" + server.getAddress().getPort() + path).build();
        var authorization = OAuth2AuthorizationRequest.authorizationCode().clientId("test-client")
                .authorizationUri("http://localhost/authorize").redirectUri("http://localhost/callback").state("state").build();
        var response = OAuth2AuthorizationResponse.success("code").redirectUri("http://localhost/callback").state("state").build();
        return new OAuth2AuthorizationCodeGrantRequest(registration, new OAuth2AuthorizationExchange(authorization, response));
    }
}
