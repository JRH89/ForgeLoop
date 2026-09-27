package io.forgeloop.control.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.forgeloop.control.domain.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class SupportIdentityTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void anonymousNeverInheritsDevelopmentAdministrator(){
        var identity=new SupportIdentity(mock(OrganizationMembershipRepository.class),"owner");
        assertNull(identity.subject());assertFalse(identity.administrator());assertThrows(ResponseStatusException.class,identity::requireAdministrator);
    }
    @Test void customerAdminCannotEnterServiceOwnerInbox(){
        var repo=mock(OrganizationMembershipRepository.class);var identity=new SupportIdentity(repo,"owner");
        var token=Jwt.withTokenValue("test").header("alg","none").subject("github:123").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("org_id","customer").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,java.util.List.of()));
        when(repo.findByOrganization_IdAndSubject("owner","github:123")).thenReturn(Optional.empty());
        assertFalse(identity.administrator());assertThrows(ResponseStatusException.class,identity::requireAdministrator);
        when(repo.findByOrganization_IdAndSubject("owner","github:123")).thenReturn(Optional.of(new OrganizationMembership(new Organization("owner","Owner"),"github:123",OperatorRole.ADMIN)));
        assertTrue(identity.administrator());assertDoesNotThrow(identity::requireAdministrator);
    }
    @Test void blankAuthenticatedSubjectCannotListAllCustomerTickets(){
        var token=Jwt.withTokenValue("test").header("alg","none").subject(" ").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,java.util.List.of()));
        var identity=new SupportIdentity(mock(OrganizationMembershipRepository.class),"owner");
        assertNull(identity.subject());assertThrows(ResponseStatusException.class,identity::requireSubject);
    }
    @Test void githubSessionUsesImmutableNumericIdentity(){
        var principal=new org.springframework.security.oauth2.core.user.DefaultOAuth2User(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),java.util.Map.of("id",123,"login","renameable-login"),"login");
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,principal.getAuthorities(),"github"));
        assertEquals("github:123",new SupportIdentity(mock(OrganizationMembershipRepository.class),"owner").subject());
    }
}
