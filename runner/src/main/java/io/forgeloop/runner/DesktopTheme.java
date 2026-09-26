package io.forgeloop.runner;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import javax.swing.*;

/** Shared desktop tokens: native keyboard behavior with a consistent, HiDPI-aware UI. */
final class DesktopTheme {
    private DesktopTheme() {}
    static void install() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Panel.background", new Color(0x101D2B));
        for(String component:new String[]{"TextField","PasswordField","ComboBox","TabbedPane"})UIManager.put(component+".background",new Color(0x172A3D));
        UIManager.put("Button.background",new Color(0x233B50));
        UIManager.put("Button.disabledBackground",new Color(0x172A3D));
        UIManager.put("TabbedPane.selectedBackground",new Color(0x1B3C42));
        UIManager.put("TabbedPane.focusColor",new Color(0x37D9AF));
        UIManager.put("Component.borderColor",new Color(0x345269));
        UIManager.put("TextArea.background", new Color(0x09131E));
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusColor", new Color(0x37D9AF));
        UIManager.put("TabbedPane.underlineColor", new Color(0x37D9AF));
        UIManager.put("Button.margin", new Insets(10, 18, 10, 18));
        UIManager.put("TextField.margin", new Insets(9, 10, 9, 10));
        UIManager.put("PasswordField.margin", new Insets(9, 10, 9, 10));
    }
    static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 26f));
        return label;
    }
    static void primary(JButton button) {
        button.setBackground(new Color(0x37D9AF));
        button.setForeground(new Color(0x06231E));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
    }
}
