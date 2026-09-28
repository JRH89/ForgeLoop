package io.forgeloop.runner;

/** Reads the immutable native package version supplied by jpackage at build time. */
public final class DesktopVersion {
    private static final String VERSION_PROPERTY = "forgeloop.desktop.version";
    private static final String VERSION_PATTERN = "[1-9][0-9]*\\.[0-9]+\\.[0-9]+";

    private DesktopVersion() { }

    public static String current() {
        String configured = System.getProperty(VERSION_PROPERTY, "");
        return configured.matches(VERSION_PATTERN) ? configured : "development";
    }
}
