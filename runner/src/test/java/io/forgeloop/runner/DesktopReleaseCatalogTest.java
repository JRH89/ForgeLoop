package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopReleaseCatalogTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DesktopReleaseCatalog.Target WINDOWS = new DesktopReleaseCatalog.Target("windows", "x64", "msi");

    @Test void selectsNewestCompletePublishedReleaseAndItsOwnPackageDigest() throws Exception {
        var selected = DesktopReleaseCatalog.latest(JSON.writeValueAsString(array(
                release("1.0.9", true), release("1.0.10", true))), WINDOWS);

        assertNotNull(selected);
        assertEquals("1.0.10", selected.version());
        assertTrue(selected.preview());
        assertEquals("forgeloop-runner-1.0.10-windows-x64.msi", selected.filename());
        assertEquals("a".repeat(64), selected.sha256());
        assertEquals("https://github.com/JRH89/ForgeLoop/releases/download/desktop-v1.0.10-preview.1/forgeloop-runner-1.0.10-windows-x64.msi", selected.packageUrl().toString());
    }

    @Test void selectsEachCurrentLinuxFormatAndArchitectureAsADistinctUpdaterTarget() throws Exception {
        String feed = JSON.writeValueAsString(array(release("1.0.10", true)));
        for (String format : List.of("deb", "rpm", "tar.gz", "pkg.tar.zst")) {
            var selected = DesktopReleaseCatalog.latest(feed, new DesktopReleaseCatalog.Target("linux", "x64", format));
            assertNotNull(selected, format);
            assertEquals("forgeloop-runner-1.0.10-linux-x64." + format, selected.filename());
        }
        var arm64 = DesktopReleaseCatalog.latest(feed, new DesktopReleaseCatalog.Target("linux", "arm64", "pkg.tar.zst"));
        assertNotNull(arm64);
        assertEquals("forgeloop-runner-1.0.10-linux-arm64.pkg.tar.zst", arm64.filename());
    }

    @Test void selectsTheWindowsArm64InstallerForAnArm64Runner() throws Exception {
        String feed = JSON.writeValueAsString(array(release("1.0.10", true)));
        var arm64 = DesktopReleaseCatalog.latest(feed, new DesktopReleaseCatalog.Target("windows", "arm64", "msi"));

        assertNotNull(arm64);
        assertEquals("forgeloop-runner-1.0.10-windows-arm64.msi", arm64.filename());
    }

    @Test void keepsLegacyDebOnlyReleaseAvailableDuringMigration() throws Exception {
        ObjectNode legacy = previousRelease("1.0.5", true);
        ArrayNode assets = (ArrayNode) legacy.path("assets");
        for (int index = assets.size() - 1; index >= 0; index--) {
            if (!assets.get(index).path("name").asText().matches(".*(?:windows-x64\\.msi|macos-(?:arm64|x64)\\.dmg|linux-x64\\.deb)$")) assets.remove(index);
        }

        String feed = JSON.writeValueAsString(array(legacy));
        var deb = DesktopReleaseCatalog.latest(feed, new DesktopReleaseCatalog.Target("linux", "x64", "deb"));
        var rpm = DesktopReleaseCatalog.latest(feed, new DesktopReleaseCatalog.Target("linux", "x64", "rpm"));

        assertNotNull(deb);
        assertEquals("forgeloop-runner-1.0.5-linux-x64.deb", deb.filename());
        assertNull(rpm);
    }

    @Test void preservesAppImageUpdateLookupForPreviouslyPublishedRelease() throws Exception {
        String feed=JSON.writeValueAsString(array(previousRelease("1.0.10",true)));
        var appImage=DesktopReleaseCatalog.latest(feed,new DesktopReleaseCatalog.Target("linux","x64","appimage"));
        assertNotNull(appImage);
        assertEquals("forgeloop-runner-1.0.10-linux-x64.AppImage",appImage.filename());
    }

    @Test void prefersStableReleaseOverPreviewOfSameVersion() throws Exception {
        var selected = DesktopReleaseCatalog.latest(JSON.writeValueAsString(array(
                release("1.0.10", true), release("1.0.10", false))), WINDOWS);

        assertNotNull(selected);
        assertFalse(selected.preview());
        assertEquals("https://github.com/JRH89/ForgeLoop/releases/tag/desktop-v1.0.10", selected.releaseUrl().toString());
    }

    @Test void ignoresDraftsIncompleteAssetsAndTamperedUrlsOrDigests() throws Exception {
        ObjectNode draft = release("2.0.0", true);draft.put("draft", true);
        ObjectNode incomplete = release("2.0.1", true);((ArrayNode)incomplete.path("assets")).remove(0);
        ObjectNode wrongUrl = release("2.0.2", true);((ObjectNode)wrongUrl.path("assets").get(0)).put("browser_download_url", "https://example.com/installer.msi");
        ObjectNode noDigest = release("2.0.3", true);((ObjectNode)noDigest.path("assets").get(0)).put("digest", "");
        var selected = DesktopReleaseCatalog.latest(JSON.writeValueAsString(array(
                draft, incomplete, wrongUrl, noDigest, release("1.0.10", true))), WINDOWS);

        assertNotNull(selected);
        assertEquals("1.0.10", selected.version());
    }

    @Test void rejectsMalformedFeedAndWrongPreviewTag() throws Exception {
        ObjectNode mislabeled = release("3.0.0", true);mislabeled.put("tag_name", "desktop-v3.0.0");
        assertNull(DesktopReleaseCatalog.latest("{invalid", WINDOWS));
        assertNull(DesktopReleaseCatalog.latest("{}", WINDOWS));
        assertNull(DesktopReleaseCatalog.latest(JSON.writeValueAsString(array(mislabeled)), WINDOWS));
    }

    @Test void mapsEachSupportedCiHostToItsPublishedPackageTarget() {
        var target = DesktopReleaseCatalog.Target.current();
        assertTrue(java.util.List.of("windows", "macos", "linux").contains(target.platform()));
        assertTrue(java.util.List.of("x64", "arm64").contains(target.architecture()));
        assertEquals(switch (target.platform()) { case "windows" -> "msi"; case "macos" -> "dmg"; default -> "deb"; }, target.extension());
        assertThrows(IllegalArgumentException.class, () -> new DesktopReleaseCatalog.Target("windows", "x64", "rpm"));
        assertThrows(IllegalArgumentException.class, () -> new DesktopReleaseCatalog.Target("linux", "arm64", "appimage"));
        if (target.platform().equals("linux")) {
            String previous = System.getProperty("forgeloop.desktop.package");
            try {
                System.setProperty("forgeloop.desktop.package", "rpm");
                assertEquals("rpm", DesktopReleaseCatalog.Target.current().extension());
                System.setProperty("forgeloop.desktop.package", "appimage");
                assertEquals("appimage", DesktopReleaseCatalog.Target.current().extension());
                System.setProperty("forgeloop.desktop.package", "tar.gz");
                assertEquals("tar.gz", DesktopReleaseCatalog.Target.current().extension());
                System.setProperty("forgeloop.desktop.package", "pkg.tar.zst");
                assertEquals("pkg.tar.zst", DesktopReleaseCatalog.Target.current().extension());
            } finally {
                if (previous == null) System.clearProperty("forgeloop.desktop.package");
                else System.setProperty("forgeloop.desktop.package", previous);
            }
        }
    }

    private static ArrayNode array(ObjectNode... releases) {
        ArrayNode result = JSON.createArrayNode();
        for (ObjectNode release : releases) result.add(release);
        return result;
    }

    private static ObjectNode release(String version, boolean preview) {
        String tag = "desktop-v" + version + (preview ? "-preview.1" : "");
        ObjectNode release = JSON.createObjectNode();
        release.put("draft", false);release.put("prerelease", preview);release.put("published_at", "2026-09-27T00:00:00Z");release.put("tag_name", tag);
        ArrayNode assets = release.putArray("assets");
        add(assets, version, tag, "windows", "x64", "msi");
        add(assets, version, tag, "windows", "arm64", "msi");
        add(assets, version, tag, "macos", "arm64", "dmg");
        add(assets, version, tag, "macos", "x64", "dmg");
        for (String arch : List.of("x64", "arm64")) {
            for (String format : List.of("deb", "rpm", "tar.gz", "pkg.tar.zst")) {
                add(assets, version, tag, "linux", arch, format);
            }
        }
        return release;
    }

    private static ObjectNode previousRelease(String version,boolean preview) {
        ObjectNode previous=release(version,preview);
        ArrayNode assets=(ArrayNode)previous.path("assets");
        // Migration-era releases included AppImage before portable/Arch targets existed.
        add(assets, version, previous.path("tag_name").asText(), "linux", "x64", "AppImage");
        List<String> suffixes=List.of("windows-x64.msi","macos-arm64.dmg","macos-x64.dmg",
                "linux-x64.deb","linux-x64.rpm","linux-x64.AppImage");
        for(int index=assets.size()-1;index>=0;index--) {
            String name=assets.get(index).path("name").asText();
            if(suffixes.stream().noneMatch(name::endsWith)) assets.remove(index);
        }
        return previous;
    }

    private static void add(ArrayNode assets, String version, String tag, String platform, String arch, String extension) {
        String filename = "forgeloop-runner-" + version + "-" + platform + "-" + arch + "." + extension;
        ObjectNode asset = assets.addObject();asset.put("name", filename);asset.put("state", "uploaded");asset.put("size", 1024);
        asset.put("digest", "sha256:" + "a".repeat(64));asset.put("browser_download_url", "https://github.com/JRH89/ForgeLoop/releases/download/" + tag + "/" + filename);
    }
}
