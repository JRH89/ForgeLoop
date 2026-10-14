package io.forgeloop.runner;

import java.nio.file.Files;
import java.math.BigDecimal;
import javax.swing.SwingUtilities;

/** Review-only launcher: isolated data, no saved identity/key, and no pricing or worker calls. */
public final class DesktopPreview {
    public static void main(String[] args)throws Exception {
        var directory=Files.createTempDirectory("forgeloop-ui-preview-");
        DesktopFiles.protect(directory);
        var config=new DesktopConfiguration("https://forgeloop.codefrontlabs.com","anthropic","claude-sonnet-5",new BigDecimal("3"),new BigDecimal("15"));
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(directory.resolve("config.json").toFile(),config);
        DesktopTheme.install();
        SwingUtilities.invokeLater(()->{
            try {
                var app=new DesktopRunner(directory,false);
                app.window().setTitle("ForgeLoop Runner — Local design preview");
                findPages(app.window()).setSelectedIndex(2);
                app.designPreview();
            }catch(Exception error){throw new IllegalStateException(error);}
        });
    }
    private static DesktopPages findPages(java.awt.Container parent) {
        for(var component:parent.getComponents()) {
            if(component instanceof DesktopPages pages)return pages;
            if(component instanceof java.awt.Container nested){var pages=findPages(nested);if(pages!=null)return pages;}
        }
        return null;
    }
}
