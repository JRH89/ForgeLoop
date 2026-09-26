package io.forgeloop.runner;

import java.nio.file.*;

/** Allowlisted support data: never export key material, enrollment files, or raw task logs. */
final class DesktopDiagnostics {
    private DesktopDiagnostics() {}
    static String summary(Path directory, DesktopConfiguration config, boolean running) {
        return "ForgeLoop Runner desktop diagnostics\n"
            + "Installer generation: 1.0.1 development preview\n"
            + "OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch") + "\n"
            + "Java: " + System.getProperty("java.version") + "\n"
            + "Connection saved: " + Files.exists(directory.resolve("identity")) + "\n"
            + "Provider settings saved: " + (config!=null) + "\n"
            + "Worker running: " + running + "\n"
            + "Automatic work at sign-in: " + (config!=null&&config.startAtLogin()) + "\n"
            + "No keys, tokens, repository names, account identifiers, or task logs included.\n";
    }
}
