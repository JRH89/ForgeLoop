package io.forgeloop.runner;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Exact runner-local opt-in to a trusted packaged profile. No target-repository or personal skill files are read. */
public final class StageSkillResolver {
    public static final String PROFILE = "superpowers-experimental-v1";
    private final String profile;
    private final Set<String> repositories;
    private final Function<String, InputStream> source;
    private SkillPack pack;

    public StageSkillResolver(String profile, Set<String> repositories) {
        this(profile, repositories, StageSkillResolver.class::getResourceAsStream);
    }
    StageSkillResolver(String profile, Set<String> repositories, Function<String, InputStream> source) {
        this.profile = profile == null ? "" : profile.trim();
        if (!this.profile.isEmpty() && !"off".equals(this.profile) && !PROFILE.equals(this.profile))
            throw new IllegalArgumentException("Unknown skill profile");
        if (repositories == null || source == null) throw new IllegalArgumentException("Skill configuration is incomplete");
        this.repositories = Set.copyOf(repositories); this.source = source;
        if (this.repositories.size() > 100 || this.repositories.stream().anyMatch(value -> !validRepository(value))
                || PROFILE.equals(this.profile) && this.repositories.isEmpty())
            throw new IllegalArgumentException("An enabled skill profile requires an exact repository allowlist");
    }

    public static StageSkillResolver fromEnvironment() { return fromEnvironment(System.getenv()); }
    static StageSkillResolver fromEnvironment(Map<String, String> environment) {
        String profile = environment.getOrDefault("FORGELOOP_SKILL_PROFILE", "").trim();
        if (profile.isEmpty() || "off".equals(profile)) return new StageSkillResolver("", Set.of());
        String raw = environment.getOrDefault("FORGELOOP_SKILL_REPOSITORIES", "");
        if (raw.length() > 24 * 1024) throw new IllegalArgumentException("Skill repository allowlist is too large");
        Set<String> repositories = Arrays.stream(raw.split(",", -1)).map(String::trim).collect(Collectors.toUnmodifiableSet());
        return new StageSkillResolver(profile, repositories);
    }

    public SkillSelection resolveRole(String role, String repository) {
        var stage = SkillStage.forRole(role);
        if (!validRepository(repository)) throw new IllegalArgumentException("Invalid skill repository");
        if (!PROFILE.equals(profile) || !repositories.contains(repository) || stage.isEmpty()) return SkillSelection.none();
        return pack().select(stage.get(), repository);
    }

    private synchronized SkillPack pack() {
        if (pack == null) pack = SkillPack.load(profile, source);
        return pack;
    }
    private static boolean validRepository(String repository) {
        return repository != null && repository.length() <= 200 && repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }
}
