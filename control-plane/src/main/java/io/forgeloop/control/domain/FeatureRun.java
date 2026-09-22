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
  public void addCriterion(String statement){criteria.add(new AcceptanceCriterion(this,statement));}
  public void recordGate(String name,boolean passed){VerificationGate gate=gates.stream().filter(item->item.matches(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Verification gate is not required by this run"));gate.record(passed);if(!passed){state=RunState.BLOCKED;return;}if(gates.stream().filter(VerificationGate::isRequired).allMatch(item->"PASSED".equals(item.getState())))state=RunState.READY_FOR_REVIEW;}
  public void cancel(){if(state==RunState.COMPLETE||state==RunState.CANCELLED)throw new IllegalStateException("Run is already terminal");tasks.forEach(DeliveryTask::hold);state=RunState.CANCELLED;}
  public long getSpentCostMicros(){return tasks.stream().mapToLong(DeliveryTask::getSpentCostMicros).sum();}
  public boolean hasBudgetRemaining(){return getSpentCostMicros()<Math.round(budgetUsd*1_000_000d);}
  public String getId(){return id;} public String getOrganizationId(){return organizationId;} public String getRepository(){return repository;} public String getSourceRef(){return sourceRef;} public String getTitle(){return title;} public String getSpecification(){return specification;} public double getBudgetUsd(){return budgetUsd;} public String getHarnessProfile(){return harnessProfile;} public String getBaseBranch(){return baseBranch;} public int getPolicyRevision(){return policyRevision;} public RunState getState(){return state;} public String getCreatedAt(){return createdAt.toString();} public List<DeliveryTask> getTasks(){return List.copyOf(tasks);} public List<VerificationGate> getGates(){return List.copyOf(gates);} public List<AcceptanceCriterion> getCriteria(){return List.copyOf(criteria);}
}
