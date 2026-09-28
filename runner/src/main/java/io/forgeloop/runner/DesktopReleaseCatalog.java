package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates public GitHub release metadata before it is displayed as an update. */
public final class DesktopReleaseCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPOSITORY = "https://github.com/JRH89/ForgeLoop";
    private static final Pattern TAG = Pattern.compile("^desktop-v(\\d+\\.\\d+\\.\\d+)(?:-preview\\.(\\d+))?$");
    private static final Pattern ASSET = Pattern.compile("^forgeloop-runner-(\\d+\\.\\d+\\.\\d+)-(windows|macos|linux)-(x64|arm64)\\.(msi|dmg|deb)$");

    public record Target(String platform, String architecture, String extension) {
        public Target {
            if (!List.of("windows", "macos", "linux").contains(platform)
                    || !List.of("x64", "arm64").contains(architecture)
                    || !List.of("msi", "dmg", "deb").contains(extension)) {
                throw new IllegalArgumentException("Unsupported desktop package target");
            }
        }
        public static Target current() {
            String platform = com.sun.jna.Platform.isWindows() ? "windows"
                    : com.sun.jna.Platform.isMac() ? "macos"
                    : com.sun.jna.Platform.isLinux() ? "linux" : "unsupported";
            String architecture = normalizeArchitecture(com.sun.jna.Platform.ARCH);
            String extension = switch (platform) { case "windows" -> "msi"; case "macos" -> "dmg"; case "linux" -> "deb"; default -> ""; };
            return new Target(platform, architecture, extension);
        }
        private static String normalizeArchitecture(String value) {
            String arch = value.toLowerCase(java.util.Locale.ROOT);
            if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
            if (arch.contains("x86-64") || arch.contains("amd64") || arch.contains("x86_64")) return "x64";
            return "unsupported";
        }
    }

    public record Release(String version, boolean preview, URI releaseUrl, URI packageUrl,
                          String filename, String sha256) { }

    private DesktopReleaseCatalog() { }

    public static Release latest(String json, Target target) {
        try {
            JsonNode releases = JSON.readTree(json);
            if (releases == null || !releases.isArray()) return null;
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode entry : releases) {
                try {
                    Candidate candidate = candidate(entry, target);
                    if (candidate != null) candidates.add(candidate);
                } catch (RuntimeException malformedRelease) {
                    // One corrupt release must not hide other complete published releases.
                }
            }
            return candidates.stream().max(Comparator.comparing(Candidate::version, DesktopReleaseCatalog::compareVersions)
                    .thenComparing(Candidate::preview, Comparator.reverseOrder())
                    .thenComparingInt(Candidate::previewNumber)).map(Candidate::release).orElse(null);
        } catch (Exception malformed) {
            return null;
        }
    }

    private static Candidate candidate(JsonNode entry, Target target) {
        if (!entry.isObject() || !entry.path("draft").isBoolean() || entry.path("draft").asBoolean()
                || !entry.path("prerelease").isBoolean() || !entry.path("published_at").isTextual()
                || !entry.path("assets").isArray()) return null;
        String tagName = text(entry, "tag_name");
        Matcher tag = TAG.matcher(tagName);
        if (!tag.matches()) return null;
        boolean preview = tag.group(2) != null;
        if (preview != entry.path("prerelease").asBoolean()) return null;
        String version = tag.group(1);
        int previewNumber = preview ? Integer.parseInt(tag.group(2)) : Integer.MAX_VALUE;
        List<String> targets = new ArrayList<>();
        JsonNode packageAsset = null;
        boolean invalidPackage = false;
        for (JsonNode asset : entry.path("assets")) {
            Matcher filename = ASSET.matcher(text(asset, "name"));
            if (!filename.matches() || !"uploaded".equals(text(asset, "state")) || asset.path("size").asLong(0) <= 0) continue;
            String assetVersion = filename.group(1), platform = filename.group(2), architecture = filename.group(3), extension = filename.group(4);
            String expectedExtension = switch (platform) { case "windows" -> "msi"; case "macos" -> "dmg"; default -> "deb"; };
            String key = platform + "/" + architecture;
            if (!version.equals(assetVersion) || !expectedExtension.equals(extension) || targets.contains(key)) {
                invalidPackage = true;
                continue;
            }
            targets.add(key);
            String filenameValue = text(asset, "name");
            String expectedUrl = REPOSITORY + "/releases/download/" + tagName + "/" + filenameValue;
            if (!expectedUrl.equals(text(asset, "browser_download_url"))
                    || !text(asset, "digest").matches("^sha256:[a-f0-9]{64}$")) {
                invalidPackage = true;
                continue;
            }
            if (platform.equals(target.platform()) && architecture.equals(target.architecture())
                    && extension.equals(target.extension())) packageAsset = asset;
        }
        List<String> expectedTargets = List.of("windows/x64", "macos/arm64", "macos/x64", "linux/x64");
        if (invalidPackage || !targets.containsAll(expectedTargets) || targets.size() != expectedTargets.size() || packageAsset == null) return null;
        String packageName = text(packageAsset, "name");
        String digest = text(packageAsset, "digest");
        URI packageUrl = URI.create(text(packageAsset, "browser_download_url"));
        URI releaseUrl = URI.create(REPOSITORY + "/releases/tag/" + tagName);
        return new Candidate(version, preview, previewNumber,
                new Release(version, preview, releaseUrl, packageUrl, packageName, digest.substring("sha256:".length())));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int index = 0; index < 3; index++) {
            int order = new BigInteger(a[index]).compareTo(new BigInteger(b[index]));
            if (order != 0) return order;
        }
        return 0;
    }

    private record Candidate(String version, boolean preview, int previewNumber, Release release) { }
}
