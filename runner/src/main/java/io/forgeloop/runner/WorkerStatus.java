package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded, secret-free child-process status; not inferred from potentially untrusted task logs. */
public final class WorkerStatus {
    public enum Phase { STARTING, IDLE, WORKING, RECONNECTING, ATTENTION, STOPPED }
    public record Snapshot(Phase phase, int retrySeconds, long lastContactMillis) {
        public String label() {
            return switch (phase) {
                case STARTING -> "Connecting - waiting for the control plane";
                case IDLE -> "Connected - waiting for eligible work";
                case WORKING -> "Working - current tasks may incur API charges";
                case RECONNECTING -> "Offline - reconnecting (retry delay " + retrySeconds + "s); no new work claimed";
                case ATTENTION -> "Stopped - server rejected the request; check runner access and logs";
                case STOPPED -> "Stopped - no work is being claimed";
            };
        }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path path;
    private long lastContactMillis;
    public WorkerStatus(Path path) { this.path = path; }
    public void publish(Phase phase, int retrySeconds) throws Exception {
        if (phase == Phase.IDLE || phase == Phase.WORKING) lastContactMillis = System.currentTimeMillis();
        if (path != null) DesktopFiles.writeAtomic(path, JSON.writeValueAsBytes(new Snapshot(phase, retrySeconds, lastContactMillis)));
    }
    public static Snapshot read(Path path) {
        try {
            if (Files.size(path) > 1024) return null;
            Snapshot value;
            try(var input=Files.newInputStream(path)) {
                byte[] bytes=input.readNBytes(1025);
                if(bytes.length>1024)return null;
                value=JSON.readValue(bytes,Snapshot.class);
            }
            return value.phase() == null || value.retrySeconds() < 0 || value.retrySeconds() > 30 ? null : value;
        } catch (Exception unavailable) { return null; }
    }
    /** Exponential delay is capped, including for very long outages. */
    static int retrySeconds(int failures) { return 1 << Math.min(4, Math.max(1, failures)); }
}
