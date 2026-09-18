package io.forgeloop.control.domain;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames="deliveryId")) public class GithubDelivery { @Id @GeneratedValue(strategy=GenerationType.UUID) private String id; @Column(nullable=false) private String deliveryId; @Column(nullable=false) private String eventType; @Column(nullable=false) private Instant receivedAt; protected GithubDelivery(){} public GithubDelivery(String deliveryId,String eventType){this.deliveryId=deliveryId;this.eventType=eventType;this.receivedAt=Instant.now();} }
