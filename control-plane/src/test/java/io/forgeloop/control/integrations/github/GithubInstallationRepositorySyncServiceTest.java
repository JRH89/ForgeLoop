package io.forgeloop.control.integrations.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.GithubInstallationRepository;
import io.forgeloop.control.domain.GithubInstallation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubInstallationRepositorySyncServiceTest {
    private final RepositoryConnectionRepository connections = mock(RepositoryConnectionRepository.class);
    private final GithubInstallationRepository installations = mock(GithubInstallationRepository.class);
    private final GithubApi github = mock(GithubApi.class);
    private final GithubInstallationRepositorySyncService service = new GithubInstallationRepositorySyncService(
            connections, installations, github, "forgeloop", "GENERIC", "unit,browser", 25);

    @Test
    void createsConservativeConnectionForNewInstalledRepository() throws Exception {
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.empty());
        when(installations.findByInstallationId(9)).thenReturn(Optional.of(new GithubInstallation(9, "local-development")));

        service.synchronizeAddedRepositories(new ObjectMapper().readTree("""
                {"installation":{"id":9},"repositories_added":[{"full_name":"JRH89/Ticketly","default_branch":"master"}]}
                """));

        verify(connections).save(any(RepositoryConnection.class));
    }

    @Test
    void preservesExistingRepositoryPolicy() throws Exception {
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.of(
                new RepositoryConnection("local-development", "JRH89/Ticketly", 9, "master", "custom", "CUSTOM", java.util.List.of("security"), 10)));
        when(installations.findByInstallationId(9)).thenReturn(Optional.of(new GithubInstallation(9, "local-development")));

        service.synchronizeAddedRepositories(new ObjectMapper().readTree("""
                {"installation":{"id":9},"repositories_added":[{"full_name":"JRH89/Ticketly","default_branch":"master"}]}
                """));

        verify(connections, org.mockito.Mockito.never()).save(any());
    }
    @Test
    void reconcilesLegacyInstallationIdWithoutReplacingPolicy() throws Exception {
        RepositoryConnection existing = new RepositoryConnection("local-development", "JRH89/Ticketly", 1, "master", "custom", "CUSTOM", java.util.List.of("security"), 10);
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.of(existing));
        when(installations.findByInstallationId(9)).thenReturn(Optional.of(new GithubInstallation(9, "local-development")));
        service.synchronizeAddedRepositories(new ObjectMapper().readTree("""
                {"installation":{"id":9},"repositories_added":[{"full_name":"JRH89/Ticketly","default_branch":"master"}]}
                """));
        verify(connections).save(existing); org.junit.jupiter.api.Assertions.assertTrue(existing.isInstalledAs(9));
    }
    @Test
    void synchronizesRepositoriesReturnedForNewInstallation() {
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.empty());
        when(installations.findByInstallationId(9)).thenReturn(Optional.of(new GithubInstallation(9, "local-development")));
        when(github.listInstallationRepositories(9)).thenReturn(java.util.List.of(new GithubInstalledRepository("JRH89/Ticketly", "master")));
        service.synchronizeInstallation(9);
        verify(connections).save(any(RepositoryConnection.class));
    }
}
