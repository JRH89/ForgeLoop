package io.forgeloop.control.domain;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RepositoryConnectionRepository extends JpaRepository<RepositoryConnection, String> {
  Optional<RepositoryConnection> findByRepository(String repository);
  /** Reattaches a webhook-validated connection with its lazy policy collection initialized. */
  @EntityGraph(attributePaths = "verificationPolicies")
  @Query("select connection from RepositoryConnection connection where connection.id = :id")
  Optional<RepositoryConnection> findForIssueIntakeById(@Param("id") String id);
  /** Fetches the policy collection inside the repository query so GraphQL never crosses a closed session. */
  @EntityGraph(attributePaths = "verificationPolicies")
  List<RepositoryConnection> findByOrganizationId(String organizationId);
}
