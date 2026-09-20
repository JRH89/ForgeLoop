package io.forgeloop.control.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keeps local development explicitly opt-in while production requires a JWT for every operator route.
 * GitHub webhooks remain HMAC-authenticated because GitHub does not send the operator's OIDC token.
 */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${forgeloop.security.mode:production}") String mode) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if ("development".equals(mode)) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else if (!"production".equals(mode)) {
            throw new IllegalStateException("FORGELOOP_SECURITY_MODE must be development or production");
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health", "/api/github/webhooks").permitAll()
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
