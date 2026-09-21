package io.forgeloop.control.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairPackageRepository extends JpaRepository<RepairPackage, String> {
    List<RepairPackage> findByTask_IdOrderByAttemptAsc(String taskId);
}
