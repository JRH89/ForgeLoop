package io.forgeloop.runner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.*;
import static org.junit.jupiter.api.Assertions.*;
class DesktopFilesTest {
    @TempDir Path directory;
    @Test void restrictsInstallationDirectory()throws Exception{
        DesktopFiles.protect(directory);
        if(Files.getFileAttributeView(directory,PosixFileAttributeView.class)!=null)assertEquals("rwx------",PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
        else assertEquals(1,Files.getFileAttributeView(directory,AclFileAttributeView.class).getAcl().size());
    }
    @Test void windowsSecretRoundTripDoesNotPersistPlaintext()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(com.sun.jna.Platform.isWindows());
        DesktopFiles.protect(directory);var store=new DesktopSecretStore(directory);String secret="fake-key-for-offline-test";
        store.save(secret);assertEquals(secret,store.load());assertFalse(new String(Files.readAllBytes(directory.resolve("provider-key.dpapi")),java.nio.charset.StandardCharsets.ISO_8859_1).contains(secret));
        store.save("replacement-fake-key");assertEquals("replacement-fake-key",store.load());
    }
    @Test void rejectsMalformedKeysWithoutCallingNativeStorage(){var store=new DesktopSecretStore(directory);assertThrows(IllegalArgumentException.class,()->store.save(""));assertThrows(IllegalArgumentException.class,()->store.save("key\nother"));}
    @Test void replacesCompleteConfigurationAndCleansTemporaryFile()throws Exception{
        Path file=directory.resolve("config.json");DesktopFiles.writeAtomic(file,"old".getBytes());DesktopFiles.writeAtomic(file,"new".getBytes());assertEquals("new",Files.readString(file));try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
}
