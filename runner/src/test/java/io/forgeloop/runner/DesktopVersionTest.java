package io.forgeloop.runner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopVersionTest {
    private static final String KEY = "forgeloop.desktop.version";

    @Test void reportsThePackageVersionAndTreatsUnversionedBuildsAsDevelopment() {
        String previous = System.getProperty(KEY);
        try {
            System.setProperty(KEY, "1.0.12");
            assertEquals("1.0.12", DesktopVersion.current());
            System.setProperty(KEY, "not-a-version");
            assertEquals("development", DesktopVersion.current());
        } finally {
            if (previous == null) System.clearProperty(KEY); else System.setProperty(KEY, previous);
        }
    }
}
