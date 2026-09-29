package io.forgeloop.control.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.forgeloop.control.domain.AgentLoopBudget;
import io.forgeloop.control.domain.HarnessDefinition;
import io.forgeloop.control.domain.LocalMcpConfiguration;
import io.forgeloop.control.domain.LoopEnforcement;
import io.forgeloop.control.domain.OrganizationPolicy;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.VerificationPolicySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Builds a deterministic, content-addressed copy of the execution policies applied to a run. */
public final class RunPolicySnapshot {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private RunPolicySnapshot() { }

    public static Captured capture(RepositoryConnection connection, OrganizationPolicy organization,
                                   HarnessDefinition harness, List<LocalMcpConfiguration> mcpConfigurations) {
        if (connection == null || organization == null || harness == null)
            throw new IllegalArgumentException("Run policy snapshot inputs are incomplete");
        var repository = new Repository(connection.getRepository(), connection.getPolicyRevision(),
                connection.getDefaultBranch(), connection.getHarnessProfile(), connection.getMaxBudgetUsd(),
                connection.getIssueLabel(), connection.isRequireAssignee(), connection.getRequiredAssignee(),
                connection.getRequiredGates().stream().sorted().toList(),
                connection.getVerificationPolicies().stream().sorted(Comparator.comparing(VerificationPolicySpec::name)).toList(),
                connection.getTestFirstGate(), connection.getTestPathGlobs(), connection.getAgentLoopBudget(),
                connection.getEnforcement(), connection.isRunRecordEnabled());
        var org = new Organization(organization.getRevision(), organization.getMaxRunBudgetUsd(),
                organization.getMaxParallelTasks(), organization.getAllowedProviders().stream().sorted().toList(),
                organization.isRequireHumanApproval(), organization.isAutoMergeEnabled());
        var harnessSnapshot = new Harness(harness.getId(), harness.getName(), harness.getRevision(), harness.getDescription(),
                harness.getAllowedRoles().stream().sorted().toList(), harness.getDefaultAttemptBudget(), harness.isEnabled());
        List<McpConfiguration> mcp = (mcpConfigurations == null ? List.<LocalMcpConfiguration>of() : mcpConfigurations)
                .stream().filter(LocalMcpConfiguration::isEnabled).sorted(Comparator.comparing(LocalMcpConfiguration::getName))
                .map(configuration -> new McpConfiguration(configuration.getId(), configuration.getName(), configuration.getRevision(),
                        configuration.getCommand(), configuration.getArguments(), configuration.getAllowedRoles().stream().sorted().toList(),
                        configuration.getContextTool(), configuration.getToolArguments(), configuration.isEnabled()))
                .toList();
        try {
            String canonicalJson = JSON.writeValueAsString(new Document("forgeloop.policy-snapshot/1", repository, org, harnessSnapshot, mcp));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
            return new Captured(canonicalJson, digest);
        } catch (Exception failure) {
            throw new IllegalStateException("Run policy snapshot could not be serialized", failure);
        }
    }

    public record Captured(String canonicalJson, String sha256) {
        public Captured {
            if (canonicalJson == null || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Run policy snapshot is invalid");
        }
    }

    private record Document(String schema, Repository repository, Organization organization, Harness harness,
                            List<McpConfiguration> localMcpConfigurations) { }
    private record Repository(String repository, int policyRevision, String defaultBranch, String harnessProfile,
                              double maxBudgetUsd, String issueLabel, boolean requireAssignee, String requiredAssignee,
                              List<String> requiredGates, List<VerificationPolicySpec> verificationPolicies,
                              String testFirstGate, List<String> testPathGlobs, AgentLoopBudget agentLoopBudget,
                              LoopEnforcement enforcement, boolean runRecordEnabled) { }
    private record Organization(int revision, double maxRunBudgetUsd, int maxParallelTasks, List<String> allowedProviders,
                                boolean requireHumanApproval, boolean autoMergeEnabled) { }
    private record Harness(String id, String name, int revision, String description, List<String> allowedRoles,
                          int defaultAttemptBudget, boolean enabled) { }
    private record McpConfiguration(String id, String name, int revision, String command, List<String> arguments,
                                    List<String> allowedRoles, String contextTool, String toolArguments, boolean enabled) { }
}
