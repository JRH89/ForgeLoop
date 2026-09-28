package io.forgeloop.control.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface RepositoryScanRepository extends JpaRepository<RepositoryScan, String> {
    @EntityGraph(attributePaths = "findings")
    List<RepositoryScan> findTop10ByOrganizationIdAndRepositoryOrderByCreatedAtDesc(String organizationId, String repository);
    @EntityGraph(attributePaths = "findings")
    Optional<RepositoryScan> findByIdAndOrganizationId(String id, String organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "findings")
    @Query("select s from RepositoryScan s where s.id = :id and s.organizationId = :organizationId")
    Optional<RepositoryScan> lockByIdAndOrganizationId(@Param("id") String id, @Param("organizationId") String organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RepositoryScan s where s.id = :id")
    Optional<RepositoryScan> lockById(@Param("id") String id);
    boolean existsByOrganizationIdAndRepositoryAndStatusIn(String organizationId, String repository, List<String> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RepositoryScan s where s.organizationId = :organizationId and (s.status = 'PENDING' or (s.status = 'RUNNING' and s.startedAt < :staleBefore)) order by s.createdAt asc")
    List<RepositoryScan> lockClaimableForOrganization(@Param("organizationId") String organizationId,
            @Param("staleBefore") java.time.Instant staleBefore, org.springframework.data.domain.Pageable page);
}
