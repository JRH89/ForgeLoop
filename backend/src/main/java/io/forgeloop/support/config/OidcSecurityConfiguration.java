package io.forgeloop.support.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.*; import org.springframework.security.config.Customizer; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.web.SecurityFilterChain;
@Configuration @ConditionalOnProperty(name="forgeloop.auth.mode",havingValue="oidc",matchIfMissing=true) public class OidcSecurityConfiguration {
 @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception { return http.csrf(csrf->csrf.disable()).authorizeHttpRequests(requests->requests.requestMatchers("/actuator/health/**","/actuator/info").permitAll().anyRequest().authenticated()).oauth2ResourceServer(oauth2->oauth2.jwt(Customizer.withDefaults())).build(); }
}
