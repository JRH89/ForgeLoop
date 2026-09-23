package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only, metadata-only progress emitted by an authenticated runner lease. */
@Entity
public class RunnerEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable=false) private String organizationId;
    @Column(nullable=false) private String runId;
    @Column(nullable=false) private String taskId;
    @Column(nullable=false) private String runnerId;
    @Column(nullable=false) private String leaseId;
    @Column(nullable=false) private long sequenceNumber;
    @Column(nullable=false,length=16) private String level;
    @Column(nullable=false,length=64) private String eventType;
    @Column(nullable=false,length=2000) private String message;
    @Column(nullable=false) private Instant occurredAt;
    @Column(nullable=false) private Instant receivedAt;
    protected RunnerEvent() { }
    public RunnerEvent(String organizationId,String runId,String taskId,String runnerId,String leaseId,long sequenceNumber,String level,String eventType,String message,Instant occurredAt){
        this.organizationId=organizationId;this.runId=runId;this.taskId=taskId;this.runnerId=runnerId;this.leaseId=leaseId;this.sequenceNumber=sequenceNumber;this.level=level;this.eventType=eventType;this.message=message;this.occurredAt=occurredAt;this.receivedAt=Instant.now();
    }
    public boolean matches(String candidateRunner,String candidateLevel,String candidateType,String candidateMessage,Instant candidateTime){return runnerId.equals(candidateRunner)&&level.equals(candidateLevel)&&eventType.equals(candidateType)&&message.equals(candidateMessage)&&occurredAt.equals(candidateTime);}
    public String getId(){return id;} public String getRunId(){return runId;} public String getTaskId(){return taskId;} public String getRunnerId(){return runnerId;} public String getLeaseId(){return leaseId;} public long getSequenceNumber(){return sequenceNumber;} public String getLevel(){return level;} public String getEventType(){return eventType;} public String getMessage(){return message;} public String getOccurredAt(){return occurredAt.toString();} public String getReceivedAt(){return receivedAt.toString();}
}
