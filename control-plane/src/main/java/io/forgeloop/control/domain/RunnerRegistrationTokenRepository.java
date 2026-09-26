package io.forgeloop.control.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunnerRegistrationTokenRepository extends JpaRepository<RunnerRegistrationToken,String>{
  /** Serialize concurrent enrollment attempts so a token can create only one identity. */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<RunnerRegistrationToken> findByTokenHash(String tokenHash);
}
