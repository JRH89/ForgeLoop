package io.forgeloop.runner;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopWindowTest {
    @TempDir Path directory;
    @Test void opensSetupWithoutNetworkOrPaidWorkAndCapturesLayout()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        DesktopFiles.protect(directory);
        SwingUtilities.invokeAndWait(()->{
            DesktopRunner app=null;
            try{
                app=new DesktopRunner(directory,false);JFrame frame=app.window();assertTrue(frame.isVisible());
                assertEquals("ForgeLoop Runner",frame.getTitle());assertFalse(find(frame,"Start runner").isEnabled());assertTrue(find(frame,"Connect in browser").isEnabled());
                BufferedImage screenshot=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=screenshot.createGraphics();frame.paintAll(graphics);graphics.dispose();
                Path output=Path.of("target","desktop-setup.png");Files.createDirectories(output.getParent());ImageIO.write(screenshot,"png",output.toFile());
                assertFalse(Files.exists(directory.resolve("identity")));assertFalse(Files.exists(directory.resolve("provider-key.dpapi")));
            }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
        });
    }
    private static JButton find(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton button&&button.getText().equals(text))return button;if(child instanceof Container container){JButton found=find(container,text);if(found!=null)return found;}}return null;}
}
