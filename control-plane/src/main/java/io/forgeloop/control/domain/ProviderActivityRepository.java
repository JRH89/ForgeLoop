package io.forgeloop.control.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderActivityRepository extends JpaRepository<ProviderActivity, String> {
    Optional<ProviderActivity> findByOrganizationIdAndActivityTypeAndSourceId(String organizationId, String activityType, String sourceId);

    @Query("select a from ProviderActivity a where a.organizationId = :organizationId and a.recordedAt >= :since "
            + "and (:repository is null or :repository = '' or a.repository = :repository) order by a.recordedAt desc")
    List<ProviderActivity> findUsage(@Param("organizationId") String organizationId,
                                    @Param("since") Instant since,
                                    @Param("repository") String repository,
                                    Pageable page);
}
