package io.forgeloop.control.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/** Defines GitHub user OAuth only when an authenticated deployment mode is selected. */
@Configuration
@ConditionalOnExpression("'${forgeloop.security.mode:production}' != 'development'")
public class GithubOAuthConfiguration {
    @Bean
    ClientRegistrationRepository githubClientRegistration(
            @Value("${forgeloop.github.client-id:}") String clientId,
            @Value("${forgeloop.github.client-secret:}") String clientSecret) {
        if (clientId.isBlank() || clientSecret.isBlank()) throw new IllegalStateException("GitHub OAuth Client ID and Client Secret are required");
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId(clientId).clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        return new InMemoryClientRegistrationRepository(github);
    }
}
