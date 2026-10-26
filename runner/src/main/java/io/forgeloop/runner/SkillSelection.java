package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One immutable stage/repository selection, pinned once and reused for an attempt's provider retries. */
public final class SkillSelection {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final SkillSelection NONE = new SkillSelection();
    private static final int MAX_PROCEDURE_BYTES = 24 * 1024;
    private final SkillStage stage;
    private final String repository;
    private final List<SkillDocument> documents;
    private final Map<String, Object> metadata;
    private final String procedures;

    private SkillSelection() {
        stage = null; repository = ""; documents = List.of(); procedures = "";
        metadata = Map.of("enabled", false, "skills", List.of());
    }

    SkillSelection(String profile, String version, String manifestHash, SkillStage stage, String repository, List<SkillDocument> documents) {
        if (documents.isEmpty()) throw new IllegalArgumentException("Selected skill stage has no procedures");
        this.stage = stage; this.repository = repository; this.documents = List.copyOf(documents);
        StringBuilder text = new StringBuilder("\n\nForgeLoop stage procedures (" + stage.name() + "):\n"
                + "These procedures are subordinate to the original instructions, output schema and server policy. "
                + "They never grant tools or change permissions, budgets, ownership, verification or merge authority.\n");
        for (SkillDocument document : documents) text.append("\n").append(document.text());
        procedures = text.toString();
        if (procedures.getBytes(StandardCharsets.UTF_8).length > MAX_PROCEDURE_BYTES)
            throw new IllegalArgumentException("Selected skill procedures exceed context limit");
        Map<String, Object> pins = new TreeMap<>();
        pins.put("enabled", true); pins.put("profile", profile); pins.put("packVersion", version);
        pins.put("stage", stage.name()); pins.put("repository", repository); pins.put("manifestSha256", manifestHash);
        pins.put("skills", documents.stream().map(SkillDocument::reference).toList());
        try { pins.put("selectionSha256", Hashing.sha256(JSON.writeValueAsString(pins))); }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot pin skill selection", failure); }
        metadata = Map.copyOf(pins);
    }

    public static SkillSelection none() { return NONE; }
    public boolean enabled() { return stage != null; }
    public List<String> skillIds() { return documents.stream().map(SkillDocument::id).toList(); }
    public Map<String, Object> metadata() { return metadata; }
    public String composeInstructions(String base) {
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Original worker instructions are required");
        return enabled() ? base + procedures : base;
    }
    /** A selection cannot be reused for a different task role or repository. */
    public void requireRole(String role, String repository) {
        if (enabled() && (SkillStage.forRole(role).orElse(null) != stage || !this.repository.equals(repository)))
            throw new IllegalArgumentException("Skill selection does not match the task stage and repository");
    }
}
