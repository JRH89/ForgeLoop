package io.forgeloop.control.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps local development explicitly opt-in while production requires a JWT for every operator route.
 * GitHub webhooks remain HMAC-authenticated because GitHub does not send the operator's OIDC token.
 */
@Configuration
public class SecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    private static void configureGithubLogin(org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer<HttpSecurity> login,
                                             GithubLoginSuccessHandler githubSuccess) {
        login.successHandler(githubSuccess).failureHandler((request, response, exception) -> {
            log.warn("GitHub OAuth login failed: {}", exception.getMessage(), exception);
            response.sendRedirect("/?login=failed");
        });
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${forgeloop.security.mode:production}") String mode,
                                                 @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                                                 @Value("${forgeloop.security.oidc-audience:}") String audience,
                                                 GithubLoginSuccessHandler githubSuccess) throws Exception {
        http.csrf(csrf -> csrf.disable());

        switch (mode) {
            case "development" -> http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            case "github" -> {
                authenticatedRoutes(http);
                http.oauth2Login(login -> configureGithubLogin(login, githubSuccess));
                http.logout(logout -> logout.logoutSuccessUrl("/").deleteCookies("JSESSIONID").invalidateHttpSession(true));
            }
            case "production" -> {
                authenticatedRoutes(http);
                http.oauth2Login(login -> configureGithubLogin(login, githubSuccess));
                http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(issuerAudienceDecoder(issuerUri, audience))));
                http.logout(logout -> logout.logoutSuccessUrl("/").deleteCookies("JSESSIONID").invalidateHttpSession(true));
            }
            default -> throw new IllegalStateException("FORGELOOP_SECURITY_MODE must be development, github, or production");
        }

        return http.build();
    }

    private static void authenticatedRoutes(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**", "/graphql", "/api/auth/session", "/api/github/webhooks", "/api/github/app/callback", "/api/runner/artifacts", "/api/runner/events", "/oauth2/**", "/login/**", "/error").permitAll()
                .anyRequest().authenticated());
    }

    /** Validates both issuer and audience so a valid token for another API cannot reach GraphQL. */
    private JwtDecoder issuerAudienceDecoder(String issuerUri, String audience) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator));
        return decoder;
    }
}
