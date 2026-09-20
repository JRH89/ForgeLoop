package io.forgeloop.control.integrations.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubInstallationRepositorySyncServiceTest {
    private final RepositoryConnectionRepository connections = mock(RepositoryConnectionRepository.class);
    private final GithubInstallationRepositorySyncService service = new GithubInstallationRepositorySyncService(
            connections, "forgeloop", "GENERIC", "unit,browser", 25);

    @Test
    void createsConservativeConnectionForNewInstalledRepository() throws Exception {
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.empty());

        service.synchronizeAddedRepositories(new ObjectMapper().readTree("""
                {"installation":{"id":9},"repositories_added":[{"full_name":"JRH89/Ticketly","default_branch":"master"}]}
                """));

        verify(connections).save(any(RepositoryConnection.class));
    }

    @Test
    void preservesExistingRepositoryPolicy() throws Exception {
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.of(
                new RepositoryConnection("JRH89/Ticketly", 9, "master", "custom", "CUSTOM", java.util.List.of("security"), 10)));

        service.synchronizeAddedRepositories(new ObjectMapper().readTree("""
                {"installation":{"id":9},"repositories_added":[{"full_name":"JRH89/Ticketly","default_branch":"master"}]}
                """));

        verify(connections, org.mockito.Mockito.never()).save(any());
    }
}
