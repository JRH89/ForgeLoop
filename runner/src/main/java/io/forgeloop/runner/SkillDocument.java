package io.forgeloop.runner;

import java.util.Map;
import java.util.Set;

/** One validated packaged procedure. Its text stays local; evidence exposes digest-only references. */
record SkillDocument(String id, String version, String sha256, String text, Set<SkillStage> stages) {
    SkillDocument { stages = Set.copyOf(stages); }
    Map<String, String> reference() { return Map.of("id", id, "version", version, "sha256", sha256); }
}
