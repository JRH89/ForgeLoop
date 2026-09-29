package io.forgeloop.control.application;

import java.util.regex.Pattern;

/** Upload-side guard matching the token and private-key patterns used by the runner record redactor. */
final class RecordSecretPolicy {
    private static final Pattern TOKEN = Pattern.compile("(?i)(gh[opusr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,})");
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----");

    private RecordSecretPolicy() { }

    static boolean containsPossibleSecret(String content) {
        return content != null && (TOKEN.matcher(content).find() || PRIVATE_KEY.matcher(content).find());
    }
}
