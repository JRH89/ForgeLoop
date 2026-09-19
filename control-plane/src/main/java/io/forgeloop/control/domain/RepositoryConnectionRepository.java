package io.forgeloop.control.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryConnectionRepository extends JpaRepository<RepositoryConnection, String> {
  Optional<RepositoryConnection> findByRepository(String repository);
}
