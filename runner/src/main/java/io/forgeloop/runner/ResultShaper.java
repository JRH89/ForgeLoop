package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Caps tool material entering provider context while leaving complete output in the local journal. */
public final class ResultShaper {
    public static final int MAX_RESULT_BYTES = 16 * 1024;
    private static final int GATE_HEAD_BYTES = 4 * 1024;
    private static final int GATE_TAIL_BYTES = 12 * 1024;

    public String shape(String toolName, ToolOutcome outcome, int maxBytes) {
        if (outcome == null || maxBytes < 1) throw new IllegalArgumentException("Tool result shaping request is invalid");
        int limit = Math.min(maxBytes, MAX_RESULT_BYTES);
        String content = outcome.status() == ToolStatus.FAILED
                ? "ERROR [" + outcome.category() + "]: " + outcome.content() : outcome.content();
        if (byteLength(content) <= limit) return content;
        if ("run_gate".equals(toolName)) return shapeGate(content, limit, outcome);
        return shapeLines(content, limit);
    }

    private String shapeGate(String content, int limit, ToolOutcome outcome) {
        int firstNewline = content.indexOf('\n');
        String heading = firstNewline < 0 ? "" : content.substring(0, firstNewline + 1);
        String output = firstNewline < 0 ? content : content.substring(firstNewline + 1);
        String digest = String.valueOf(outcome.meta().getOrDefault("outputSha256", Hashing.sha256(output)));
        int omitted = Math.max(0, byteLength(output) - GATE_HEAD_BYTES - GATE_TAIL_BYTES);
        String marker = "\n[" + omitted + " bytes omitted; full output kept in the run journal, sha256 " + digest + "]\n";
        int bodyBytes = Math.max(0, limit - byteLength(heading) - byteLength(marker));
        int headBytes = Math.min(GATE_HEAD_BYTES, bodyBytes / 3);
        int tailBytes = Math.min(GATE_TAIL_BYTES, bodyBytes - headBytes);
        String head = takeHead(output, headBytes);
        String tail = takeTail(output, tailBytes);
        String combined = heading + head + marker + tail;
        return fitCodePoints(combined, limit);
    }

    private String shapeLines(String content, int limit) {
        String[] lines = content.split("\\n", -1);
        StringBuilder body = new StringBuilder();
        int retainedLines = 0;
        boolean cut = false;
        String footer = "";
        for (int index = 0; index < lines.length; index++) {
            if (index == lines.length - 1 && lines[index].isEmpty()) break;
            String renderedLine = lines[index] + (index < lines.length - 1 ? "\n" : "");
            int next = nextLineNumber(lines, index);
            String candidateFooter = "[page cut at line " + next + "; continue with startLine=" + next + "]";
            if (byteLength(body.toString()) + byteLength(renderedLine) + byteLength("\n" + candidateFooter) > limit) {
                cut = true;
                int continuation = body.isEmpty() ? next + 1 : next;
                footer = "[page cut at line " + next + "; continue with startLine=" + continuation + "]";
                if (body.isEmpty()) {
                    int room = Math.max(1, limit - byteLength("\n" + footer));
                    body.append(takeHead(renderedLine, room));
                }
                break;
            }
            body.append(renderedLine);
            retainedLines++;
        }
        if (!cut && retainedLines < lines.length) {
            int next = nextLineNumber(lines, retainedLines);
            footer = "[page cut at line " + next + "; continue with startLine=" + next + "]";
            cut = byteLength(body.toString()) > limit;
        }
        String shaped = cut ? body + "\n" + footer : body.toString();
        return fitCodePoints(shaped, limit);
    }

    private int nextLineNumber(String[] lines, int from) {
        for (int index = Math.min(from, lines.length - 1); index < lines.length; index++) {
            String line = lines[index].stripLeading();
            int lastColon = line.lastIndexOf(':');
            if (lastColon > 0) {
                int previousColon = line.lastIndexOf(':', lastColon - 1);
                String candidate = line.substring(previousColon + 1, lastColon).strip();
                try { return Integer.parseInt(candidate); }
                catch (NumberFormatException ignored) { /* Use ordinal fallback below. */ }
            }
            break;
        }
        return Math.max(1, from + 1);
    }

    private static String takeHead(String value, int maxBytes) {
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) break;
            result.appendCodePoint(point); bytes += size; offset += Character.charCount(point);
        }
        return result.toString();
    }

    private static String takeTail(String value, int maxBytes) {
        List<Integer> points = new ArrayList<>();
        int bytes = 0;
        for (int offset = value.length(); offset > 0;) {
            int point = value.codePointBefore(offset);
            int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) break;
            points.add(point); bytes += size; offset -= Character.charCount(point);
        }
        StringBuilder result = new StringBuilder();
        for (int index = points.size() - 1; index >= 0; index--) result.appendCodePoint(points.get(index));
        return result.toString();
    }

    private static String fitCodePoints(String value, int maxBytes) { return byteLength(value) <= maxBytes ? value : takeHead(value, maxBytes); }
    private static int byteLength(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
