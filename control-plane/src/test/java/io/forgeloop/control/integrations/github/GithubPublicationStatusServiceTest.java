package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubPublicationStatusServiceTest {
    @Test
    void returnsAndCachesClosedPullRequestState() {
        GithubApi github = mock(GithubApi.class);
        RepositoryConnectionRepository connections = mock(RepositoryConnectionRepository.class);
        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.getInstallationId()).thenReturn(42L);
        when(github.getPullRequestState(42L, "JRH89/Ticketly", 8L)).thenReturn("CLOSED");
        GithubPublication publication = new GithubPublication("run-1", "JRH89/Ticketly", "forgeloop/run-1", "run-1");
        publication.recordPullRequest(8L, false);
        GithubPublicationStatusService service = new GithubPublicationStatusService(github, connections);

        assertEquals("CLOSED", service.state(publication));
        assertEquals("CLOSED", service.state(publication));

        verify(github).getPullRequestState(42L, "JRH89/Ticketly", 8L);
    }

    @Test
    void trustsPersistedMergeReceiptWithoutCallingGitHub() {
        GithubApi github = mock(GithubApi.class);
        GithubPublicationStatusService service = new GithubPublicationStatusService(github, mock(RepositoryConnectionRepository.class));
        GithubPublication publication = new GithubPublication("run-1", "JRH89/Ticketly", "forgeloop/run-1", "run-1");
        publication.recordPullRequest(8L, false);
        publication.recordMerge("merge-sha");

        assertEquals("MERGED", service.state(publication));
    }
}
