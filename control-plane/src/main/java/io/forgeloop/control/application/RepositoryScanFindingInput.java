package io.forgeloop.control.application;

import java.util.List;

/** Structured, source-free finding submitted by the authenticated customer runner. */
public record RepositoryScanFindingInput(String severity, String title, String description, String impact,
                                         String evidence, List<String> affectedFiles,
                                         List<String> acceptanceCriteria) { }
