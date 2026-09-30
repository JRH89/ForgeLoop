package io.forgeloop.control.application;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import io.forgeloop.control.domain.RepositoryScanRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.security.OperatorContext;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization-bound manual scan queue, result validation, and human-approved issue publishing. */
@Service
public class RepositoryScanService {
    private static final List<String> ACTIVE = List.of("PENDING", "RUNNING");
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> PROVIDERS = Set.of("anthropic", "openai", "gemini", "local");
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_./@+ -]{1,300}");
    private final RepositoryScanRepository scans;
    private final RepositoryConnectionRepository connections;
    private final OperatorContext operators;
    private final GithubApi github;
    private final AuditLedgerService audit;
    private final ProviderActivityService providerActivities;

    public RepositoryScanService(RepositoryScanRepository scans, RepositoryConnectionRepository connections,
                                 OperatorContext operators, GithubApi github, AuditLedgerService audit,
                                 ProviderActivityService providerActivities) {
        this.scans = scans; this.connections = connections; this.operators = operators; this.github = github; this.audit = audit;
        this.providerActivities = providerActivities;
    }

    @Transactional
    public RepositoryScan request(String repository) {
        operators.requireAdministrator();
        RepositoryConnection connection = requireEnabled(repository);
        if (scans.existsByOrganizationIdAndRepositoryAndStatusIn(operators.organizationId(), repository, ACTIVE))
            throw new IllegalStateException("A scan is already queued or running for this repository");
        RepositoryScan saved = scans.save(new RepositoryScan(operators.organizationId(), repository, connection.getDefaultBranch(), operators.subject()));
        audit.record("REPOSITORY_SCAN_REQUESTED", "REPOSITORY_SCAN", saved.getId(), repository);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RepositoryScan> list(String repository) {
        requireEnabled(repository);
        List<RepositoryScan> result = scans.findTop10ByOrganizationIdAndRepositoryOrderByCreatedAtDesc(
                operators.organizationId(), repository);
        // Initialize the lazy proposal histories before leaving this transaction (OSIV is disabled).
        // Hibernate batches these secondary loads instead of joining two List/bag collections at once.
        result.forEach(scan -> scan.getFindings().forEach(RepositoryScanFinding::getProposal));
        return result;
    }

    /** Claims a single queued scan in a transaction so two runners cannot both analyze it. */
    @Transactional
    public RepositoryScanGrant claim(Runner runner) {
        if (!runner.isEnabled()) throw new IllegalStateException("Runner is disabled");
        java.time.Instant staleBefore = java.time.Instant.now().minus(java.time.Duration.ofMinutes(30));
        List<RepositoryScan> claimable = scans.lockClaimableForOrganization(runner.getOrganizationId(), staleBefore, PageRequest.of(0, 1));
        if (claimable.isEmpty()) return null;
        RepositoryScan scan = claimable.getFirst();
        if (scan.requeueExpiredClaim(staleBefore))
            audit.record("REPOSITORY_SCAN_REQUEUED", "REPOSITORY_SCAN", scan.getId(), "expired-runner-claim");
        RepositoryConnection connection = connections.findByRepository(scan.getRepository())
                .filter(RepositoryConnection::isEnabled)
                .filter(item -> item.belongsTo(runner.getOrganizationId()))
                .orElseThrow(() -> new IllegalStateException("Repository authorization is no longer available"));
        scan.claim(runner.getId());
        audit.record("REPOSITORY_SCAN_CLAIMED", "REPOSITORY_SCAN", scan.getId(), runner.getId());
        return new RepositoryScanGrant(scan.getId(), scan.getRepository(), scan.getBaseBranch(), github.issueInstallationToken(connection.getInstallationId()));
    }

    @Transactional
    public RepositoryScan complete(String scanId, Runner runner, RepositoryScanResultInput input) {
        RepositoryScan scan = scans.lockById(scanId).orElseThrow(() -> new IllegalArgumentException("Repository scan not found"));
        if (!scan.getOrganizationId().equals(runner.getOrganizationId())) throw new IllegalArgumentException("Repository scan not found");
        if (!input.passed()) {
            scan.fail(runner.getId(), "The scan did not finish. Check the runner's provider settings and local logs, then retry.");
        } else {
            if (input.findings() == null || input.findings().size() > 20) throw new IllegalArgumentException("Repository scan finding count is invalid");
            if (input.provider() == null || !PROVIDERS.contains(input.provider())
                    || input.model() == null || input.model().isBlank() || input.model().length() > 200)
                throw new IllegalArgumentException("Repository scan provider metadata is invalid");
            List<RepositoryScanFinding> findings = input.findings().stream().map(RepositoryScanService::validatedFinding).toList();
            scan.complete(runner.getId(), input.commitSha(), input.provider(), input.model(), input.inputTokens(), input.outputTokens(),
                    input.estimatedCostMicros(), input.costKnown(), findings);
            providerActivities.record(runner.getOrganizationId(), "REPOSITORY_SCAN", scan.getRepository(), scan.getId(),
                    input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
            audit.record("REPOSITORY_SCAN_COMPLETED", "REPOSITORY_SCAN", scan.getId(), "findings=" + findings.size());
        }
        return scan;
    }

    private RepositoryConnection requireEnabled(String repository) {
        RepositoryConnection connection = connections.findByRepository(repository)
                .orElseThrow(() -> new IllegalArgumentException("Repository is not connected"));
        if (!connection.isEnabled() || !connection.belongsTo(operators.organizationId()))
            throw new IllegalArgumentException("Repository is not connected");
        return connection;
    }

    private static RepositoryScanFinding validatedFinding(RepositoryScanFindingInput input) {
        if (input == null || input.severity() == null || !SEVERITIES.contains(input.severity())) throw new IllegalArgumentException("Finding severity is invalid");
        String title = bounded(input.title(), 200, "title");
        if (title.lines().count() != 1) throw new IllegalArgumentException("Finding title must fit on one line");
        String description = bounded(input.description(), 3000, "description");
        String impact = bounded(input.impact(), 1200, "impact");
        String evidence = bounded(input.evidence(), 1600, "evidence");
        List<String> files = boundedList(input.affectedFiles(), 10, 300, "affected file");
        if (files.isEmpty() || files.stream().anyMatch(path -> !SAFE_PATH.matcher(path).matches() || path.startsWith("/")
                || path.matches(".*(^|/)\\.\\.(/|$).*"))) throw new IllegalArgumentException("Finding paths must be safe repository-relative paths");
        List<String> criteria = boundedList(input.acceptanceCriteria(), 6, 300, "acceptance criterion");
        if (criteria.isEmpty()) throw new IllegalArgumentException("Finding must include at least one acceptance criterion");
        return new RepositoryScanFinding(input.severity(), title, description, impact, evidence,
                String.join("\n", files), String.join("\n", criteria));
    }

    private static List<String> boundedList(List<String> values, int maxCount, int maxLength, String label) {
        if (values == null || values.size() > maxCount) throw new IllegalArgumentException("Finding " + label + " list is invalid");
        return values.stream().map(value -> bounded(value, maxLength, label)).toList();
    }
    private static String bounded(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength) throw new IllegalArgumentException("Finding " + label + " is invalid");
        return value.trim();
    }
}
