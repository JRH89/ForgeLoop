package io.forgeloop.control.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueConversationRepository extends JpaRepository<IssueConversation, String> {
    List<IssueConversation> findTop50ByOrganizationIdAndRepositoryOrderByUpdatedAtDesc(String organizationId, String repository);
    Optional<IssueConversation> findByIdAndOrganizationId(String id, String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IssueConversation c where c.id = :id and c.organizationId = :organizationId")
    Optional<IssueConversation> lockByIdAndOrganizationId(@Param("id") String id, @Param("organizationId") String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IssueConversation c where c.organizationId = :organizationId and (c.status = 'PENDING' or (c.status = 'RUNNING' and c.startedAt < :staleBefore)) order by c.createdAt asc")
    List<IssueConversation> lockClaimableForOrganization(@Param("organizationId") String organizationId,
                                                          @Param("staleBefore") Instant staleBefore, Pageable page);
}
