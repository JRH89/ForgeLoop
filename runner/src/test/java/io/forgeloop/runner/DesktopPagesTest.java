package io.forgeloop.runner;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopPagesTest {
    @Test void sidebarRoutesAllPagesAndRetainsAccessibleNamesWhenCollapsed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesktopTheme.install();
            DesktopPages pages = new DesktopPages();
            for (int index = 0; index < 4; index++) pages.addPage(new JPanel());
            DesktopShell shell = new DesktopShell(pages,DesktopLayout.text("Stopped"));
            shell.setSize(640,760);shell.doLayout();
            List<JButton> links = new ArrayList<>();
            findLinks(shell,links);
            assertEquals(4,links.size());
            for (JButton link : links) {
                assertEquals("",link.getText(),"Narrow windows use an icon rail");
                assertFalse(link.getAccessibleContext().getAccessibleName().isBlank());
                assertEquals(link.getAccessibleContext().getAccessibleName(),link.getToolTipText());
                link.doClick();
                int expected = Integer.parseInt(link.getName().substring("navigation-".length()));
                assertEquals(expected,pages.getSelectedIndex());
                assertTrue(pages.getSelectedComponent().isVisible());
            }
            shell.setSize(1040,760);shell.doLayout();
            for (JButton link : links) assertEquals(link.getAccessibleContext().getAccessibleName(),link.getText());
            assertThrows(IllegalArgumentException.class,()->pages.setSelectedIndex(4));
        });
    }

    private static void findLinks(Container parent,List<JButton> result) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && button.getName()!=null && button.getName().startsWith("navigation-")) result.add(button);
            if (child instanceof Container nested) findLinks(nested,result);
        }
    }
}
