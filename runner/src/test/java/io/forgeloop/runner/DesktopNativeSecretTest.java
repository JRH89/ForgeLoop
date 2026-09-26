package io.forgeloop.runner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class DesktopNativeSecretTest {
    @TempDir Path directory;
    @Test void nativeStoreReadsRotatesAndRemovesOnlyItsOwnTestKey()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(com.sun.jna.Platform.isWindows()||com.sun.jna.Platform.isMac()||"true".equals(System.getenv("FORGELOOP_TEST_KEYRING")));
        DesktopFiles.protect(directory);var store=new DesktopSecretStore(directory);
        try {store.save("fake-native-test-key");assertEquals("fake-native-test-key",store.load());store.save("rotated-fake-test-key");assertEquals("rotated-fake-test-key",store.load());}
        finally {store.remove();}
        assertThrows(Exception.class,store::load);
    }
}
