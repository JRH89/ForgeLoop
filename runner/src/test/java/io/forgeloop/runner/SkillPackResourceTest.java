package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SkillPackResourceTest {
    @Test void experimentalPackIsBundledWithTheRunner() {
        assertNotNull(getClass().getResource("/skills/superpowers-experimental-v1/manifest.json"),
                "The opted-in skill pack must be packaged, not taken from a runner user's personal skills");
    }
    @Test void upstreamLicenseAndAttributionTravelWithThePack() throws Exception {
        try (var license = getClass().getResourceAsStream("/skills/superpowers-experimental-v1/LICENSE-superpowers.txt")) {
            assertNotNull(license);
            String text = new String(license.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("MIT License"));
            assertTrue(text.contains("Jesse Vincent"));
        }
        assertNotNull(getClass().getResource("/skills/superpowers-experimental-v1/NOTICE.md"));
    }

}
