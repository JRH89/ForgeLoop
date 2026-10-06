package io.forgeloop.runner;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopPrerequisitesTest {
    @Test void acceptsGitAndLinuxContainerEngine() {
        var report = DesktopPrerequisites.check((tool, args) -> tool.equals("git")
                ? new DesktopPrerequisites.CommandResult(0, "git version 2.46.0")
                : new DesktopPrerequisites.CommandResult(0, "linux\n"));

        assertTrue(report.ready());
        assertEquals(DesktopPrerequisites.State.READY, report.git().state());
        assertEquals(DesktopPrerequisites.State.READY, report.docker().state());
    }

    @Test void distinguishesMissingGitFromUnavailableDockerEngine() {
        var report = DesktopPrerequisites.check((tool, args) -> {
            if (tool.equals("git")) throw new IllegalStateException("not found");
            return new DesktopPrerequisites.CommandResult(1, "daemon unavailable");
        });

        assertEquals(DesktopPrerequisites.State.MISSING, report.git().state());
        assertEquals(DesktopPrerequisites.State.NOT_RUNNING, report.docker().state());
        assertTrue(report.summary().contains("Git was not found"));
    }

    @Test void explainsWindowsContainerModeAndNeverTreatsItAsReady() {
        var report = DesktopPrerequisites.check((tool, args) -> tool.equals("git")
                ? new DesktopPrerequisites.CommandResult(0, "git version 2.46.0")
                : new DesktopPrerequisites.CommandResult(0, "windows\n"));

        assertFalse(report.ready());
        assertEquals(DesktopPrerequisites.State.WRONG_CONTAINER_MODE, report.docker().state());
        assertTrue(report.docker().detail().contains("Linux containers"));
    }

    @Test void permissionFailureIsNotTreatedAsAStoppedEngine() {
        for (String detail : java.util.List.of("permission denied", "Access is denied", "operation not permitted")) {
            var report = DesktopPrerequisites.check((tool, args) -> tool.equals("git")
                    ? new DesktopPrerequisites.CommandResult(0, "git version 2.46.0")
                    : new DesktopPrerequisites.CommandResult(1, detail));
            assertEquals(DesktopPrerequisites.State.ACCESS_DENIED, report.docker().state());
            assertFalse(report.ready());
            assertTrue(report.summary().contains("permissions"));
        }
    }

    @Test void checksBothDependenciesAndReturnsOfficialInstallationGuides() {
        AtomicInteger checks = new AtomicInteger();
        var report = DesktopPrerequisites.check((tool, args) -> {
            checks.incrementAndGet();
            throw new IOException("offline");
        });

        assertEquals(2, checks.get());
        assertTrue(report.git().installGuide().isAbsolute());
        assertTrue(report.docker().installGuide().isAbsolute());
        assertTrue(report.git().installGuide().getHost().equals("git-scm.com"));
        assertTrue(report.docker().installGuide().getHost().equals("docs.docker.com"));
    }

    @Test void selectsGuidesForTheOperatingSystemRunningTheApp() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String expected = os.contains("win") ? "win" : os.contains("mac") || os.contains("darwin") ? "mac" : "linux";

        assertTrue(DesktopPrerequisites.gitGuide().getPath().contains(expected));
        assertTrue(DesktopPrerequisites.dockerGuide().getPath().contains(expected));
    }
}
