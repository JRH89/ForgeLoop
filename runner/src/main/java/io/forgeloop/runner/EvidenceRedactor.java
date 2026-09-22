package io.forgeloop.runner;

import java.util.regex.Pattern;

/** Removes common credential forms before logs leave the local runner. */
final class EvidenceRedactor {
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(api[_-]?key|client[_-]?secret|authorization)\\s*[:=]\\s*([^\\s]+)");
    private static final Pattern TOKEN = Pattern.compile("(?i)(gh[opusr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,})");
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----");
    private EvidenceRedactor() { }
    static String redact(String value) { return PRIVATE_KEY.matcher(TOKEN.matcher(ASSIGNMENT.matcher(value).replaceAll("$1=[REDACTED]")).replaceAll("[REDACTED]")).replaceAll("[REDACTED PRIVATE KEY]"); }
}
