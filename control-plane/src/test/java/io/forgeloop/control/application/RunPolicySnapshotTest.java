package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.forgeloop.control.domain.HarnessDefinition;
import io.forgeloop.control.domain.LocalMcpConfiguration;
import io.forgeloop.control.domain.OrganizationPolicy;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.VerificationPolicySpec;
import io.forgeloop.control.domain.FeatureRun;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunPolicySnapshotTest {
    private static final String IMAGE = "node@sha256:" + "a".repeat(64);

    @Test void canonicalSnapshotIsStableAndIncludesEnabledExecutionInputs() {
        RepositoryConnection connection = new RepositoryConnection("org-1", "acme/app", 12, "main", "forgeloop",
                "GENERIC", List.of(new VerificationPolicySpec("unit", "CONTAINER", IMAGE,
                List.of("npm", "test"), "NONE", 300, true, "ALL")), 25, true);
        connection.configureAssignmentPolicy(true, "release-manager");
        connection.configureEnforcement(List.of("src/generated/**"), false, null);
        OrganizationPolicy organization = new OrganizationPolicy("org-1", 100, 4, List.of("openai", "anthropic"), true, false);
        HarnessDefinition harness = new HarnessDefinition("org-1", "GENERIC", "Standard delivery", List.of("BACKEND", "PLANNER"), 2);
        LocalMcpConfiguration enabled = new LocalMcpConfiguration("org-1", "docs", "npx", List.of("-y", "docs-mcp"),
                List.of("BACKEND"), "search", "{\"limit\":4}");
        RunPolicySnapshot.Captured first = RunPolicySnapshot.capture(connection, organization, harness, List.of(enabled));
        RunPolicySnapshot.Captured second = RunPolicySnapshot.capture(connection, organization, harness, List.of(enabled));

        assertEquals(first, second);
        assertTrue(first.canonicalJson().contains("release-manager"));
        assertTrue(first.canonicalJson().contains("docs-mcp"));
        assertEquals(64, first.sha256().length());
    }

    @Test void setLikePolicyFieldsDoNotDependOnTheirInputOrder() {
        RepositoryConnection connection = connection();
        OrganizationPolicy firstPolicy = new OrganizationPolicy("org-1", 100, 4, List.of("openai", "anthropic"), true, false);
        OrganizationPolicy reorderedPolicy = new OrganizationPolicy("org-1", 100, 4, List.of("anthropic", "openai"), true, false);
        HarnessDefinition firstHarness = new HarnessDefinition("org-1", "GENERIC", "Standard delivery", List.of("BACKEND", "PLANNER"), 2);
        HarnessDefinition reorderedHarness = new HarnessDefinition("org-1", "GENERIC", "Standard delivery", List.of("PLANNER", "BACKEND"), 2);

        RunPolicySnapshot.Captured first = RunPolicySnapshot.capture(connection, firstPolicy, firstHarness, List.of());
        RunPolicySnapshot.Captured reordered = RunPolicySnapshot.capture(connection, reorderedPolicy, reorderedHarness, List.of());

        assertEquals(first, reordered);
    }

    @Test void runRejectsOversizedOrMismatchedPolicySnapshotAndPersistsVerifiedDigest() throws Exception {
        FeatureRun run = new FeatureRun("org-1", "acme/app", "issue-1", "title", "spec", 1, "GENERIC", 1);
        String canonicalJson = "{\"schema\":\"forgeloop.policy-snapshot/1\"}";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));

        assertThrows(IllegalArgumentException.class,
                () -> run.snapshotPolicy("x".repeat(65_537), "a".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> run.snapshotPolicy(canonicalJson, "a".repeat(64)));
        run.snapshotPolicy(canonicalJson, digest);

        assertEquals(canonicalJson, run.getPolicySnapshot());
        assertEquals(digest, run.getPolicySnapshotSha256());
    }

    private static RepositoryConnection connection() {
        return new RepositoryConnection("org-1", "acme/app", 12, "main", "forgeloop", "GENERIC",
                List.of(new VerificationPolicySpec("unit", "CONTAINER", IMAGE, List.of("npm", "test"),
                        "NONE", 300, true, "ALL")), 25, true);
    }
}
