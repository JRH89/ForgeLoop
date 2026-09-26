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
            DesktopTheme.install();
            DesktopRunner app=null;
            try{
                app=new DesktopRunner(directory,false);JFrame frame=app.window();assertTrue(frame.isVisible());
                assertEquals("ForgeLoop Runner",frame.getTitle());assertFalse(find(frame,"Start runner").isEnabled());assertTrue(find(frame,"Connect in browser").isEnabled());
                BufferedImage screenshot=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=screenshot.createGraphics();frame.paintAll(graphics);graphics.dispose();
                Path output=Path.of("target","desktop-setup.png");Files.createDirectories(output.getParent());ImageIO.write(screenshot,"png",output.toFile());
                JTabbedPane tabs=findTabs(frame);
                for(int index=1;index<3;index++){tabs.setSelectedIndex(index);frame.validate();BufferedImage step=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D painter=step.createGraphics();frame.paintAll(painter);painter.dispose();ImageIO.write(step,"png",Path.of("target","desktop-step-"+index+".png").toFile());}
                assertFalse(Files.exists(directory.resolve("identity")));assertFalse(Files.exists(directory.resolve("provider-key.dpapi")));
            }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
        });
    }
    @Test void reopensSavedSetupWithoutNetworkDecryptingKeysOrStartingWork()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        DesktopFiles.protect(directory);
        new RunnerIdentityStore().save(directory.resolve("identity"),new RunnerIdentity("test-runner","fake-credential"));
        Files.writeString(directory.resolve("runner-name"),"Windows desktop test");
        Files.writeString(directory.resolve("endpoint"),"https://example.com");
        var config=new DesktopConfiguration("https://example.com","anthropic","custom-model",new java.math.BigDecimal("3"),new java.math.BigDecimal("12"));
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(directory.resolve("config.json").toFile(),config);
        SwingUtilities.invokeAndWait(()->{
            DesktopTheme.install();DesktopRunner app=null;
            try{
                app=new DesktopRunner(directory,false);var frame=app.window();
                assertEquals(2,findTabs(frame).getSelectedIndex());
                assertTrue(hasText(frame,"Windows desktop test"));
                assertTrue(hasText(frame,"Connected - saved on this computer"));
                assertTrue(hasText(frame,"Saved key configured - leave blank to keep"));
                assertFalse(find(frame,"Reopen approval page").isEnabled());
                assertFalse(find(frame,"Cancel connection").isEnabled());
                assertTrue(hasText(frame,"custom-model"));
            }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
        });
        // No key was provided: reopening must not call the OS vault or a provider.
        assertFalse(Files.exists(directory.resolve("anthropic-key.dpapi")));
        assertFalse(Files.exists(directory.resolve("provider-policy.json")));
    }
    private static boolean hasText(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JLabel label&&text.equals(label.getText()))return true;if(child instanceof JTextField field&&text.equals(field.getText()))return true;if(child instanceof Container container&&hasText(container,text))return true;}return false;}
    private static JButton find(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton button&&button.getText().equals(text))return button;if(child instanceof Container container){JButton found=find(container,text);if(found!=null)return found;}}return null;}
    private static JTabbedPane findTabs(Container parent){for(Component child:parent.getComponents()){if(child instanceof JTabbedPane tabs)return tabs;if(child instanceof Container container){JTabbedPane found=findTabs(container);if(found!=null)return found;}}return null;}
}
