package io.forgeloop.runner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Bounded classpath loader. Manifests cannot name file paths, dependencies, commands, or network resources. */
final class SkillPack {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final int MAX_MANIFEST_BYTES = 32 * 1024;
    private static final int MAX_SKILL_BYTES = 8 * 1024;
    private final String profile;
    private final String version;
    private final String manifestSha256;
    private final List<SkillDocument> skills;

    private SkillPack(String profile, String version, String digest, List<SkillDocument> skills) {
        this.profile = profile; this.version = version; this.manifestSha256 = digest; this.skills = List.copyOf(skills);
    }

    static SkillPack load(String profile, Function<String, InputStream> resources) {
        String root = "/skills/" + profile + "/";
        try {
            String manifest = read(resources, root + "manifest.json", MAX_MANIFEST_BYTES);
            JsonNode value = JSON.readTree(manifest);
            fields(value, Set.of("schema", "profile", "version", "upstream", "skills"));
            require("forgeloop.skill-pack/1".equals(text(value, "schema")) && profile.equals(text(value, "profile")), "Invalid skill pack identity");
            String version = version(value);
            JsonNode upstream = value.path("upstream");
            fields(upstream, Set.of("repository", "commit", "license"));
            require("https://github.com/obra/superpowers".equals(text(upstream, "repository"))
                    && text(upstream, "commit").matches("[0-9a-f]{40}") && "MIT".equals(text(upstream, "license")), "Invalid skill provenance");
            JsonNode entries = value.path("skills");
            require(entries.isArray() && !entries.isEmpty() && entries.size() <= 16, "Invalid skill list");
            List<SkillDocument> documents = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode entry : entries) {
                fields(entry, Set.of("id", "version", "sha256", "stages"));
                String id = text(entry, "id");
                require(id.matches("[a-z][a-z0-9-]{0,63}") && ids.add(id), "Invalid or duplicate skill ID");
                String sha256 = text(entry, "sha256");
                require(sha256.matches("[0-9a-f]{64}"), "Invalid skill digest");
                JsonNode stageValues = entry.path("stages");
                require(stageValues.isArray() && !stageValues.isEmpty() && stageValues.size() <= SkillStage.values().length, "Invalid skill stages");
                Set<SkillStage> stages = new HashSet<>();
                for (JsonNode stage : stageValues) {
                    require(stage.isTextual() && stages.add(SkillStage.valueOf(stage.asText())), "Invalid or duplicate skill stage");
                }
                String content = read(resources, root + id + "/SKILL.md", MAX_SKILL_BYTES);
                require(!content.isBlank() && sha256.equals(Hashing.sha256(content)), "Skill content integrity check failed");
                documents.add(new SkillDocument(id, version(entry), sha256, content, stages));
            }
            return new SkillPack(profile, version, Hashing.sha256(manifest), documents);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Bundled skill pack is missing or invalid", invalid);
        }
    }

    SkillSelection select(SkillStage stage, String repository) {
        return new SkillSelection(profile, version, manifestSha256, stage, repository,
                skills.stream().filter(skill -> skill.stages().contains(stage)).toList());
    }

    private static String read(Function<String, InputStream> resources, String path, int limit) throws Exception {
        try (InputStream input = resources.apply(path)) {
            require(input != null, "Missing skill resource");
            byte[] bytes = input.readNBytes(limit + 1);
            require(bytes.length > 0 && bytes.length <= limit, "Skill resource exceeds size limit");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
    private static String text(JsonNode node, String field) {
        require(node.path(field).isTextual(), "Missing skill manifest field");
        return node.path(field).asText();
    }
    private static String version(JsonNode value) {
        String version = text(value, "version");
        require(version.length() <= 24 && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"), "Invalid skill version");
        return version;
    }
    private static void fields(JsonNode node, Set<String> expected) {
        require(node != null && node.isObject() && node.size() == expected.size(), "Unexpected skill manifest fields");
        node.fieldNames().forEachRemaining(field -> require(expected.contains(field), "Unexpected skill manifest field"));
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
