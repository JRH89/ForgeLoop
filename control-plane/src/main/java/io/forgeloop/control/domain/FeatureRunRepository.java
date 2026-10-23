package io.forgeloop.control.domain;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureRunRepository extends JpaRepository<FeatureRun, String> {
  // Filter ownership and enabled connections in SQL before any display graph is loaded.
  String VISIBLE = "select run from FeatureRun run where run.organizationId = :organizationId "
      + "and exists (select connection.id from RepositoryConnection connection "
      + "where connection.repository = run.repository and connection.organizationId = run.organizationId "
      + "and connection.enabled = true) ";

  @Query(VISIBLE + "order by run.createdAt desc, run.id desc")
  java.util.List<FeatureRun> findVisible(@Param("organizationId") String organizationId);

  @Query(VISIBLE + "and (:archived is null or run.archived = :archived) order by run.createdAt desc, run.id desc")
  java.util.List<FeatureRun> findVisiblePage(@Param("organizationId") String organizationId,
      @Param("archived") Boolean archived, org.springframework.data.domain.Pageable pageable);

  Optional<FeatureRun> findByRepositoryAndSourceRef(String repository, String sourceRef);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select run from FeatureRun run where run.id = :id")
  Optional<FeatureRun> findByIdForUpdate(@Param("id") String id);
}
