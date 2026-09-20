package io.forgeloop.control.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, String> { Optional<GithubInstallation> findByInstallationId(long installationId); }
