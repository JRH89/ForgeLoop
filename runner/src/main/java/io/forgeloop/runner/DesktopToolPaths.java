package io.forgeloop.runner;
import java.nio.file.*;
import java.util.*;
import java.io.File;

/** GUI/login sessions often lack the PATH configured by an interactive shell. */
public final class DesktopToolPaths {
    private DesktopToolPaths(){}
    public static String searchPath(){
        var directories=new LinkedHashSet<String>();
        String existing=System.getenv("PATH");if(existing!=null)directories.addAll(Arrays.asList(existing.split(java.util.regex.Pattern.quote(File.pathSeparator))));
        if(com.sun.jna.Platform.isMac()){directories.add("/usr/local/bin");directories.add("/opt/homebrew/bin");directories.add("/Applications/Docker.app/Contents/Resources/bin");directories.add(Path.of(System.getProperty("user.home"),".docker","bin").toString());}
        else if(com.sun.jna.Platform.isWindows()){String programs=System.getenv("ProgramFiles");if(programs!=null){directories.add(Path.of(programs,"Git","cmd").toString());directories.add(Path.of(programs,"Docker","Docker","resources","bin").toString());}}
        else {directories.add("/usr/local/bin");directories.add("/usr/bin");directories.add("/bin");}
        directories.removeIf(String::isBlank);return String.join(File.pathSeparator,directories);
    }
    public static String executable(String name){
        String file=com.sun.jna.Platform.isWindows()?name+".exe":name;
        for(String directory:searchPath().split(java.util.regex.Pattern.quote(File.pathSeparator))){Path candidate=Path.of(directory,file);if(Files.isRegularFile(candidate)&&Files.isExecutable(candidate))return candidate.toString();}
        throw new IllegalStateException("Install "+name+" and restart the desktop app");
    }
}
