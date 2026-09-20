package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RepositoryConnectionServiceTest {
  private final RepositoryConnectionRepository repository = Mockito.mock(RepositoryConnectionRepository.class); private final RepositoryConnectionService service = new RepositoryConnectionService(repository, new OperatorContext("development", "local-development", Mockito.mock(OrganizationMembershipRepository.class)));
  @Test void registersARepositoryWithPolicy() { when(repository.findByRepository("acme/support")).thenReturn(Optional.empty()); when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0)); RepositoryConnection connection = service.register(new RepositoryRegistration("acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile", "browser"), 20)); assertEquals("forgeloop", connection.getIssueLabel()); assertEquals(List.of("compile", "browser"), connection.getRequiredGates()); }
  @Test void rejectsDuplicateRepository() { when(repository.findByRepository("acme/support")).thenReturn(Optional.of(new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); assertThrows(IllegalStateException.class, () -> service.register(new RepositoryRegistration("acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); }
  @Test void rejectsConnectionOwnedByAnotherOrganization() { when(repository.findByRepository("other/support")).thenReturn(Optional.of(new RepositoryConnection("other", "other/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); assertThrows(IllegalStateException.class, () -> service.requireEnabled("other/support")); }
}
