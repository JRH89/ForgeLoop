package io.forgeloop.control.integrations.github;

import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reads current pull-request state without repeatedly spending GitHub API quota during console refreshes. */
@Service
public class GithubPublicationStatusService {
    private static final Logger log = LoggerFactory.getLogger(GithubPublicationStatusService.class);
    private static final long CACHE_NANOS = Duration.ofSeconds(30).toNanos();
    private final GithubApi github;
    private final RepositoryConnectionRepository connections;
    private final ConcurrentHashMap<String, CachedState> cache = new ConcurrentHashMap<>();

    public GithubPublicationStatusService(GithubApi github, RepositoryConnectionRepository connections) {
        this.github = github;
        this.connections = connections;
    }

    public String state(GithubPublication publication) {
        if (publication.getPullRequestNumber() == null) return "NOT_CREATED";
        if (publication.getMergedAt() != null) return "MERGED";

        String key = publication.getRepository() + "#" + publication.getPullRequestNumber();
        long now = System.nanoTime();
        CachedState cached = cache.get(key);
        if (cached != null && cached.expiresAtNanos() > now) return cached.state();

        String state;
        try {
            RepositoryConnection connection = connections.findByRepository(publication.getRepository())
                    .filter(RepositoryConnection::isEnabled)
                    .orElseThrow(() -> new IllegalStateException("Repository is not currently connected"));
            state = github.getPullRequestState(connection.getInstallationId(), publication.getRepository(), publication.getPullRequestNumber());
        } catch (RuntimeException exception) {
            log.warn("Could not refresh GitHub pull-request status for {}: {}", key, exception.getMessage());
            state = cached == null ? "UNKNOWN" : cached.state();
        }
        cache.put(key, new CachedState(state, now + CACHE_NANOS));
        return state;
    }

    private record CachedState(String state, long expiresAtNanos) { }
}