package io.forgeloop.control.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One schedulable delivery step, explicitly constrained to a runner capability. */
@Entity
public class DeliveryTask {
    private static final Set<String> WRITING_ROLES = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR");
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private FeatureRun run;
    private String role;
    private String planKey;
    private String title;
    private String requiredCapability;
    private String ownedPaths;
    private long budgetMicros;
    private String changeSha;
    @Enumerated(EnumType.STRING) private TaskState state;
    private int attemptBudget = 2;
    private int attempts;
    @ManyToMany
    @JoinTable(name = "delivery_task_dependency",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "dependency_id"))
    private List<DeliveryTask> dependencies = new ArrayList<>();
    @OneToMany(mappedBy = "task") private List<ProviderAttempt> providerAttempts = new ArrayList<>();
    @OneToMany(mappedBy = "task") private List<RepairPackage> repairPackages = new ArrayList<>();
    @OneToMany(mappedBy = "task") private List<TestCheckEvidence> testCheckEvidence = new ArrayList<>();
    @ManyToOne private VerificationGate verificationGate;

    protected DeliveryTask() { }
    DeliveryTask(FeatureRun run, String planKey, String role, String title, String requiredCapability,
                 List<String> ownedPaths, int attemptBudget, long budgetMicros) {
        this.run = run; this.planKey = planKey; this.role = role; this.title = title; this.requiredCapability = requiredCapability;
        this.ownedPaths = String.join("\n", ownedPaths); this.attemptBudget = attemptBudget; this.budgetMicros = budgetMicros;
        this.state = TaskState.PENDING;
    }

    DeliveryTask(FeatureRun run, String role, String title, String requiredCapability) {
        this(run, role.toLowerCase() + "-" + (run.getTasks().size() + 1), role, title, requiredCapability, List.of(), 2, 0);
    }

    public void transition(TaskState to) {
        if (state == TaskState.VERIFIED || state == TaskState.FAILED) throw new IllegalStateException("Terminal task cannot transition");
        if (to == TaskState.REPAIR_QUEUED && ++attempts > attemptBudget) { state = TaskState.FAILED; return; }
        boolean allowed = switch (state) {
            case PENDING -> to == TaskState.LEASED || to == TaskState.HELD;
            case LEASED -> to == TaskState.PREPARING || to == TaskState.RUNNING || to == TaskState.CHANGE_READY
                    || to == TaskState.INTEGRATED || to == TaskState.VERIFIED || to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case PREPARING -> to == TaskState.RUNNING || to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case RUNNING -> to == TaskState.CHANGE_READY || to == TaskState.VERIFIED || to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case CHANGE_READY -> to == TaskState.INTEGRATED || to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case INTEGRATED -> to == TaskState.VERIFIED || to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case RETRYABLE_FAILURE -> to == TaskState.REPAIR_QUEUED || to == TaskState.HELD;
            case REPAIR_QUEUED -> to == TaskState.LEASED || to == TaskState.HELD;
            case HELD -> false;
            case VERIFIED, FAILED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid task transition from " + state + " to " + to);
        state = to;
    }
    public void dependsOn(DeliveryTask dependency) { dependencies.add(dependency); }
    /** Reopens only server-owned pipeline stages after a bounded quality-gate repair is scheduled. */
    void resetPipelineStage() {
        if (!List.of("INTEGRATION", "REVIEW", "VERIFICATION", "RED_CHECK", "GREEN_CHECK").contains(role)) throw new IllegalStateException("Only pipeline stages can be reset");
        state = TaskState.PENDING;
    }
    /** Requeues a failed RED check after its test writer has been sent for a bounded repair. */
    public void requeueRedCheckAfterWriterRepair() {
        if (!"RED_CHECK".equals(role)) throw new IllegalStateException("Only a RED check can be requeued after test-writer repair");
        state = TaskState.PENDING;
    }
    public void attachVerificationGate(VerificationGate gate) { if (!List.of("VERIFICATION", "RED_CHECK", "GREEN_CHECK").contains(role)) throw new IllegalStateException("Only verification tasks can own gates"); this.verificationGate = gate; }
    public void recordChangeSha(String sha) {
        if (sha == null || !sha.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("Change SHA is invalid");
        this.changeSha = sha;
    }
    void attachRepairPackage(RepairPackage repairPackage) { repairPackages.add(repairPackage); }
    void attachTestCheckEvidence(TestCheckEvidence item) { testCheckEvidence.add(item); }
    public boolean dependenciesSatisfied() {
        return dependencies.stream().allMatch(task -> task.state == TaskState.INTEGRATED || task.state == TaskState.VERIFIED
                || ("INTEGRATION".equals(role) && task.state == TaskState.CHANGE_READY)
                || ("RED_CHECK".equals(role) && isWritingRole(task.role) && task.state == TaskState.CHANGE_READY)
                || (isWritingRole(role) && isWritingRole(task.role) && task.state == TaskState.CHANGE_READY));
    }
    public void integrateDependencies() {
        if (!"INTEGRATION".equals(role)) throw new IllegalStateException("Only an integration task can integrate dependencies");
        dependencies.stream().filter(task -> task.state == TaskState.CHANGE_READY).forEach(task -> task.transition(TaskState.INTEGRATED));
    }
    public boolean pathConflictsWith(DeliveryTask other) {
        return getOwnedPaths().stream().anyMatch(left -> other.getOwnedPaths().stream().anyMatch(right -> overlaps(left, right)));
    }
    private static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + "/") || right.startsWith(left + "/");
    }
    /** Prevents new execution claims while preserving completed task evidence for an operator cancellation. */
    public void hold() { if (state != TaskState.VERIFIED && state != TaskState.FAILED) state = TaskState.HELD; }

    /** Grants exactly one additional, audited repair attempt after an operator reviews a failure. */
    public void retryByOperator() {
        if (state != TaskState.FAILED && state != TaskState.HELD && state != TaskState.RETRYABLE_FAILURE) {
            throw new IllegalStateException("Only failed or held tasks can be retried");
        }
        attemptBudget++;
        state = TaskState.REPAIR_QUEUED;
    }

    public String getId() { return id; } public String getPlanKey() { return planKey; } public String getRole() { return role; } public String getTitle() { return title; }
    /**
     * Converts a retry into a repair worker only when the original task was allowed to write source files.
     * Control stages retain their original role so a planner or integrator can never inherit repair write access.
     */
    public String getExecutionRole() {
        return state == TaskState.REPAIR_QUEUED && List.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR").contains(role)
                ? "REPAIR"
                : role;
    }
    public FeatureRun getRun() { return run; }
    /** Runner dispatch fields are derived from the policy-bound run, not runner input. */
    public String getRepository() { return run.getRepository(); }
    public String getBaseBranch() { return run.getBaseBranch(); }
    public String getSourceRef() { return run.getSourceRef(); }
    public String getSpecification() { return run.getSpecification(); }
    public String getExecutionSpecification() {
        if ((state != TaskState.REPAIR_QUEUED && !"REPAIR".equals(role)) || repairPackages.isEmpty()) return run.getSpecification();
        RepairPackage repair = repairPackages.getLast();
        return run.getSpecification() + "\n\nBounded repair context:\nFailure category: " + repair.getFailureCategory()
                + "\nChange SHA: " + (repair.getChangeSha() == null ? "unknown" : repair.getChangeSha())
                + "\nEvidence digest: " + (repair.getEvidenceDigest() == null ? "unknown" : repair.getEvidenceDigest())
                + "\nOwned paths: " + String.join(",", repair.getOwnedPaths())
                + (repair.getFailingTests().isEmpty() ? "" : "\nFailing tests:\n- " + String.join("\n- ", repair.getFailingTests()))
                + "\nAcceptance criteria:\n- " + String.join("\n- ", repair.getAcceptanceCriteria());
    }
    public double getBudgetUsd() { return run.getBudgetUsd(); }
    public String getRequiredCapability() { return requiredCapability; } public TaskState getState() { return state; }
    public int getAttemptBudget() { return attemptBudget; } public int getAttempts() { return attempts; }
    public long getBudgetMicros() { return budgetMicros; }
    public String getChangeSha() { return changeSha; }
    public long getSpentCostMicros() { return providerAttempts.stream().filter(ProviderAttempt::isCostKnown).mapToLong(ProviderAttempt::getEstimatedCostMicros).sum(); }
    public boolean hasBudgetRemaining() { return budgetMicros == 0 || getSpentCostMicros() < budgetMicros; }
    public List<String> getOwnedPaths() { return ownedPaths == null || ownedPaths.isBlank() ? List.of() : ownedPaths.lines().toList(); }
    public List<DeliveryTask> getDependencies() { return List.copyOf(dependencies); }
    public List<String> getDependencyKeys() { return dependencies.stream().map(DeliveryTask::getPlanKey).toList(); }
    /**
     * Returns writer commits in a safe cherry-pick order while preserving declared order for peers.
     * Quality repair commits are appended after the original writer graph because they are based on
     * the prior integration head rather than on the independent writer base.
     */
    public List<String> getDependencyChangeShas() {
        List<String> changeShas = new ArrayList<>();
        java.util.Set<DeliveryTask> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        dependencies.stream().filter(task -> !"REPAIR".equals(task.role)).forEach(task -> appendWriterChangeShas(task, visited, changeShas));
        dependencies.stream().filter(task -> "REPAIR".equals(task.role)).forEach(task -> appendWriterChangeShas(task, visited, changeShas));
        return List.copyOf(changeShas);
    }
    private static void appendWriterChangeShas(DeliveryTask task, java.util.Set<DeliveryTask> visited, List<String> changeShas) {
        if (!visited.add(task)) return;
        if (isWritingRole(task.role)) {
            task.dependencies.stream().filter(dependency -> isWritingRole(dependency.role))
                    .forEach(dependency -> appendWriterChangeShas(dependency, visited, changeShas));
        }
        if (task.changeSha != null) changeShas.add(task.changeSha);
    }
    /** Returns the predecessor writer used to pin this task to a runner-local commit. */
    public java.util.Optional<DeliveryTask> getWritingDependency() {
        return dependencies.stream().filter(task -> isWritingRole(task.role)).findFirst();
    }
    public boolean isWritingTask() { return isWritingRole(role); }
    /** Exposes the snapshotted loop only for source-writing roles, never control or review tasks. */
    public TaskAgentLoop getAgentLoop() {
        if (!isWritingRole(role)) return null;
        AgentLoopBudget budget = run.getAgentLoopBudget();
        if (budget == null) return null;
        List<AgentLoopGate> gates = run.getGates().stream().map(VerificationGate::toAgentLoopGate)
                .filter(java.util.Objects::nonNull).toList();
        return new TaskAgentLoop(budget, gates);
    }
    private static boolean isWritingRole(String role) {
        return WRITING_ROLES.contains(role);
    }
    public List<String> getAcceptanceCriteria() { return run.getCriteria().stream().map(AcceptanceCriterion::getStatement).toList(); }
    /** Repair workers use the integrated head; chained writers use their committed predecessor. */
    public String getExecutionBaseRef() {
        if ("REPAIR".equals(role)) {
            return run.getTasks().stream().filter(task -> "INTEGRATION".equals(task.role)).map(DeliveryTask::getChangeSha)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(run.getBaseBranch());
        }
        if (isWritingRole(role) && !dependencies.isEmpty()) {
            DeliveryTask dependency = getWritingDependency().filter(ignored ->
                            dependencies.stream().filter(DeliveryTask::isWritingTask).count() == 1)
                    .orElseThrow(() -> new IllegalStateException("A chained writing task must have exactly one writing dependency"));
            if (dependency.changeSha == null) throw new IllegalStateException("Writing dependency has no change commit");
            return dependency.changeSha;
        }
        return run.getBaseBranch();
    }
    public List<ProviderAttempt> getProviderAttempts() { return List.copyOf(providerAttempts); }
    public List<RepairPackage> getRepairPackages() { return List.copyOf(repairPackages); }
    public VerificationGate getVerificationGate() { return verificationGate; }
    public String getVerificationGateName() { return verificationGate == null ? null : verificationGate.getName(); }
    public String getVerificationKind() { return verificationGate == null ? null : verificationGate.getKind(); }
    public String getVerificationImageDigest() { return verificationGate == null ? null : verificationGate.getImageDigest(); }
    public List<String> getVerificationCommand() { return verificationGate == null ? List.of() : verificationGate.getCommand(); }
    public String getVerificationNetworkPolicy() { return verificationGate == null ? null : verificationGate.getNetworkPolicy(); }
    public Integer getVerificationTimeoutSeconds() { return verificationGate == null ? null : verificationGate.getTimeoutSeconds(); }
    public String getTestReportFormat() { return verificationGate == null ? null : verificationGate.getTestReport(); }
    /** Uses the persisted task role so retries cannot gain a different patch boundary. */
    public String getWriteBoundary() {
        if (!run.isTestFirst()) return "ANY";
        return "INDEPENDENT_TEST".equals(role) ? "TESTS_ONLY"
                : isWritingRole(role) ? "NO_TESTS" : "ANY";
    }
    public List<String> getTestPathGlobs() { return run.getTestPathGlobs(); }
    public String getVerificationBaseRef() {
        if ("RED_CHECK".equals(role)) return getWritingDependency().map(DeliveryTask::getChangeSha)
                .filter(java.util.Objects::nonNull).orElseThrow(() -> new IllegalStateException("RED check has no test commit"));
        return dependencies.stream().filter(task -> "INTEGRATION".equals(task.role)).map(DeliveryTask::getChangeSha)
                .filter(java.util.Objects::nonNull).findFirst().orElse(run.getBaseBranch());
    }
    /** Check tasks may outlive the ordinary ten-minute provider-task lease while running repository gates. */
    public Duration leaseDuration() {
        if ("RED_CHECK".equals(role)) return Duration.ofMinutes(10).plusSeconds(2L * requiredGateTimeout());
        if ("GREEN_CHECK".equals(role)) return Duration.ofMinutes(10).plusSeconds(requiredGateTimeout());
        return Duration.ofMinutes(10);
    }
    private long requiredGateTimeout() {
        if (verificationGate == null || verificationGate.getTimeoutSeconds() == null) throw new IllegalStateException("Test check has no gate timeout");
        return verificationGate.getTimeoutSeconds();
    }
    public List<String> getExpectedTests() {
        return "GREEN_CHECK".equals(role) ? run.currentRedTests().stream().limit(2_000).toList() : List.of();
    }
    public boolean isExpectedTestsOverflow() { return "GREEN_CHECK".equals(role) && run.currentRedTests().size() > 2_000; }
    public String getTestFirstEvidence() { return "REVIEW".equals(role) ? run.getTestFirstReviewEvidence() : null; }
    public List<TestCheckEvidence> getTestCheckEvidence() { return List.copyOf(testCheckEvidence); }
}
