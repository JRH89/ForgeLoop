package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.OperatorRole;
import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class OperatorContextTest {
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
    @Test void readsOrganizationOnlyForPersistedMembership() {
        Jwt jwt = jwt(); SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(memberships.findByOrganization_IdAndSubject("acme", "user-1")).thenReturn(Optional.of(new OrganizationMembership(new Organization("acme", "Acme"), "user-1", OperatorRole.ADMIN)));
        assertEquals("acme", context("production").organizationId());
    }
    @Test void rejectsUnprovisionedJwtMembership() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt()));
        assertThrows(AccessDeniedException.class, () -> context("production").organizationId());
    }
    @Test void permitsFallbackOnlyInDevelopment() {
        assertEquals("local", context("development").organizationId());
        assertThrows(AccessDeniedException.class, () -> context("production").organizationId());
    }
    @Test void requiresPersistedAdministratorRole() {
        Jwt jwt = jwt(); SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(memberships.findByOrganization_IdAndSubject("acme", "user-1")).thenReturn(Optional.of(new OrganizationMembership(new Organization("acme", "Acme"), "user-1", OperatorRole.OPERATOR)));
        assertThrows(AccessDeniedException.class, () -> context("production").requireAdministrator());
        when(memberships.findByOrganization_IdAndSubject("acme", "user-1")).thenReturn(Optional.of(new OrganizationMembership(new Organization("acme", "Acme"), "user-1", OperatorRole.ADMIN)));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> context("production").requireAdministrator());
    }
    @Test void switchesOnlyToAnOrganizationContainingTheGithubIdentity() {
        SecurityContextHolder.getContext().setAuthentication(oauth());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        OrganizationMembership owned = new OrganizationMembership(new Organization("owned", "Owned"), "github:42", OperatorRole.ADMIN);
        OrganizationMembership invited = OrganizationMembership.invitation(new Organization("invited", "Invited"), "github:42", "octocat", OperatorRole.VIEWER);
        when(memberships.findBySubjectOrderByIdAsc("github:42")).thenReturn(List.of(owned, invited));
        when(memberships.findByOrganization_IdAndSubject("invited", "github:42")).thenReturn(Optional.of(invited));
        OperatorContext context = context("github");
        assertEquals("owned", context.organizationId());
        context.selectOrganization("invited");
        assertEquals("invited", context.organizationId());
        assertEquals(OperatorRole.VIEWER, context.role());
        org.junit.jupiter.api.Assertions.assertTrue(invited.isAccepted());
        assertThrows(AccessDeniedException.class, () -> context.selectOrganization("stranger"));
        assertEquals("invited", context.organizationId());
    }
    private OperatorContext context(String mode) { return new OperatorContext(mode, "local", memberships); }
    private Jwt jwt() { return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1", "org_id", "acme")); }
    private OAuth2AuthenticationToken oauth() {
        var authority = new SimpleGrantedAuthority("USER");
        return new OAuth2AuthenticationToken(new DefaultOAuth2User(List.of(authority), Map.of("id", 42, "login", "octocat"), "id"), List.of(authority), "github");
    }
}
