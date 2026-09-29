package io.forgeloop.runner;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Restricts Java regex constructs that can cause catastrophic backtracking on repository text. */
final class SafeRegex {
    private SafeRegex() { }
    static Pattern compile(String expression) throws PatternSyntaxException {
        if (expression == null || expression.length() > 256 || hasBackReference(expression)
                || expression.matches("(?s).*\\([^)]*[+*{][^)]*\\)[+*{?].*")
                || expression.matches("(?s).*\\([^)]*\\|[^)]*\\)[+*{?].*"))
            throw new IllegalArgumentException("Search pattern uses an unsafe regular-expression construct");
        int quantifiers = 0;
        boolean escaped = false;
        boolean inCharacterClass = false;
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (value == '\\') { escaped = true; continue; }
            if (value == '[') { inCharacterClass = true; continue; }
            if (value == ']') { inCharacterClass = false; continue; }
            if (!inCharacterClass && (value == '+' || value == '*' || value == '?' || value == '{') && ++quantifiers > 4)
                throw new IllegalArgumentException("Search pattern has too many repetition operators");
        }
        return Pattern.compile(expression);
    }

    private static boolean hasBackReference(String expression) {
        boolean escaped = false;
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (escaped) { if (value >= '1' && value <= '9') return true; escaped = false; }
            else if (value == '\\') escaped = true;
        }
        return false;
    }
}
