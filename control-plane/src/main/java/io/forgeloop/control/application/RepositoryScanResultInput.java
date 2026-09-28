package io.forgeloop.control.application;

import java.util.List;

/** Runner telemetry and proposed work items; repository source is deliberately not a field here. */
public record RepositoryScanResultInput(boolean passed, String commitSha, String provider, String model,
                                        long inputTokens, long outputTokens, long estimatedCostMicros,
                                        boolean costKnown, String failureSummary,
                                        List<RepositoryScanFindingInput> findings) { }
