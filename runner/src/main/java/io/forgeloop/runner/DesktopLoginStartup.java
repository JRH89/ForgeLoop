package io.forgeloop.runner;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** User-scoped login hooks; never installs a privileged service or starts work during setup. */
public final class DesktopLoginStartup {
    private static final String MARKER="ForgeLoop-managed-login";
    private DesktopLoginStartup(){}
    public static void configure(boolean enabled)throws Exception{
        String launcher=System.getProperty("jpackage.app-path");
        if(launcher==null||launcher.isBlank())throw new IllegalStateException("Login startup requires the installed native app, not a development JAR");
        Path home=Path.of(System.getProperty("user.home"));String os=System.getProperty("os.name").toLowerCase();
        Path target;
        if(os.contains("win"))target=Path.of(System.getenv("APPDATA"),"Microsoft","Windows","Start Menu","Programs","Startup","ForgeLoop Runner.vbs");
        else if(os.contains("mac"))target=home.resolve("Library/LaunchAgents/io.forgeloop.runner.plist");
        else target=home.resolve(".config/autostart/forgeloop-runner.desktop");
        if(Files.exists(target)&&!Files.readString(target).contains(MARKER))throw new IllegalStateException("A custom login entry already exists; review it before replacing");
        if(!enabled){Files.deleteIfExists(target);return;}
        Files.createDirectories(target.getParent());Files.writeString(target,content(os,launcher),StandardCharsets.UTF_8);
    }
    static String content(String os,String launcher){
        if(launcher.contains("\n")||launcher.contains("\r")||launcher.contains("\0"))throw new IllegalArgumentException("Invalid launcher path");
        if(os.contains("win"))return "' "+MARKER+"\r\nCreateObject(\"WScript.Shell\").Run \"\"\""+launcher.replace("\"","\"\"")+"\"\" --autostart\", 1, False\r\n";
        if(os.contains("mac"))return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- "+MARKER+" -->\n<plist version=\"1.0\"><dict><key>Label</key><string>io.forgeloop.runner</string><key>ProgramArguments</key><array><string>"+xml(launcher)+"</string><string>--autostart</string></array><key>RunAtLoad</key><true/></dict></plist>\n";
        String escaped=launcher.replace("\\","\\\\").replace("\"","\\\"").replace("$","\\$").replace("`","\\`").replace("%","%%");
        return "[Desktop Entry]\n# "+MARKER+"\nType=Application\nName=ForgeLoop Runner\nExec=\""+escaped+"\" --autostart\nTerminal=false\n";
    }
    private static String xml(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
