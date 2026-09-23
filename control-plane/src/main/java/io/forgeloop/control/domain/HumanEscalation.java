package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Durable operator work item created when autonomous execution cannot safely continue. */
@Entity
public class HumanEscalation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
    @Column(nullable=false) private String organizationId;
    @Column(nullable=false) private String runId;
    private String taskId;
    @Column(nullable=false,length=64) private String reason;
    @Column(nullable=false,length=16) private String severity;
    @Column(nullable=false,length=24) private String status;
    @Column(nullable=false,length=1000) private String summary;
    @Column(nullable=false) private Instant createdAt;
    private Instant acknowledgedAt; private String acknowledgedBy; private Instant resolvedAt; private String resolvedBy;
    protected HumanEscalation() { }
    public HumanEscalation(FeatureRun run,DeliveryTask task,String reason,String severity,String summary){this.organizationId=run.getOrganizationId();this.runId=run.getId();this.taskId=task==null?null:task.getId();this.reason=reason;this.severity=severity;this.status="OPEN";this.summary=summary;this.createdAt=Instant.now();}
    public void acknowledge(String actor){if("RESOLVED".equals(status))throw new IllegalStateException("Resolved escalation cannot be acknowledged");if("OPEN".equals(status)){status="ACKNOWLEDGED";acknowledgedAt=Instant.now();acknowledgedBy=actor;}}
    public void resolve(String actor){if(!"RESOLVED".equals(status)){status="RESOLVED";resolvedAt=Instant.now();resolvedBy=actor;}}
    public void raiseAgain(String newSummary){if("RESOLVED".equals(status)){status="OPEN";summary=newSummary;createdAt=Instant.now();acknowledgedAt=null;acknowledgedBy=null;resolvedAt=null;resolvedBy=null;}}
    public boolean belongsTo(String organization){return organizationId.equals(organization);}
    public String getId(){return id;} public String getRunId(){return runId;} public String getTaskId(){return taskId;} public String getReason(){return reason;} public String getSeverity(){return severity;} public String getStatus(){return status;} public String getSummary(){return summary;} public String getCreatedAt(){return createdAt.toString();} public String getAcknowledgedAt(){return acknowledgedAt==null?null:acknowledgedAt.toString();} public String getAcknowledgedBy(){return acknowledgedBy;} public String getResolvedAt(){return resolvedAt==null?null:resolvedAt.toString();} public String getResolvedBy(){return resolvedBy;}
}
