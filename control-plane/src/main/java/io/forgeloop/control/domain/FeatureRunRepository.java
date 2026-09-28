package io.forgeloop.control.domain;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureRunRepository extends JpaRepository<FeatureRun, String> {
  Optional<FeatureRun> findByRepositoryAndSourceRef(String repository, String sourceRef);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select run from FeatureRun run where run.id = :id")
  Optional<FeatureRun> findByIdForUpdate(@Param("id") String id);
}
