package io.forgeloop.control.application;
import io.forgeloop.control.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class RunnerService { private final RunnerRepository runners; private final RunnerRegistrationTokenRepository tokens; private final SecureRandom random=new SecureRandom(); public RunnerService(RunnerRepository runners,RunnerRegistrationTokenRepository tokens){this.runners=runners;this.tokens=tokens;} @Transactional public String issueRegistrationToken(String organizationId){if(organizationId==null||organizationId.isBlank())throw new IllegalArgumentException("Organization is required");byte[] bytes=new byte[32];random.nextBytes(bytes);String raw=HexFormat.of().formatHex(bytes);tokens.save(new RunnerRegistrationToken(hash(raw),organizationId,Instant.now().plus(Duration.ofMinutes(15))));return raw;} @Transactional public Runner register(RunnerRegistration request){RunnerRegistrationToken token=tokens.findByTokenHash(hash(request.token())).orElseThrow(()->new IllegalArgumentException("Invalid registration token"));token.consume();return runners.save(new Runner(token.getOrganizationId(),request.name(),request.version(),request.capabilities()));} @Transactional public Runner heartbeat(String runnerId){Runner runner=runners.findById(runnerId).orElseThrow(()->new IllegalArgumentException("Runner not found"));runner.heartbeat();return runner;} public List<Runner> list(String organizationId){return runners.findByOrganizationId(organizationId);} private String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("SHA-256 unavailable",ex);}} }
