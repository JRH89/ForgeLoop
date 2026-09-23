package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Immutable criterion-level evidence produced by an independent review. */
@Entity
public class ReviewCriterionAssessment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private ReviewEvidence review;
    @Column(nullable = false, length = 4000) private String statement;
    @Column(nullable = false, length = 10) private String status;
    @Column(nullable = false, length = 4000) private String evidence;

    protected ReviewCriterionAssessment() { }
    ReviewCriterionAssessment(ReviewEvidence review, String statement, String status, String evidence) {
        this.review = review; this.statement = statement; this.status = status; this.evidence = evidence;
    }
    public String getId() { return id; }
    public String getStatement() { return statement; }
    public String getStatus() { return status; }
    public String getEvidence() { return evidence; }
}
