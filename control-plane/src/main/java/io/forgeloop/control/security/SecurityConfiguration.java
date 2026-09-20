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
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${forgeloop.security.mode:production}") String mode,
                                            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                                            @Value("${forgeloop.security.oidc-audience:}") String audience) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if ("development".equals(mode)) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else if (!"production".equals(mode)) {
            throw new IllegalStateException("FORGELOOP_SECURITY_MODE must be development or production");
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health", "/api/github/webhooks").permitAll()
                    .anyRequest().authenticated());
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(issuerAudienceDecoder(issuerUri, audience))));
        }
        return http.build();
    }

    /** Validates both issuer and audience so a valid token for another API cannot reach GraphQL. */
    private JwtDecoder issuerAudienceDecoder(String issuerUri, String audience) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator));
        return decoder;
    }
}
