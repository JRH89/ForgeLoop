package io.forgeloop.control.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositoryIssueProposalRepository extends JpaRepository<RepositoryIssueProposal, String> {
    boolean existsByFinding_IdAndStatusIn(String findingId, List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "finding")
    @Query("select p from RepositoryIssueProposal p where p.id = :id and p.organizationId = :organizationId")
    Optional<RepositoryIssueProposal> lockByIdAndOrganizationId(@Param("id") String id,
                                                                 @Param("organizationId") String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "finding")
    @Query("select p from RepositoryIssueProposal p where p.organizationId = :organizationId and "
            + "(p.status = 'PENDING' or (p.status = 'RUNNING' and p.startedAt < :staleBefore)) order by p.createdAt asc")
    List<RepositoryIssueProposal> lockClaimableForOrganization(@Param("organizationId") String organizationId,
                                                                @Param("staleBefore") Instant staleBefore,
                                                                Pageable page);
}
