package io.forgeloop.runner;

import java.util.regex.Pattern;
import java.util.Optional;

/** Removes common credential forms before logs leave the local runner. */
final class EvidenceRedactor {
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|password|passwd|authorization|secret)[\"']?\\s*[:=]\\s*[\"']?([^\\s\"',;}]+)[\"']?");
    private static final Pattern TOKEN = Pattern.compile("(?i)(gh[opusr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,})");
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----");
    private static final Pattern GITHUB_TOKEN = Pattern.compile("(?i)gh[opusr]_[A-Za-z0-9_]{20,}");
    private EvidenceRedactor() { }
    static String redact(String value) { return PRIVATE_KEY.matcher(TOKEN.matcher(ASSIGNMENT.matcher(value).replaceAll("$1=[REDACTED]")).replaceAll("[REDACTED]")).replaceAll("[REDACTED PRIVATE KEY]"); }

    /** Finds only a category label; the credential itself is never returned or journaled. */
    static Optional<String> findCredentialToken(String value) {
        if (value == null) return Optional.empty();
        if (PRIVATE_KEY.matcher(value).find()) return Optional.of("private key");
        var token = TOKEN.matcher(value);
        if (!token.find()) return Optional.empty();
        return Optional.of(GITHUB_TOKEN.matcher(token.group()).find() ? "GitHub token" : "API key");
    }

    static CredentialRedaction redactCredentialTokens(String value) {
        if (value == null || value.isEmpty()) return new CredentialRedaction(value == null ? "" : value, 0);
        int[] count = {0};
        String withoutTokens = TOKEN.matcher(value).replaceAll(match -> { count[0]++; return "[REDACTED]"; });
        String redacted = PRIVATE_KEY.matcher(withoutTokens).replaceAll(match -> { count[0]++; return "[REDACTED PRIVATE KEY]"; });
        return new CredentialRedaction(redacted, count[0]);
    }

    record CredentialRedaction(String content, int count) { }
}
