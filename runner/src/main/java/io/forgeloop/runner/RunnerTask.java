package io.forgeloop.runner;

import java.util.List;

/** Immutable, server-derived work context a runner needs before it can safely prepare a worktree. */
public record RunnerTask(String id, String role, String title, String repository, String baseBranch,
                         String sourceRef, String specification, String requiredCapability, double budgetUsd,
                         List<String> ownedPaths, List<String> dependencyChangeShas, String verificationGateName,
                         String verificationKind, String verificationImageDigest, List<String> verificationCommand,
                         String verificationNetworkPolicy, Integer verificationTimeoutSeconds, String verificationBaseRef,
                         String executionBaseRef, List<String> acceptanceCriteria,List<LocalMcpConfiguration> mcpConfigurations,
                         String writeBoundary, List<String> testPathGlobs, String testReportFormat,
                         List<String> expectedTests, boolean expectedTestsOverflow, String testFirstEvidence,
                         RunnerRedPrerequisite redPrerequisite) {
    public RunnerTask {
        if (id == null || id.isBlank() || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || baseBranch == null || baseBranch.isBlank() || executionBaseRef == null || executionBaseRef.isBlank()
                || specification == null || specification.isBlank() || requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("Runner task context is incomplete");
        }
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
        dependencyChangeShas = dependencyChangeShas == null ? List.of() : List.copyOf(dependencyChangeShas);
        verificationCommand = verificationCommand == null ? List.of() : List.copyOf(verificationCommand);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        mcpConfigurations=mcpConfigurations==null?List.of():List.copyOf(mcpConfigurations);
        writeBoundary = writeBoundary == null ? "ANY" : writeBoundary;
        testPathGlobs = testPathGlobs == null ? List.of() : List.copyOf(testPathGlobs);
        expectedTests = expectedTests == null ? List.of() : List.copyOf(expectedTests);
        if (expectedTests.size() > 2_000 || expectedTests.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 500)) {
            throw new IllegalArgumentException("Expected test identities exceed their bound");
        }
        if (testFirstEvidence != null && testFirstEvidence.length() > 8_000) {
            throw new IllegalArgumentException("Test-first review evidence exceeds its bound");
        }
        if (!List.of("ANY", "TESTS_ONLY", "NO_TESTS").contains(writeBoundary)
                || (!"ANY".equals(writeBoundary) && !TestPathGlobs.areValid(testPathGlobs))) {
            throw new IllegalArgumentException("Task write boundary is invalid");
        }
        if (testReportFormat != null && !"JUNIT_XML".equals(testReportFormat)) {
            throw new IllegalArgumentException("Verification test report format is invalid");
        }
    }
    public RunnerTask(String id, String role, String title, String repository, String baseBranch,
                      String sourceRef, String specification, String requiredCapability) {
        this(id, role, title, repository, baseBranch, sourceRef, specification, requiredCapability, 0, List.of(), List.of(), null, null, null, List.of(), null, null, baseBranch, baseBranch, List.of(),List.of(), "ANY", List.of(), null, List.of(), false, null);
    }
    public RunnerTask(String id,String role,String title,String repository,String baseBranch,String sourceRef,String specification,String requiredCapability,double budgetUsd,List<String> ownedPaths,List<String> dependencyChangeShas,String verificationGateName,String verificationKind,String verificationImageDigest,List<String> verificationCommand,String verificationNetworkPolicy,Integer verificationTimeoutSeconds,String verificationBaseRef,String executionBaseRef,List<String> acceptanceCriteria,List<LocalMcpConfiguration> mcpConfigurations){this(id,role,title,repository,baseBranch,sourceRef,specification,requiredCapability,budgetUsd,ownedPaths,dependencyChangeShas,verificationGateName,verificationKind,verificationImageDigest,verificationCommand,verificationNetworkPolicy,verificationTimeoutSeconds,verificationBaseRef,executionBaseRef,acceptanceCriteria,mcpConfigurations, "ANY", List.of(), null, List.of(), false, null);}
    /** Preserves callers built against the test-first task contract before evidence fields were added. */
    public RunnerTask(String id,String role,String title,String repository,String baseBranch,String sourceRef,String specification,String requiredCapability,double budgetUsd,List<String> ownedPaths,List<String> dependencyChangeShas,String verificationGateName,String verificationKind,String verificationImageDigest,List<String> verificationCommand,String verificationNetworkPolicy,Integer verificationTimeoutSeconds,String verificationBaseRef,String executionBaseRef,List<String> acceptanceCriteria,List<LocalMcpConfiguration> mcpConfigurations,String writeBoundary,List<String> testPathGlobs,String testReportFormat){this(id,role,title,repository,baseBranch,sourceRef,specification,requiredCapability,budgetUsd,ownedPaths,dependencyChangeShas,verificationGateName,verificationKind,verificationImageDigest,verificationCommand,verificationNetworkPolicy,verificationTimeoutSeconds,verificationBaseRef,executionBaseRef,acceptanceCriteria,mcpConfigurations,writeBoundary,testPathGlobs,testReportFormat,List.of(),false,null);}
    /** Preserves callers using the complete task shape before RED evidence was dispatched. */
    public RunnerTask(String id,String role,String title,String repository,String baseBranch,String sourceRef,String specification,String requiredCapability,double budgetUsd,List<String> ownedPaths,List<String> dependencyChangeShas,String verificationGateName,String verificationKind,String verificationImageDigest,List<String> verificationCommand,String verificationNetworkPolicy,Integer verificationTimeoutSeconds,String verificationBaseRef,String executionBaseRef,List<String> acceptanceCriteria,List<LocalMcpConfiguration> mcpConfigurations,String writeBoundary,List<String> testPathGlobs,String testReportFormat,List<String> expectedTests,boolean expectedTestsOverflow,String testFirstEvidence){this(id,role,title,repository,baseBranch,sourceRef,specification,requiredCapability,budgetUsd,ownedPaths,dependencyChangeShas,verificationGateName,verificationKind,verificationImageDigest,verificationCommand,verificationNetworkPolicy,verificationTimeoutSeconds,verificationBaseRef,executionBaseRef,acceptanceCriteria,mcpConfigurations,writeBoundary,testPathGlobs,testReportFormat,expectedTests,expectedTestsOverflow,testFirstEvidence,null);}
    public RunnerTask(String id,String role,String title,String repository,String baseBranch,String sourceRef,String specification,String requiredCapability,double budgetUsd,List<String> ownedPaths,List<String> dependencyChangeShas,String verificationGateName,String verificationKind,String verificationImageDigest,List<String> verificationCommand,String verificationNetworkPolicy,Integer verificationTimeoutSeconds,String verificationBaseRef,String executionBaseRef,List<String> acceptanceCriteria){this(id,role,title,repository,baseBranch,sourceRef,specification,requiredCapability,budgetUsd,ownedPaths,dependencyChangeShas,verificationGateName,verificationKind,verificationImageDigest,verificationCommand,verificationNetworkPolicy,verificationTimeoutSeconds,verificationBaseRef,executionBaseRef,acceptanceCriteria,List.of(), "ANY", List.of(), null, List.of(), false, null);}
}
