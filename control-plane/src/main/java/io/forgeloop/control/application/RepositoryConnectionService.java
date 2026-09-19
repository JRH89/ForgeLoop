package io.forgeloop.control.application;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryConnectionService {
  private final RepositoryConnectionRepository connections;
  public RepositoryConnectionService(RepositoryConnectionRepository connections) { this.connections = connections; }
  @Transactional public RepositoryConnection register(RepositoryRegistration input) {
    if (connections.findByRepository(input.repository()).isPresent()) throw new IllegalStateException("Repository is already connected");
    return connections.save(new RepositoryConnection(input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd()));
  }
  public RepositoryConnection requireEnabled(String repository) {
    RepositoryConnection connection = connections.findByRepository(repository).orElseThrow(() -> new IllegalStateException("Repository is not connected"));
    if (!connection.isEnabled()) throw new IllegalStateException("Repository connection is disabled");
    return connection;
  }
  public List<RepositoryConnection> list() { return connections.findAll(); }
}
