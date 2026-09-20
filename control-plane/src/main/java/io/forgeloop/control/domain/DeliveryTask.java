package io.forgeloop.control.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** One schedulable delivery step, explicitly constrained to a runner capability. */
@Entity
public class DeliveryTask {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private FeatureRun run;
    private String role;
    private String title;
    private String requiredCapability;
    @Enumerated(EnumType.STRING) private TaskState state;
    private int attemptBudget = 2;
    private int attempts;

    protected DeliveryTask() { }
    DeliveryTask(FeatureRun run, String role, String title, String requiredCapability) {
        this.run = run; this.role = role; this.title = title; this.requiredCapability = requiredCapability;
        this.state = TaskState.PENDING;
    }

    public void transition(TaskState to) {
        if (state == TaskState.VERIFIED || state == TaskState.FAILED) throw new IllegalStateException("Terminal task cannot transition");
        if (to == TaskState.REPAIR_QUEUED && ++attempts > attemptBudget) { state = TaskState.FAILED; return; }
        state = to;
    }
    /** Prevents new execution claims while preserving completed task evidence for an operator cancellation. */
    public void hold() { if (state != TaskState.VERIFIED && state != TaskState.FAILED) state = TaskState.HELD; }

    public String getId() { return id; } public String getRole() { return role; } public String getTitle() { return title; }
    public FeatureRun getRun() { return run; }
    /** Runner dispatch fields are derived from the policy-bound run, not runner input. */
    public String getRepository() { return run.getRepository(); }
    public String getBaseBranch() { return run.getBaseBranch(); }
    public String getSourceRef() { return run.getSourceRef(); }
    public String getSpecification() { return run.getSpecification(); }
    public String getRequiredCapability() { return requiredCapability; } public TaskState getState() { return state; }
    public int getAttemptBudget() { return attemptBudget; } public int getAttempts() { return attempts; }
}
