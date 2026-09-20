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

    protected VerificationEvidence() { }

    public VerificationEvidence(DeliveryTask task, Runner runner, String kind, String gate, String image, String command,
                                int exitCode, boolean timedOut, String output) {
        this.task = task; this.runner = runner; this.kind = kind; this.gate = gate; this.image = image; this.command = command;
        this.exitCode = exitCode; this.timedOut = timedOut; this.output = output; this.recordedAt = Instant.now();
        this.digest = digest(kind, gate, image, command, exitCode, timedOut, output);
    }

    private String digest(String kind, String gate, String image, String command, int exitCode, boolean timedOut, String output) {
        try {
            String material = String.join("\u0000", kind, gate == null ? "" : gate, image == null ? "" : image, command,
                    Integer.toString(exitCode), Boolean.toString(timedOut), output);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    public String getId() { return id; } public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); } public String getKind() { return kind; }
    public String getGate() { return gate; }
    public String getImage() { return image; } public String getCommand() { return command; }
    public int getExitCode() { return exitCode; } public boolean isTimedOut() { return timedOut; }
    public String getOutput() { return output; } public String getDigest() { return digest; }
    public String getRecordedAt() { return recordedAt.toString(); }
}
