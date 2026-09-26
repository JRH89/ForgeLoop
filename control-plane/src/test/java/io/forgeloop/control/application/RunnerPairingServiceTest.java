package io.forgeloop.control.application;

import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RunnerPairingServiceTest {
    final RunnerPairingRepository repository=mock(RunnerPairingRepository.class);
    final RunnerService runners=mock(RunnerService.class);
    final OperatorContext operators=mock(OperatorContext.class);
    final AuditLedgerService audit=mock(AuditLedgerService.class);
    final RunnerPairingService service=new RunnerPairingService(repository,runners,operators,audit);
    final String verifier="a".repeat(64), challenge=RunnerPairingService.challenge(verifier);
    @Test void approvalUsesAuthenticatedOrganization() {
        when(operators.organizationId()).thenReturn("trusted-org");
        assertTrue(service.approve(challenge,"My laptop"));
        verify(operators).requireAdministrator();
        verify(repository).insertApproval(eq(challenge),eq("trusted-org"),eq("My laptop"),any());
    }
    @Test void rejectsUnauthorizedAndDuplicateApproval() {
        doThrow(new org.springframework.security.access.AccessDeniedException("admin required")).when(operators).requireAdministrator();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.approve(challenge,"node"));
        verifyNoInteractions(repository);
        reset(operators); when(repository.existsById(challenge)).thenReturn(true);
        assertThrows(IllegalStateException.class,()->service.approve(challenge,"node"));
    }
    @Test void pendingDoesNotEnrollOrPersist() {
        when(repository.lockByChallenge(challenge)).thenReturn(Optional.empty());
        assertNull(service.exchange(verifier)); verifyNoInteractions(runners);
    }
    @Test void singleUseProofEnrollsOnlyApprovedTenant() {
        when(repository.lockByChallenge(challenge)).thenReturn(Optional.of(new RunnerPairing(challenge,"org","node",Instant.now().plusSeconds(60))));
        when(runners.issueRegistrationToken("org")).thenReturn("token");
        service.exchange(verifier);
        verify(runners).register(argThat(request->request.token().equals("token")&&request.name().equals("node")));
        assertThrows(IllegalStateException.class,()->service.exchange(verifier));
        verify(runners,times(1)).register(any());
    }
    @Test void expiryAndMalformedProofFailClosed() {
        when(repository.lockByChallenge(challenge)).thenReturn(Optional.of(new RunnerPairing(challenge,"org","node",Instant.now().minusSeconds(1))));
        assertThrows(IllegalStateException.class,()->service.exchange(verifier));
        assertThrows(IllegalArgumentException.class,()->service.exchange("short"));
        assertThrows(IllegalArgumentException.class,()->service.approve(challenge,"bad\nname"));
        verifyNoInteractions(runners);
    }
}
