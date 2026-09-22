package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.HexFormat;

/** Append-only runner verification evidence with a control-plane-calculated digest. */
@Entity
public class VerificationEvidence {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private DeliveryTask task;
    @ManyToOne(optional = false) private Runner runner;
    @Column(nullable = false, length = 80) private String kind;
    @Column(length = 120) private String gate;
    @Column(length = 255) private String image;
    @Column(nullable = false, length = 4000) private String command;
    private int exitCode;
    private boolean timedOut;
    @Column(nullable = false, length = 65536) private String output;
    @Column(nullable = false, unique = true, length = 64) private String digest;
    @Column(nullable = false) private Instant recordedAt;
    @Column(nullable = false) private Instant startedAt;
    @Column(nullable = false) private Instant finishedAt;
    @Column(length = 1000) private String artifactReference;
    @Column(nullable = false, length = 64) private String outputDigest;
    @Column(nullable = false, length = 64) private String bundleDigest;

    protected VerificationEvidence() { }

    public VerificationEvidence(DeliveryTask task, Runner runner, String kind, String gate, String image, List<String> command,
                                int exitCode, boolean timedOut, String output, Instant startedAt, Instant finishedAt,
                                String artifactReference, String outputDigest, String bundleDigest) {
        String calculatedOutput = digest(output);
        String calculatedBundle = bundleDigest(kind, gate, image, command, exitCode, timedOut, calculatedOutput, startedAt, finishedAt, artifactReference);
        if (!calculatedOutput.equals(outputDigest) || !calculatedBundle.equals(bundleDigest)) throw new IllegalArgumentException("Evidence checksum mismatch");
        this.task = task; this.runner = runner; this.kind = kind; this.gate = gate; this.image = image; this.command = String.join("\n", command);
        this.exitCode = exitCode; this.timedOut = timedOut; this.output = output; this.startedAt = startedAt; this.finishedAt = finishedAt;
        this.artifactReference = artifactReference; this.outputDigest = outputDigest; this.bundleDigest = bundleDigest; this.recordedAt = Instant.now();
        this.digest = digest(task.getId() + "\u0000" + runner.getId() + "\u0000" + bundleDigest);
    }

    public static String bundleDigest(String kind, String gate, String image, List<String> command, int exitCode, boolean timedOut, String outputDigest, Instant startedAt, Instant finishedAt, String artifactReference) {
        return digest(String.join("\u0000", kind, gate == null ? "" : gate, image == null ? "" : image, String.join("\u001f", command), Integer.toString(exitCode), Boolean.toString(timedOut), outputDigest, startedAt.toString(), finishedAt.toString(), artifactReference == null ? "" : artifactReference));
    }
    public static String digest(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    public String getId() { return id; } public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); } public String getKind() { return kind; }
    public String getGate() { return gate; }
    public String getImage() { return image; } public List<String> getCommand() { return command.lines().toList(); }
    public int getExitCode() { return exitCode; } public boolean isTimedOut() { return timedOut; }
    public String getOutput() { return output; } public String getDigest() { return digest; }
    public String getRecordedAt() { return recordedAt.toString(); }
    public String getStartedAt() { return startedAt.toString(); } public String getFinishedAt() { return finishedAt.toString(); }
    public String getArtifactReference() { return artifactReference; } public String getOutputDigest() { return outputDigest; } public String getBundleDigest() { return bundleDigest; }
}
