package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.util.List;

/** Immutable run-level snapshot of one repository verification policy. */
@Entity
public class VerificationGate {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private FeatureRun run;
    @Column(nullable = false, length = 80) private String name;
    private boolean required = true;
    @Enumerated(EnumType.STRING) private VerificationGateState state = VerificationGateState.PENDING;
    @Column(length = 40) private String kind;
    @Column(length = 255) private String imageDigest;
    @Column(length = 8000) private String command;
    @Column(length = 20) private String networkPolicy;
    private Integer timeoutSeconds;
    @Column(length = 20) private String criterionCoverage;

    protected VerificationGate() { }
    VerificationGate(FeatureRun run, VerificationPolicySpec policy) {
        this.run = run; this.name = policy.name(); this.required = policy.required(); this.kind = policy.kind();
        this.imageDigest = policy.imageDigest(); this.command = String.join("\n", policy.command());
        this.networkPolicy = policy.networkPolicy(); this.timeoutSeconds = policy.timeoutSeconds();
        this.criterionCoverage = policy.criterionCoverage();
    }
    /** Compatibility constructor for narrow domain tests; production runs use detailed policies. */
    VerificationGate(FeatureRun run, String name) { this.run = run; this.name = name; }
    public boolean matches(String candidate) { return name.equals(candidate); }
    public void record(boolean passed, boolean timedOut) {
        if (state == VerificationGateState.PASSED && !passed) throw new IllegalStateException("Passed verification gate cannot be downgraded");
        state = timedOut ? VerificationGateState.TIMED_OUT : passed ? VerificationGateState.PASSED : VerificationGateState.FAILED;
    }
    public void record(boolean passed) { record(passed, false); }
    public void skipByPolicy() { if (required) throw new IllegalStateException("A required gate cannot be skipped by policy"); state = VerificationGateState.SKIPPED_BY_POLICY; }
    public void manualOverride() { if (state != VerificationGateState.FAILED && state != VerificationGateState.TIMED_OUT) throw new IllegalStateException("Only a failed or timed-out gate can be overridden"); state = VerificationGateState.MANUAL_OVERRIDE; }
    public boolean satisfiesReview() { return !required || state == VerificationGateState.PASSED; }
    public VerificationPolicySpec toSpec() {
        if (kind == null) throw new IllegalStateException("Legacy verification gate has no executable policy");
        return new VerificationPolicySpec(name, kind, imageDigest, getCommand(), networkPolicy, timeoutSeconds, required, criterionCoverage);
    }
    public String getId() { return id; } public String getName() { return name; } public boolean isRequired() { return required; }
    public String getState() { return state.name(); } public String getKind() { return kind; } public String getImageDigest() { return imageDigest; }
    public List<String> getCommand() { return command == null ? List.of() : command.lines().toList(); }
    public String getNetworkPolicy() { return networkPolicy; } public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public String getCriterionCoverage() { return criterionCoverage; }
}
