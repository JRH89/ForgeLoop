package io.forgeloop.control.domain;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface GithubPublicationRepository extends JpaRepository<GithubPublication, String> {
    Optional<GithubPublication> findByFeatureRunId(String featureRunId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GithubPublication> findByRepositoryAndHeadSha(String repository, String headSha);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GithubPublication> findByRepositoryAndPullRequestNumber(String repository, Long pullRequestNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GithubPublication> findByAutoMergeRequestedTrueAndMergedAtIsNullAndPullRequestNumberIsNotNull();
}
