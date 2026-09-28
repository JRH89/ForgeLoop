package io.forgeloop.runner;

import java.util.List;

public record RepositoryScanResult(String commitSha, ProviderUsageEvidence usage, List<RepositoryScanFinding> findings) { }
