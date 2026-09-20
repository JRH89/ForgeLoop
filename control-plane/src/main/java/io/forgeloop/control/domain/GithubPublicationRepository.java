package io.forgeloop.control.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubPublicationRepository extends JpaRepository<GithubPublication, String> {
    Optional<GithubPublication> findByFeatureRunId(String featureRunId);
}
