package io.forgeloop.control.domain;
import jakarta.persistence.*;import java.util.*;
@Entity public class OrganizationPolicy{
 @Id private String organizationId;private double maxRunBudgetUsd;private int maxParallelTasks;@Column(length=1000)private String allowedProviders;private boolean requireHumanApproval;private int revision;
 protected OrganizationPolicy(){} public OrganizationPolicy(String org,double budget,int parallel,List<String> providers,boolean approval){update(budget,parallel,providers,approval);organizationId=org;revision=1;}
 public void update(double budget,int parallel,List<String> providers,boolean approval){if(budget<=0||parallel<1||parallel>16||providers==null||providers.isEmpty()||providers.stream().anyMatch(p->!Set.of("anthropic","openai","gemini","local").contains(p)))throw new IllegalArgumentException("Organization policy is invalid");maxRunBudgetUsd=budget;maxParallelTasks=parallel;allowedProviders=String.join(",",new LinkedHashSet<>(providers));requireHumanApproval=approval;if(revision>0)revision++;}
 public boolean permitsBudget(double value){return value>0&&value<=maxRunBudgetUsd;}public String getOrganizationId(){return organizationId;}public double getMaxRunBudgetUsd(){return maxRunBudgetUsd;}public int getMaxParallelTasks(){return maxParallelTasks;}public List<String> getAllowedProviders(){return Arrays.stream(allowedProviders.split(",")).toList();}public boolean isRequireHumanApproval(){return requireHumanApproval;}public int getRevision(){return revision;}
}
