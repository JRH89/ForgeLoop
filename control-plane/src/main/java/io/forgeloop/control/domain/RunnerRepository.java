package io.forgeloop.control.domain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunnerRepository extends JpaRepository<Runner, String> { List<Runner> findByOrganizationId(String organizationId); }
