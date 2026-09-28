package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        assertFalse(target.platform().equals("windows") && target.architecture().equals("arm64"));
        assertFalse(target.platform().equals("linux") && target.architecture().equals("arm64"));
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
        add(assets, version, tag, "macos", "arm64", "dmg");
        add(assets, version, tag, "macos", "x64", "dmg");
        add(assets, version, tag, "linux", "x64", "deb");
        return release;
    }

    private static void add(ArrayNode assets, String version, String tag, String platform, String arch, String extension) {
        String filename = "forgeloop-runner-" + version + "-" + platform + "-" + arch + "." + extension;
        ObjectNode asset = assets.addObject();asset.put("name", filename);asset.put("state", "uploaded");asset.put("size", 1024);
        asset.put("digest", "sha256:" + "a".repeat(64));asset.put("browser_download_url", "https://github.com/JRH89/ForgeLoop/releases/download/" + tag + "/" + filename);
    }
}
