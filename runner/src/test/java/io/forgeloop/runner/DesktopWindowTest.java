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
        // A manual test fixture prevents the Provider tab from fetching a live pricing catalog.
        writeManualConfiguration();
        SwingUtilities.invokeAndWait(()->{
            DesktopTheme.install();
            DesktopRunner app=null;
            try{
                app=new DesktopRunner(directory,false);JFrame frame=app.window();assertTrue(frame.isVisible());
                assertEquals("ForgeLoop Runner",frame.getTitle());assertFalse(find(frame,"Start runner").isEnabled());assertFalse(find(frame,"Cancel Docker startup").isEnabled());assertTrue(find(frame,"Connect in browser").isEnabled());
                assertTrue(find(frame,"Check requirements").isShowing());assertTrue(hasText(frame,"Git"));assertTrue(hasText(frame,"Docker Engine"));
                BufferedImage screenshot=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=screenshot.createGraphics();frame.paintAll(graphics);graphics.dispose();
                Path output=Path.of("target","desktop-setup.png");Files.createDirectories(output.getParent());ImageIO.write(screenshot,"png",output.toFile());
                DesktopPages tabs=findTabs(frame);
                assertEquals(4,tabs.getPageCount());
                assertFalse(find(frame,"Reconnect to ForgeLoop").isEnabled());
                for(int index=1;index<tabs.getPageCount();index++){tabs.setSelectedIndex(index);frame.validate();BufferedImage step=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D painter=step.createGraphics();frame.paintAll(painter);painter.dispose();ImageIO.write(step,"png",Path.of("target","desktop-step-"+index+".png").toFile());}
                tabs.setSelectedIndex(2);frame.validate();
                assertFalse(find(frame,"Cancel Docker startup").isShowing());
                assertFalse(Files.exists(directory.resolve("identity")));assertFalse(Files.exists(directory.resolve("provider-key.dpapi")));
            }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
        });
    }
    @Test void allThreeTabsFitNarrowWindowsAndLargeFontsWithoutClipping()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        DesktopFiles.protect(directory);writeManualConfiguration();
        SwingUtilities.invokeAndWait(()->{
            for(int fontSize:new int[]{14,21,28}){
                DesktopTheme.install();UIManager.put("defaultFont",new javax.swing.plaf.FontUIResource(Font.SANS_SERIF,Font.PLAIN,fontSize));
                DesktopRunner app=null;
                try{
                    app=new DesktopRunner(directory,false);JFrame frame=app.window();DesktopPages tabs=findTabs(frame);
                    for(int width:new int[]{640,940}){
                        frame.setSize(width,760);
                        for(int index=0;index<tabs.getPageCount();index++){
                            tabs.setSelectedIndex(index);frame.validate();
                            JScrollPane scroll=(JScrollPane)tabs.getSelectedComponent();
                            // Reflow twice, matching Swing's validate/repaint cycle after the scrollbar appears.
                            layoutRecursively(frame);layoutRecursively(frame);
                            assertEquals(scroll.getViewport().getExtentSize().width,scroll.getViewport().getView().getWidth(),"page must track viewport width");
                            assertInsideViewport(scroll.getViewport().getView(),scroll.getViewport());
                            scroll.getViewport().setViewPosition(new Point());
                            capture(frame,"desktop-layout-"+width+"-font"+fontSize+"-tab"+index+".png");
                        }
                    }
                    assertFalse(Files.exists(directory.resolve("identity")));
                    assertFalse(Files.exists(directory.resolve("provider-policy.json")));
                }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
            }
            DesktopTheme.install();
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
                assertTrue(hasText(frame,"Saved connection — not yet verified this session"));
                assertTrue(hasText(frame,"Saved key configured - leave blank to keep"));
                assertFalse(find(frame,"Reopen approval page").isEnabled());
                assertFalse(find(frame,"Cancel connection").isEnabled());
                assertTrue(find(frame,"Reconnect to ForgeLoop").isEnabled());
                assertFalse(find(frame,"Connect in browser").isEnabled());
                assertTrue(hasText(frame,"custom-model"));
                findTabs(frame).setSelectedIndex(1);frame.validate();
                // A pre-catalog saved rate is treated as an intentional manual override.
                assertTrue(find(frame,"Use automatic prices").isShowing());
                assertTrue(find(frame,"Save provider settings").isShowing());
            }catch(Exception error){throw new AssertionError(error);}finally{if(app!=null)app.disposeIdle();}
        });
        // No key was provided: reopening must not call the OS vault or a provider.
        assertFalse(Files.exists(directory.resolve("anthropic-key.dpapi")));
        assertFalse(Files.exists(directory.resolve("provider-policy.json")));
    }
    private void writeManualConfiguration()throws Exception{new com.fasterxml.jackson.databind.ObjectMapper().writeValue(directory.resolve("config.json").toFile(),new DesktopConfiguration("https://example.com","anthropic","custom-model",new java.math.BigDecimal("3"),new java.math.BigDecimal("12")));}
    private static void capture(JFrame frame,String name)throws Exception{BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();frame.paintAll(graphics);graphics.dispose();Files.createDirectories(Path.of("target"));ImageIO.write(image,"png",Path.of("target",name).toFile());}
    private static void layoutRecursively(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child.isVisible()&&child instanceof Container container)layoutRecursively(container);}
    private static void assertInsideViewport(Component child,JViewport viewport){
        if(!child.isVisible()||child instanceof JScrollBar)return;
        if(child instanceof JComponent component&&!(child instanceof JViewport)&&!(child instanceof JScrollBar)){
            Rectangle bounds=SwingUtilities.convertRectangle(child.getParent(),child.getBounds(),viewport.getView());
            assertTrue(bounds.x>=0&&bounds.x+bounds.width<=viewport.getExtentSize().width,"Clipped horizontally: "+child.getClass().getSimpleName()+" "+bounds);
            if(child instanceof JButton button&&!button.getText().isBlank()){component.scrollRectToVisible(new Rectangle(0,0,child.getWidth(),child.getHeight()));assertTrue(viewport.getViewRect().contains(bounds),"Button must be reachable by vertical scrolling: "+button.getText()+" "+bounds+" view="+viewport.getViewRect());}
            if(child instanceof JTextArea area&&area.getParent().getClass()!=JViewport.class)assertTrue(area.getHeight()>=area.getPreferredSize().height,"Wrapped status must not clip vertically");
        }
        if(child instanceof Container container)for(Component nested:container.getComponents())assertInsideViewport(nested,viewport);
    }
    private static boolean hasText(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JLabel label&&text.equals(label.getText()))return true;if(child instanceof javax.swing.text.JTextComponent field&&text.equals(field.getText()))return true;if(child instanceof Container container&&hasText(container,text))return true;}return false;}
    private static JButton find(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton button&&button.getText().equals(text))return button;if(child instanceof Container container){JButton found=find(container,text);if(found!=null)return found;}}return null;}
    private static DesktopPages findTabs(Container parent){for(Component child:parent.getComponents()){if(child instanceof DesktopPages tabs)return tabs;if(child instanceof Container container){DesktopPages found=findTabs(container);if(found!=null)return found;}}return null;}
}
