package io.forgeloop.control.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<Organization, String> { @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) java.util.Optional<Organization> findForUpdateById(String id); }
