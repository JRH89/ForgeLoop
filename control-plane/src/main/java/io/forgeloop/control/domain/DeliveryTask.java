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
import java.util.ArrayList;
import java.util.List;

/** One schedulable delivery step, explicitly constrained to a runner capability. */
@Entity
public class DeliveryTask {
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
    public void attachVerificationGate(VerificationGate gate) { if (!"VERIFICATION".equals(role)) throw new IllegalStateException("Only verification tasks can own gates"); this.verificationGate = gate; }
    public void recordChangeSha(String sha) {
        if (sha == null || !sha.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("Change SHA is invalid");
        this.changeSha = sha;
    }
    void attachRepairPackage(RepairPackage repairPackage) { repairPackages.add(repairPackage); }
    public boolean dependenciesSatisfied() {
        return dependencies.stream().allMatch(task -> task.state == TaskState.INTEGRATED || task.state == TaskState.VERIFIED
                || ("INTEGRATION".equals(role) && task.state == TaskState.CHANGE_READY));
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
    public String getExecutionRole() { return state == TaskState.REPAIR_QUEUED && !"VERIFICATION".equals(role) ? "REPAIR" : role; }
    public FeatureRun getRun() { return run; }
    /** Runner dispatch fields are derived from the policy-bound run, not runner input. */
    public String getRepository() { return run.getRepository(); }
    public String getBaseBranch() { return run.getBaseBranch(); }
    public String getSourceRef() { return run.getSourceRef(); }
    public String getSpecification() { return run.getSpecification(); }
    public String getExecutionSpecification() {
        if (state != TaskState.REPAIR_QUEUED || repairPackages.isEmpty()) return run.getSpecification();
        RepairPackage repair = repairPackages.getLast();
        return run.getSpecification() + "\n\nBounded repair context:\nFailure category: " + repair.getFailureCategory()
                + "\nChange SHA: " + (repair.getChangeSha() == null ? "unknown" : repair.getChangeSha())
                + "\nEvidence digest: " + (repair.getEvidenceDigest() == null ? "unknown" : repair.getEvidenceDigest())
                + "\nOwned paths: " + String.join(",", repair.getOwnedPaths())
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
    public List<String> getDependencyChangeShas() { return dependencies.stream().map(DeliveryTask::getChangeSha).filter(java.util.Objects::nonNull).toList(); }
    public List<ProviderAttempt> getProviderAttempts() { return List.copyOf(providerAttempts); }
    public List<RepairPackage> getRepairPackages() { return List.copyOf(repairPackages); }
    public VerificationGate getVerificationGate() { return verificationGate; }
    public String getVerificationGateName() { return verificationGate == null ? null : verificationGate.getName(); }
    public String getVerificationKind() { return verificationGate == null ? null : verificationGate.getKind(); }
    public String getVerificationImageDigest() { return verificationGate == null ? null : verificationGate.getImageDigest(); }
    public List<String> getVerificationCommand() { return verificationGate == null ? List.of() : verificationGate.getCommand(); }
    public String getVerificationNetworkPolicy() { return verificationGate == null ? null : verificationGate.getNetworkPolicy(); }
    public Integer getVerificationTimeoutSeconds() { return verificationGate == null ? null : verificationGate.getTimeoutSeconds(); }
    public String getVerificationBaseRef() { return dependencies.stream().filter(task -> "INTEGRATION".equals(task.role)).map(DeliveryTask::getChangeSha).filter(java.util.Objects::nonNull).findFirst().orElse(run.getBaseBranch()); }
}
