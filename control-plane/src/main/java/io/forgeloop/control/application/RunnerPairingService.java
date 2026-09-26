package io.forgeloop.control.application;

import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Browser approval binds a single-use desktop proof to the administrator's organization. */
@Service
public class RunnerPairingService {
    private final RunnerPairingRepository pairings;
    private final RunnerService runners;
    private final OperatorContext operators;
    private final AuditLedgerService audit;
    public RunnerPairingService(RunnerPairingRepository pairings, RunnerService runners, OperatorContext operators, AuditLedgerService audit) {
        this.pairings=pairings; this.runners=runners; this.operators=operators; this.audit=audit;
    }
    @Transactional public boolean approve(String challenge, String name) {
        operators.requireAdministrator();
        requireProof(challenge);
        if (name==null || name.isBlank() || name.length()>100 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Runner name must contain 1 to 100 printable characters");
        if (pairings.existsById(challenge)) throw new IllegalStateException("Pairing was already approved; reconnect from the runner");
        // Insert-only prevents concurrent approval overwriting tenant ownership.
        pairings.insertApproval(challenge,operators.organizationId(),name,Instant.now().plusSeconds(300));
        audit.record("RUNNER_PAIRING_APPROVED","ORGANIZATION",operators.organizationId(),challenge);
        return true;
    }
    @Transactional public RunnerEnrollment exchange(String verifier) {
        requireProof(verifier);
        RunnerPairing pairing=pairings.lockByChallenge(challenge(verifier)).orElse(null);
        if (pairing==null) return null; // Pending requests do not create database rows.
        pairing.consume(Instant.now());
        String token=runners.issueRegistrationToken(pairing.getOrganizationId());
        return runners.register(new RunnerRegistration(token,pairing.getName(),"0.1.0",List.of("git","provider","docker")));
    }
    private static void requireProof(String value) {
        if (value==null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid pairing proof");
    }
    public static String challenge(String verifier) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
