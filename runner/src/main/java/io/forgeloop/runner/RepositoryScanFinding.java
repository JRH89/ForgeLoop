package io.forgeloop.runner;

import java.util.List;

public record RepositoryScanFinding(String severity, String title, String description, String impact,
                                    String evidence, List<String> affectedFiles, List<String> acceptanceCriteria) { }
