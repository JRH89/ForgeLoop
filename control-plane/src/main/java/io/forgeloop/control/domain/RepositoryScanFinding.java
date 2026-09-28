package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** A bounded actionable suggestion; no raw repository file contents are retained. */
@Entity
@Table(name = "repository_scan_finding")
public class RepositoryScanFinding {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RepositoryScan scan;
    @Column(nullable = false, length = 16) private String severity;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 3000) private String description;
    @Column(nullable = false, length = 1200) private String impact;
    @Column(nullable = false, length = 1600) private String evidence;
    @Column(nullable = false, length = 3000) private String affectedFiles;
    @Column(nullable = false, length = 2400) private String acceptanceCriteria;
    @Column(nullable = false) private int severityOrder;
    private Integer issueNumber;
    @Column(length = 500) private String issueUrl;
    @OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC") private List<RepositoryIssueProposal> issueProposals = new ArrayList<>();

    protected RepositoryScanFinding() { }
    public RepositoryScanFinding(String severity, String title, String description, String impact, String evidence,
                                 String affectedFiles, String acceptanceCriteria) {
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.impact = impact;
        this.evidence = evidence;
        this.affectedFiles = affectedFiles;
        this.acceptanceCriteria = acceptanceCriteria;
        this.severityOrder = switch (severity) { case "CRITICAL" -> 0; case "HIGH" -> 1; case "MEDIUM" -> 2; default -> 3; };
    }
    RepositoryScanFinding attachTo(RepositoryScan scan) { this.scan = scan; return this; }
    public RepositoryScan getScan() { return scan; }
    public void recordGithubIssue(int number, String url) {
        if (issueUrl != null) throw new IllegalStateException("A GitHub issue was already created for this finding");
        if (number < 1 || url == null || !url.startsWith("https://github.com/")) throw new IllegalArgumentException("GitHub issue receipt is invalid");
        issueNumber = number;
        issueUrl = url;
    }
    public String getId() { return id; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImpact() { return impact; }
    public String getEvidence() { return evidence; }
    public java.util.List<String> getAffectedFiles() { return java.util.Arrays.stream(affectedFiles.split("\\n")).filter(value -> !value.isBlank()).toList(); }
    public java.util.List<String> getAcceptanceCriteria() { return java.util.Arrays.stream(acceptanceCriteria.split("\\n")).filter(value -> !value.isBlank()).toList(); }
    public Integer getIssueNumber() { return issueNumber; }
    public String getIssueUrl() { return issueUrl; }
    /** The most recent opt-in generation attempt is the actionable view; earlier attempts remain persisted. */
    public RepositoryIssueProposal getProposal() { return issueProposals.isEmpty() ? null : issueProposals.getLast(); }
}
