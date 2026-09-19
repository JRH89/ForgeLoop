package io.forgeloop.control.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunnerRegistrationTokenRepository extends JpaRepository<RunnerRegistrationToken,String>{Optional<RunnerRegistrationToken> findByTokenHash(String tokenHash);}
