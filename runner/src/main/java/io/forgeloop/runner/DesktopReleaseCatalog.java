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
    private static final Pattern ASSET = Pattern.compile("^forgeloop-runner-(\\d+\\.\\d+\\.\\d+)-(windows|macos|linux)-(x64|arm64)\\.(msi|dmg|deb|rpm|AppImage|tar\\.gz|pkg\\.tar\\.zst)$");

    public record Target(String platform, String architecture, String extension) {
        public Target {
            if (!List.of("windows", "macos", "linux").contains(platform)
                    || !List.of("x64", "arm64").contains(architecture)
                    || !supportedPackage(platform, architecture, extension)) {
                throw new IllegalArgumentException("Unsupported desktop package target");
            }
        }
        public static Target current() {
            String platform = com.sun.jna.Platform.isWindows() ? "windows"
                    : com.sun.jna.Platform.isMac() ? "macos"
                    : com.sun.jna.Platform.isLinux() ? "linux" : "unsupported";
            String architecture = normalizeArchitecture(com.sun.jna.Platform.ARCH);
            String extension = switch (platform) {
                case "windows" -> "msi";
                case "macos" -> "dmg";
                case "linux" -> configuredLinuxPackage();
                default -> "";
            };
            return new Target(platform, architecture, extension);
        }
        private static String configuredLinuxPackage() {
            String configured = System.getProperty("forgeloop.desktop.package", "deb").toLowerCase(java.util.Locale.ROOT);
            return List.of("deb", "rpm", "appimage", "tar.gz", "pkg.tar.zst").contains(configured) ? configured : "deb";
        }
        private static boolean supportedPackage(String platform, String architecture, String extension) {
            return switch (platform) {
                case "windows" -> List.of("x64", "arm64").contains(architecture) && extension.equals("msi");
                case "macos" -> List.of("x64", "arm64").contains(architecture) && extension.equals("dmg");
                case "linux" -> List.of("x64", "arm64").contains(architecture)
                        && (List.of("deb", "rpm", "tar.gz", "pkg.tar.zst").contains(extension.toLowerCase(java.util.Locale.ROOT))
                        || architecture.equals("x64") && extension.equalsIgnoreCase("appimage"));
                default -> false;
            };
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
            String assetVersion = filename.group(1), platform = filename.group(2), architecture = filename.group(3), extension = filename.group(4).toLowerCase(java.util.Locale.ROOT);
            String key = platform + "/" + architecture + "/" + extension;
            if (!version.equals(assetVersion) || !Target.supportedPackage(platform, architecture, extension) || targets.contains(key)) {
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
                    && extension.equalsIgnoreCase(target.extension())) packageAsset = asset;
        }
        List<String> legacyTargets = List.of("windows/x64/msi", "macos/arm64/dmg", "macos/x64/dmg", "linux/x64/deb");
        List<String> currentTargets = List.of("windows/x64/msi", "macos/arm64/dmg", "macos/x64/dmg",
                "linux/x64/deb", "linux/x64/rpm", "linux/x64/appimage");
        List<String> expandedTargets = List.of(
                "windows/x64/msi", "windows/arm64/msi", "macos/arm64/dmg", "macos/x64/dmg",
                "linux/x64/deb", "linux/x64/rpm", "linux/x64/tar.gz", "linux/x64/pkg.tar.zst",
                "linux/arm64/deb", "linux/arm64/rpm", "linux/arm64/tar.gz", "linux/arm64/pkg.tar.zst");
        // Accept published migration-era releases while requiring every new target.
        boolean completeTargetSet = (targets.size() == legacyTargets.size() && targets.containsAll(legacyTargets))
                || (targets.size() == currentTargets.size() && targets.containsAll(currentTargets))
                || (targets.size() == expandedTargets.size() && targets.containsAll(expandedTargets));
        if (invalidPackage || !completeTargetSet || packageAsset == null) return null;
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
