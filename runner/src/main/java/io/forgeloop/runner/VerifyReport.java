package io.forgeloop.runner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Accumulates checks without allowing one unverifiable item to hide a concrete failure. */
public final class VerifyReport {
    private static final ObjectMapper JSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final List<VerifyCheck> checks = new ArrayList<>();

    public void add(String id, String subject, VerifyVerdict verdict, String detail) {
        checks.add(new VerifyCheck(id, subject == null ? "run" : subject, verdict, detail));
    }

    public List<VerifyCheck> checks() { return List.copyOf(checks); }

    public int exitCode() {
        if (checks.stream().anyMatch(check -> check.verdict() == VerifyVerdict.FAIL)) return 1;
        if (checks.stream().anyMatch(check -> check.verdict() == VerifyVerdict.UNVERIFIABLE)) return 3;
        return 0;
    }

    public String toJson() throws IOException {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(new Report(exitCode(), checks()));
    }

    public void writeJson(Path destination) throws IOException {
        if (destination == null) throw new IllegalArgumentException("JSON report destination is required");
        Path normalized = destination.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) throw new IOException("JSON report cannot overwrite a symbolic link");
        if (normalized.getParent() != null) Files.createDirectories(normalized.getParent());
        Files.writeString(normalized, toJson(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public void printTo(java.io.PrintStream output) throws IOException { output.println(toJson()); }

    private record Report(int exitCode, List<VerifyCheck> checks) { }
}
