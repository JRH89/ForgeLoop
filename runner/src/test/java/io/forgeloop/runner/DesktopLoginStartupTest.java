package io.forgeloop.runner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DesktopLoginStartupTest {
    @Test void escapesNativeLaunchPaths(){assertTrue(DesktopLoginStartup.content("windows","C:\\Apps\\ForgeLoop Runner.exe").contains("--autostart"));assertTrue(DesktopLoginStartup.content("mac","/Applications/A&B.app/runner").contains("A&amp;B"));assertTrue(DesktopLoginStartup.content("linux","/opt/100% runner").contains("100%% runner"));}
    @Test void refusesMultilineCommandInjection(){assertThrows(IllegalArgumentException.class,()->DesktopLoginStartup.content("linux","/app\nExec=bad"));}
}
