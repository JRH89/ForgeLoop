package io.forgeloop.runner;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import javax.swing.*;

/** Shared desktop tokens: native keyboard behavior with a consistent, HiDPI-aware UI. */
final class DesktopTheme {
    static final Color BACKGROUND = new Color(0x0B1017);
    static final Color SURFACE = new Color(0x111923);
    static final Color BORDER = new Color(0x273342);
    static final Color MUTED = new Color(0x94A3B5);
    static final Color ACCENT = new Color(0x37D9AF);
    private DesktopTheme() {}
    static void install() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", new Color(0xE8EDF4));
        for(String component:new String[]{"TextField","PasswordField","ComboBox","TabbedPane"})UIManager.put(component+".background",SURFACE);
        UIManager.put("Button.background",new Color(0x1B2735));
        UIManager.put("Button.disabledBackground",SURFACE);
        UIManager.put("Button.disabledText",new Color(0x677588));
        UIManager.put("Component.borderColor",BORDER);
        UIManager.put("TextArea.background", BACKGROUND);
        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Button.margin", new Insets(8, 14, 8, 14));
        UIManager.put("TextField.margin", new Insets(8, 10, 8, 10));
        UIManager.put("PasswordField.margin", new Insets(8, 10, 8, 10));
    }
    static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 26f));
        return label;
    }
    static void primary(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(new Color(0x06231E));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
    }
}
