package io.forgeloop.control.application;

import java.util.regex.Pattern;

/** Defense-in-depth rejection for common credential formats that must never enter evidence storage. */
final class EvidenceSecretPolicy {
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization:\\s*(bearer|basic)|api[_-]?key\\s*[:=]|client[_-]?secret\\s*[:=]|-----BEGIN [A-Z ]*PRIVATE KEY-----|gh[opusr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,})");
    private EvidenceSecretPolicy() { }
    static void requireRedacted(String output) { if (SECRET.matcher(output).find()) throw new IllegalArgumentException("Evidence output contains a possible raw secret"); }
}
