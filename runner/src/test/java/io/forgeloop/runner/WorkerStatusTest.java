package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerStatusTest {
    @TempDir Path directory;
    @Test void publishesSafeAtomicSnapshotsAndKeepsLastContactDuringOutage() throws Exception {
        Path path=directory.resolve("worker-status.json");var status=new WorkerStatus(path);
        status.publish(WorkerStatus.Phase.STARTING,0);
        assertEquals(0,WorkerStatus.read(path).lastContactMillis());
        status.publish(WorkerStatus.Phase.IDLE,0);long contact=WorkerStatus.read(path).lastContactMillis();
        assertTrue(contact>0);status.publish(WorkerStatus.Phase.RECONNECTING,4);
        assertEquals(contact,WorkerStatus.read(path).lastContactMillis());
        assertTrue(WorkerStatus.read(path).label().contains("no new work claimed"));
        assertEquals(4,WorkerStatus.read(path).retrySeconds());
    }
    @Test void rejectsCorruptOversizedAndUnknownStatusFiles() throws Exception {
        Path path=directory.resolve("status");assertNull(WorkerStatus.read(path));
        Files.writeString(path,"x".repeat(1025));assertNull(WorkerStatus.read(path));
        Files.writeString(path,"{\"phase\":\"UNTRUSTED\"}");assertNull(WorkerStatus.read(path));
    }
    @Test void retryDelayIsBounded() {
        assertEquals(2,WorkerStatus.retrySeconds(1));assertEquals(4,WorkerStatus.retrySeconds(2));
        assertEquals(16,WorkerStatus.retrySeconds(1000));
    }
}
