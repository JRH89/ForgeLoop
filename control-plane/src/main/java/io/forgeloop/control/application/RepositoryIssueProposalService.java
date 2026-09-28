package io.forgeloop.control.application;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.RepositoryIssueProposal;
import io.forgeloop.control.domain.RepositoryIssueProposalRepository;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import io.forgeloop.control.domain.RepositoryScanRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.integrations.github.GithubIssueReceipt;
import io.forgeloop.control.security.OperatorContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in second-pass issue drafting, isolated on an enrolled runner and gated by human publication approval. */
@Service
public class RepositoryIssueProposalService {
    private static final List<String> ACTIVE = List.of("PENDING", "RUNNING", "READY");
    private static final Set<String> PROVIDERS = Set.of("anthropic", "openai", "gemini", "local");
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_./@+ -]{1,300}");
    private final RepositoryScanRepository scans;
    private final RepositoryIssueProposalRepository proposals;
    private final RepositoryConnectionRepository connections;
    private final OperatorContext operators;
    private final GithubApi github;
    private final AuditLedgerService audit;
    private final ProviderActivityService activities;

    public RepositoryIssueProposalService(RepositoryScanRepository scans, RepositoryIssueProposalRepository proposals,
                                          RepositoryConnectionRepository connections, OperatorContext operators,
                                          GithubApi github, AuditLedgerService audit, ProviderActivityService activities) {
        this.scans = scans;
        this.proposals = proposals;
        this.connections = connections;
        this.operators = operators;
        this.github = github;
        this.audit = audit;
        this.activities = activities;
    }

    /** Queues exactly one additional model call for a finding only after explicit administrator intent. */
    @Transactional
    public RepositoryIssueProposal request(String scanId, String findingId) {
        operators.requireAdministrator();
        RepositoryScan scan = scans.lockByIdAndOrganizationId(scanId, operators.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Repository scan not found"));
        if (!"COMPLETE".equals(scan.getStatus())) throw new IllegalStateException("Only completed scans can generate issue proposals");
        RepositoryScanFinding finding = scan.getFindings().stream().filter(item -> item.getId().equals(findingId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Scan finding not found"));
        if (finding.getIssueUrl() != null) throw new IllegalStateException("A GitHub issue already exists for this finding");
        if (proposals.existsByFinding_IdAndStatusIn(findingId, ACTIVE))
            throw new IllegalStateException("This finding already has an active issue proposal");
        RepositoryIssueProposal proposal = proposals.save(new RepositoryIssueProposal(scan.getOrganizationId(), finding, operators.subject()));
        audit.record("ISSUE_PROPOSAL_REQUESTED", "REPOSITORY_SCAN_FINDING", findingId, proposal.getId());
        return proposal;
    }

    /** Claims only proposal drafts requested within the runner's organization. */
    @Transactional
    public RepositoryIssueProposalGrant claim(Runner runner) {
        if (!runner.isEnabled()) throw new IllegalStateException("Runner is disabled");
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(30));
        List<RepositoryIssueProposal> claimable = proposals.lockClaimableForOrganization(
                runner.getOrganizationId(), staleBefore, PageRequest.of(0, 1));
        if (claimable.isEmpty()) return null;
        RepositoryIssueProposal proposal = claimable.getFirst();
        if (proposal.requeueExpiredClaim(staleBefore))
            audit.record("ISSUE_PROPOSAL_REQUEUED", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), "expired-runner-claim");
        proposal.claim(runner.getId());
        audit.record("ISSUE_PROPOSAL_CLAIMED", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), runner.getId());
        return RepositoryIssueProposalGrant.from(proposal);
    }

    /** Saves the bounded draft and its usage metadata; no GitHub operation happens here. */
    @Transactional
    public RepositoryIssueProposal complete(String proposalId, Runner runner, RepositoryIssueProposalResultInput input) {
        RepositoryIssueProposal proposal = proposals.lockByIdAndOrganizationId(proposalId, runner.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue proposal not found"));
        if (input == null) throw new IllegalArgumentException("Issue proposal result is required");
        if (!input.passed()) {
            validateOptionalUsage(input);
            proposal.fail(runner.getId(), "The runner could not generate this issue proposal.", input.provider(), input.model(),
                    input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
            recordUsage(proposal, input);
            audit.record("ISSUE_PROPOSAL_FAILED", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), input.provider() == null ? "provider-no-result" : input.model());
            return proposal;
        }
        List<String> criteria = validateCriteria(input.acceptanceCriteria());
        proposal.complete(runner.getId(), bounded(input.title(), 200, "title"), bounded(input.body(), 12000, "body"),
                criteria,
                input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
        recordUsage(proposal, input);
        audit.record("ISSUE_PROPOSAL_READY", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), input.model());
        return proposal;
    }

    /** Only a ready, organization-scoped draft can create a GitHub issue, after administrator review. */
    @Transactional
    public RepositoryIssueProposal approve(String proposalId, String title, String body, List<String> criteria) {
        operators.requireAdministrator();
        RepositoryIssueProposal proposal = proposals.lockByIdAndOrganizationId(proposalId, operators.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue proposal not found"));
        if (!"READY".equals(proposal.getStatus())) throw new IllegalStateException("Only a ready issue proposal can be published");
        String approvedTitle = bounded(title, 200, "title");
        String approvedBody = bounded(body, 12000, "body");
        List<String> approvedCriteria = validateCriteria(criteria);
        RepositoryScanFinding finding = proposal.getFinding();
        if (finding.getIssueUrl() != null) throw new IllegalStateException("A GitHub issue already exists for this finding");
        RepositoryConnection connection = requireEnabled(proposal.getRepository(), operators.organizationId());
        String issueBody = issueBody(finding.getScan(), finding, approvedBody, approvedCriteria);
        GithubIssueReceipt receipt = github.createIssue(connection.getInstallationId(), proposal.getRepository(), approvedTitle, issueBody);
        proposal.approve(receipt.number(), receipt.url());
        audit.record("ISSUE_PROPOSAL_APPROVED", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), receipt.number() + "|" + receipt.url());
        return proposal;
    }

    @Transactional
    public RepositoryIssueProposal reject(String proposalId, String reason) {
        operators.requireAdministrator();
        RepositoryIssueProposal proposal = proposals.lockByIdAndOrganizationId(proposalId, operators.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue proposal not found"));
        String safeReason = reason == null || reason.isBlank() ? "No reason supplied" : bounded(reason, 500, "rejection reason");
        proposal.reject(safeReason);
        audit.record("ISSUE_PROPOSAL_REJECTED", "REPOSITORY_ISSUE_PROPOSAL", proposal.getId(), safeReason);
        return proposal;
    }

    private void recordUsage(RepositoryIssueProposal proposal, RepositoryIssueProposalResultInput input) {
        if (input.provider() == null) return;
        activities.record(proposal.getOrganizationId(), "ISSUE_SPECIFICATION", proposal.getRepository(), proposal.getId(),
                input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
    }

    private RepositoryConnection requireEnabled(String repository, String organizationId) {
        return connections.findByRepository(repository).filter(RepositoryConnection::isEnabled)
                .filter(connection -> connection.belongsTo(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Repository is not connected"));
    }

    private static List<String> validateCriteria(List<String> criteria) {
        if (criteria == null || criteria.isEmpty() || criteria.size() > 10)
            throw new IllegalArgumentException("Issue proposal must include 1 to 10 acceptance criteria");
        return criteria.stream().map(item -> bounded(item, 400, "acceptance criterion")).toList();
    }

    private static void validateOptionalUsage(RepositoryIssueProposalResultInput input) {
        if (input.provider() == null) {
            if (input.model() != null || input.inputTokens() != 0 || input.outputTokens() != 0
                    || input.estimatedCostMicros() != 0 || input.costKnown())
                throw new IllegalArgumentException("Issue proposal usage metadata is incomplete");
            return;
        }
        if (!PROVIDERS.contains(input.provider())) throw new IllegalArgumentException("Issue proposal provider is invalid");
    }

    private static String bounded(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength)
            throw new IllegalArgumentException("Issue proposal " + label + " is invalid");
        return value.trim();
    }

    private static String issueBody(RepositoryScan scan, RepositoryScanFinding finding, String body, List<String> criteria) {
        if (finding.getAffectedFiles().stream().anyMatch(path -> !SAFE_PATH.matcher(path).matches() || path.startsWith("/")
                || path.matches(".*(^|/)\\.\\.(/|$).*"))) throw new IllegalArgumentException("Finding file paths are invalid");
        return body + "\n\n### Evidence from repository scan\n" + finding.getEvidence()
                + "\n\n**Severity:** " + finding.getSeverity() + "  \n**Repository snapshot:** `" + scan.getCommitSha() + "`\n\n### Affected files\n"
                + finding.getAffectedFiles().stream().map(path -> "- `" + path + "`").reduce((a, b) -> a + "\n" + b).orElse("")
                + "\n\n### Acceptance criteria\n" + criteria.stream().map(item -> "- [ ] " + item).reduce((a, b) -> a + "\n" + b).orElse("")
                + "\n\n---\nCreated from an administrator-reviewed ForgeLoop issue proposal. The issue is not labeled or assigned automatically.";
    }
}
