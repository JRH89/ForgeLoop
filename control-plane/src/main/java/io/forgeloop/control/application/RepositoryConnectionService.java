package io.forgeloop.control.application;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.security.OperatorContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ensures every operator-facing repository operation is bound to the caller's organization. */
@Service
public class RepositoryConnectionService {
  private final RepositoryConnectionRepository connections;
  private final OperatorContext operators;
  public RepositoryConnectionService(RepositoryConnectionRepository connections, OperatorContext operators) { this.connections = connections; this.operators = operators; }
  @Transactional public RepositoryConnection register(RepositoryRegistration input) {
    if (connections.findByRepository(input.repository()).isPresent()) throw new IllegalStateException("Repository is already connected");
    return connections.save(new RepositoryConnection(operators.organizationId(), input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd()));
  }
  public RepositoryConnection requireEnabled(String repository) {
    RepositoryConnection connection = connections.findByRepository(repository).orElseThrow(() -> new IllegalStateException("Repository is not connected"));
    if (!connection.belongsTo(operators.organizationId()) || !connection.isEnabled()) throw new IllegalStateException("Repository connection is unavailable");
    return connection;
  }
  public List<RepositoryConnection> list() { String organizationId = operators.organizationId(); return connections.findAll().stream().filter(connection -> connection.belongsTo(organizationId)).toList(); }
}
