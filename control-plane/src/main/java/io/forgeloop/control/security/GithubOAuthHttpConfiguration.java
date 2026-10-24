package io.forgeloop.control.security;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** GitHub sends CSP headers longer than Apache's default single-line limit. */
@Configuration
public class GithubOAuthHttpConfiguration {
    @Bean
    RestClientAuthorizationCodeTokenResponseClient githubTokenResponseClient() {
        // The JDK transport accepts GitHub's large headers while retaining TLS validation
        // and its bounded total-header limit. Never follow token-endpoint redirects.
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(30));
        var rest = RestClient.builder().requestFactory(transport)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(rest);
        return client;
    }
}
