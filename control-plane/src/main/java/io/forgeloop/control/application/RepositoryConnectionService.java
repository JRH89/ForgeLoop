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
  private final AuditLedgerService audit;
  public RepositoryConnectionService(RepositoryConnectionRepository connections, OperatorContext operators, AuditLedgerService audit) { this.connections = connections; this.operators = operators; this.audit = audit; }
  @Transactional public RepositoryConnection register(RepositoryRegistration input) {
    if (connections.findByRepository(input.repository()).isPresent()) throw new IllegalStateException("Repository is already connected");
    RepositoryConnection connection = new RepositoryConnection(operators.organizationId(), input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd());
    if (!input.verificationPolicies().isEmpty()) connection.replaceVerificationPolicies(input.verificationPolicies());
    RepositoryConnection saved = connections.save(connection);
    audit.record("REPOSITORY_CONNECTED", "REPOSITORY_CONNECTION", saved.getId() == null ? input.repository() : saved.getId(), input.repository());
    return saved;
  }
  @Transactional public RepositoryConnection configureVerification(String repository, List<io.forgeloop.control.domain.VerificationPolicySpec> policies) {
    RepositoryConnection connection = requireEnabled(repository); connection.replaceVerificationPolicies(policies);
    RepositoryConnection saved = connections.save(connection); audit.record("REPOSITORY_VERIFICATION_POLICY_UPDATED", "REPOSITORY_CONNECTION", saved.getId(), "revision=" + saved.getPolicyRevision()); return saved;
  }
  public RepositoryConnection requireEnabled(String repository) {
    RepositoryConnection connection = connections.findByRepository(repository).orElseThrow(() -> new IllegalStateException("Repository is not connected"));
    if (!connection.belongsTo(operators.organizationId()) || !connection.isEnabled()) throw new IllegalStateException("Repository connection is unavailable");
    return connection;
  }
  @Transactional(readOnly = true)
  public List<RepositoryConnection> list() { return connections.findByOrganizationId(operators.organizationId()); }
  @Transactional public RepositoryConnection configureIntake(String repository, String requiredAssignee) {
    operators.requireAdministrator();
    RepositoryConnection connection = requireEnabled(repository);
    connection.configureRequiredAssignee(requiredAssignee);
    audit.record("REPOSITORY_INTAKE_UPDATED", "REPOSITORY_CONNECTION", connection.getId(), "revision=" + connection.getPolicyRevision());
    return connection;
  }
}
