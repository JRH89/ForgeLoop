package io.forgeloop.control.domain;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RepositoryConnectionRepository extends JpaRepository<RepositoryConnection, String> {
  Optional<RepositoryConnection> findByRepository(String repository);
  /** Fetches the policy collection inside the repository query so GraphQL never crosses a closed session. */
  @EntityGraph(attributePaths = "verificationPolicies")
  List<RepositoryConnection> findByOrganizationId(String organizationId);
}
