package io.forgeloop.control.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RunnerPairingRepository extends JpaRepository<RunnerPairing,String> {
    @Modifying
    @Query(value="insert into runner_pairing (challenge,organization_id,name,expires_at) values (:challenge,:organization,:name,:expires)",nativeQuery=true)
    void insertApproval(@Param("challenge") String challenge,@Param("organization") String organization,@Param("name") String name,@Param("expires") java.time.Instant expires);
    /** Concurrent exchanges must never create two identities from one approval. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RunnerPairing p where p.challenge = :challenge")
    Optional<RunnerPairing> lockByChallenge(@Param("challenge") String challenge);
}
