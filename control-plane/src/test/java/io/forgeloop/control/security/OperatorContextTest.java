package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OperatorContextTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void readsOrganizationOnlyFromValidatedJwtClaim() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1", "org_id", "acme"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertEquals("acme", new OperatorContext("production", "local").organizationId());
    }

    @Test void permitsFallbackOnlyInDevelopment() {
        assertEquals("local", new OperatorContext("development", "local").organizationId());
        assertThrows(AccessDeniedException.class, () -> new OperatorContext("production", "local").organizationId());
    }

    @Test void requiresAdministratorAuthorityOutsideDevelopment() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1", "org_id", "acme"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThrows(AccessDeniedException.class, () -> new OperatorContext("production", "local").requireAdministrator());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new OperatorContext("production", "local").requireAdministrator());
    }
}
