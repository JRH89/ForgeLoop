package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** A policy-bound delivery run, isolated to the organization that owns its repository connection. */
@Entity
public class FeatureRun {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
  @Column(nullable=false) private String organizationId;
  private String repository; private String sourceRef; private String title;
  @Column(length=20000) private String specification;
  private double budgetUsd; private String harnessProfile; private String baseBranch; private int policyRevision;
  @Enumerated(EnumType.STRING) private RunState state; private Instant createdAt;
  private Instant approvedAt; private String approvedBy;
  @OneToMany(mappedBy="run",cascade=CascadeType.ALL,orphanRemoval=true) private List<DeliveryTask> tasks=new ArrayList<>();
  @OneToMany(mappedBy="run",cascade=CascadeType.ALL,orphanRemoval=true) private List<VerificationGate> gates=new ArrayList<>();
  @OneToMany(mappedBy="run",cascade=CascadeType.ALL,orphanRemoval=true) private List<AcceptanceCriterion> criteria=new ArrayList<>();
  protected FeatureRun() { }
  public FeatureRun(String organizationId,String repository,String sourceRef,String title,String specification,double budgetUsd,String harnessProfile,String baseBranch,int policyRevision) { this.organizationId=organizationId; this.repository=repository; this.sourceRef=sourceRef; this.title=title; this.specification=specification; this.budgetUsd=budgetUsd; this.harnessProfile=harnessProfile; this.baseBranch=baseBranch; this.policyRevision=policyRevision; this.state=RunState.RECEIVED; this.createdAt=Instant.now(); }
  public FeatureRun(String organizationId,String repository,String sourceRef,String title,String specification,double budgetUsd,String harnessProfile,int policyRevision) { this(organizationId, repository, sourceRef, title, specification, budgetUsd, harnessProfile, "main", policyRevision); }
  /** Compatibility constructor for local fixtures; production creation always supplies a repository owner. */
  public FeatureRun(String repository,String sourceRef,String title,String specification,double budgetUsd,String harnessProfile,int policyRevision) { this("local-development", repository, sourceRef, title, specification, budgetUsd, harnessProfile, policyRevision); }
  public boolean belongsTo(String candidateOrganizationId) { return organizationId.equals(candidateOrganizationId); }
  public void addTask(String role,String title,String requiredCapability){tasks.add(new DeliveryTask(this,role,title,requiredCapability));}
  public DeliveryTask addPlannedTask(String planKey,String role,String title,String requiredCapability,List<String> ownedPaths,int attemptBudget,long budgetMicros){DeliveryTask task=new DeliveryTask(this,planKey,role,title,requiredCapability,ownedPaths,attemptBudget,budgetMicros);tasks.add(task);return task;}
  public void beginPlanning(){if(state!=RunState.RECEIVED)throw new IllegalStateException("Run is not ready for planning");state=RunState.PLANNING;}
  public void queuePlannedWork(){if(state!=RunState.PLANNING)throw new IllegalStateException("Run is not planning");state=RunState.QUEUED;}
  public void startExecution(){if(state!=RunState.QUEUED&&state!=RunState.EXECUTING)throw new IllegalStateException("Run is not executable");state=RunState.EXECUTING;}
  public void block(){if(state==RunState.COMPLETE||state==RunState.CANCELLED)throw new IllegalStateException("Terminal run cannot be blocked");state=RunState.BLOCKED;}
  public void addGate(String name){gates.add(new VerificationGate(this,name));}
  public void addGate(VerificationPolicySpec policy){gates.add(new VerificationGate(this,policy));}
  public void addCriterion(String statement){criteria.add(new AcceptanceCriterion(this,statement));}
  public void addPolicyVerificationTasks(){List<DeliveryTask> prerequisites=tasks.stream().filter(task->!"PLANNER".equals(task.getRole())&&!"VERIFICATION".equals(task.getRole())).toList();DeliveryTask previous=null;for(VerificationGate gate:gates){if(gate.getKind()==null)continue;if(!gate.isRequired()){gate.skipByPolicy();continue;}DeliveryTask verification=new DeliveryTask(this,"verify-"+gate.getName(),"VERIFICATION","Verify "+gate.getName(),"docker",List.of(),2,0);verification.attachVerificationGate(gate);prerequisites.forEach(verification::dependsOn);if(previous!=null)verification.dependsOn(previous);tasks.add(verification);previous=verification;}}
  /** Adds a server-owned read-only review after integration; the planner cannot omit or weaken it. */
  public void addIndependentReviewTask(){DeliveryTask review=new DeliveryTask(this,"independent-review","REVIEW","Review integrated change against the specification","provider",List.of(),2,0);List<DeliveryTask> integrated=tasks.stream().filter(task->"INTEGRATION".equals(task.getRole())).toList();(integrated.isEmpty()?tasks.stream().filter(task->!"PLANNER".equals(task.getRole())).toList():integrated).forEach(review::dependsOn);tasks.add(review);}
  /** Creates a bounded code-repair task and reopens integration, review, and sequential verification. */
  public RepairPackage scheduleQualityRepair(DeliveryTask failedTask,String failureCategory,String evidenceDigest){if(!List.of("REVIEW","VERIFICATION").contains(failedTask.getRole()))throw new IllegalArgumentException("Only review or verification can schedule a quality repair");failedTask.transition(TaskState.REPAIR_QUEUED);if(failedTask.getState()==TaskState.FAILED){block();return null;}List<String> paths=tasks.stream().filter(task->List.of("IMPLEMENTATION","BACKEND","FRONTEND","INDEPENDENT_TEST","REPAIR").contains(task.getRole())).flatMap(task->task.getOwnedPaths().stream()).distinct().toList();if(paths.isEmpty()){block();return null;}DeliveryTask repair=new DeliveryTask(this,"quality-repair-"+failedTask.getPlanKey()+"-"+failedTask.getAttempts(),"REPAIR","Repair "+failedTask.getTitle(),"provider",paths,2,0);tasks.add(repair);RepairPackage repairPackage=new RepairPackage(repair,failureCategory,evidenceDigest);tasks.stream().filter(task->"INTEGRATION".equals(task.getRole())).findFirst().orElseThrow(()->new IllegalStateException("Repair requires an integration stage")).dependsOn(repair);tasks.stream().filter(task->List.of("INTEGRATION","REVIEW","VERIFICATION").contains(task.getRole())).forEach(DeliveryTask::resetPipelineStage);gates.forEach(VerificationGate::resetForRepair);state=RunState.EXECUTING;approvedAt=null;approvedBy=null;return repairPackage;}
  public void overrideGate(String name){VerificationGate gate=gates.stream().filter(item->item.matches(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Verification gate is not part of this run"));gate.manualOverride();state=RunState.BLOCKED;}
  public void recordGate(String name,boolean passed){recordGate(name,passed,false);}
  public void recordGate(String name,boolean passed,boolean timedOut){VerificationGate gate=gates.stream().filter(item->item.matches(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Verification gate is not required by this run"));gate.record(passed,timedOut);if(!passed){state=RunState.BLOCKED;return;}if(gates.stream().allMatch(VerificationGate::satisfiesReview)&&gates.stream().filter(VerificationGate::isRequired).allMatch(item->"ALL".equals(item.getCriterionCoverage())))criteria.forEach(AcceptanceCriterion::cover);evaluateReviewReadiness();}
  public void evaluateReviewReadiness(){List<DeliveryTask> verificationTasks=tasks.stream().filter(task->"VERIFICATION".equals(task.getRole())).toList();boolean workComplete=verificationTasks.isEmpty()||verificationTasks.stream().allMatch(task->task.getState()==TaskState.VERIFIED);if(workComplete&&gates.stream().allMatch(VerificationGate::satisfiesReview)&&criteria.stream().allMatch(AcceptanceCriterion::isCovered))state=RunState.READY_FOR_REVIEW;}
  public void cancel(){if(state==RunState.COMPLETE||state==RunState.CANCELLED)throw new IllegalStateException("Run is already terminal");tasks.forEach(DeliveryTask::hold);state=RunState.CANCELLED;}
  /** Records the human release decision separately from automated verification. */
  public void approve(String actor){if(state!=RunState.READY_FOR_REVIEW)throw new IllegalStateException("Only a verified run can be approved");if(approvedAt!=null)return;if(actor==null||actor.isBlank())throw new IllegalArgumentException("Approver is required");approvedAt=Instant.now();approvedBy=actor;}
  /** Completes delivery only after GitHub confirms the expected commit was merged. */
  public void completeDelivery(){if(state!=RunState.READY_FOR_REVIEW&&state!=RunState.PR_OPEN)throw new IllegalStateException("Run is not awaiting delivery");if(!isApproved())throw new IllegalStateException("Run is not approved");state=RunState.COMPLETE;}
  public void resumeAfterRetry(DeliveryTask task){if(state!=RunState.BLOCKED&&state!=RunState.FAILED)throw new IllegalStateException("Run is not blocked");if(task.getRun()!=this)throw new IllegalArgumentException("Retry task does not belong to run");state="PLANNER".equals(task.getRole())?RunState.PLANNING:RunState.EXECUTING;approvedAt=null;approvedBy=null;}
  public long getSpentCostMicros(){return tasks.stream().mapToLong(DeliveryTask::getSpentCostMicros).sum();}
  public boolean hasBudgetRemaining(){return getSpentCostMicros()<Math.round(budgetUsd*1_000_000d);}
  public String getId(){return id;} public String getOrganizationId(){return organizationId;} public String getRepository(){return repository;} public String getSourceRef(){return sourceRef;} public String getTitle(){return title;} public String getSpecification(){return specification;} public double getBudgetUsd(){return budgetUsd;} public String getHarnessProfile(){return harnessProfile;} public String getBaseBranch(){return baseBranch;} public int getPolicyRevision(){return policyRevision;} public RunState getState(){return state;} public String getCreatedAt(){return createdAt.toString();} public List<DeliveryTask> getTasks(){return List.copyOf(tasks);} public List<VerificationGate> getGates(){return List.copyOf(gates);} public List<AcceptanceCriterion> getCriteria(){return List.copyOf(criteria);} public boolean isApproved(){return approvedAt!=null;} public String getApprovedAt(){return approvedAt==null?null:approvedAt.toString();} public String getApprovedBy(){return approvedBy;}
}
