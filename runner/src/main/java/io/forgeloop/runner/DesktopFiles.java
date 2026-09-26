package io.forgeloop.runner;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Private per-user state is separate from the installed application and survives upgrades. */
public final class DesktopFiles {
    private DesktopFiles(){}
    public static Path directory() throws Exception {
        Path directory=Path.of(System.getProperty("user.home"),".forgeloop","desktop-runner");
        Files.createDirectories(directory);
        protect(directory);
        return directory;
    }
    public static void protect(Path path)throws Exception{
        var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class);
        if(posix!=null){posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(path)?"rwx------":"rw-------"));return;}
        var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
        if(acl==null)throw new IllegalStateException("Owner-only file permissions are required");
        var builder=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(Files.isDirectory(path))builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
        acl.setAcl(List.of(builder.build()));
    }
}
