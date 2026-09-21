package io.forgeloop.control.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderAttemptRepository extends JpaRepository<ProviderAttempt, String> {
    Optional<ProviderAttempt> findByTask_IdAndRequestIdDigest(String taskId, String requestIdDigest);
    Optional<ProviderAttempt> findFirstByTask_IdOrderByRecordedAtDesc(String taskId);
    List<ProviderAttempt> findByTask_IdOrderByRecordedAtAsc(String taskId);
}
