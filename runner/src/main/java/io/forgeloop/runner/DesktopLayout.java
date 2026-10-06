package io.forgeloop.runner;

import java.awt.*;
import javax.swing.*;

/** Width-aware desktop forms: only the page scrolls, never an oversized hidden column. */
final class DesktopLayout {
    private DesktopLayout() {}

    static JPanel stack(int gap) { return new JPanel(new StackLayout(gap)); }

    /** Plain text wraps to its allocated width, including after asynchronous status updates. */
    static JTextArea text(String value) {
        JTextArea text = new JTextArea(value) {
            @Override public Dimension getPreferredSize() {
                // Swing's text view needs a width before it can measure wrapped lines.
                if (getWidth() == 0) setSize(400, Short.MAX_VALUE);
                return new Dimension(1, super.getPreferredSize().height);
            }
        };
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(UIManager.getFont("Label.font"));
        text.setForeground(UIManager.getColor("Label.foreground"));
        text.setBorder(BorderFactory.createEmptyBorder());
        return text;
    }

    static JPanel field(String label, JComponent control, String help) {
        JPanel field = stack(7);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(control);
        control.getAccessibleContext().setAccessibleName(label);
        field.add(caption);
        field.add(control);
        if (help != null && !help.isBlank()) field.add(text(help));
        return field;
    }

    static JPanel section(String title, String subtitle, JComponent body) {
        JPanel card = stack(14);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x345269)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        JTextArea heading = text(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 2));
        card.add(heading);
        if (subtitle != null && !subtitle.isBlank()) card.add(text(subtitle));
        card.add(body);
        return card;
    }

    /** Natural-width buttons wrap as a group rather than stretching into equal-width columns. */
    static JPanel actions(JComponent... controls) {
        JPanel row = new JPanel(new ActionLayout(10));
        for (JComponent control : controls) row.add(control);
        return row;
    }

    static JScrollPane scroll(JComponent content) {
        JPanel page = new ScrollablePage();
        page.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        page.add(content);
        JScrollPane scroll = new JScrollPane(page, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setPreferredSize(new Dimension(820, 510));
        return scroll;
    }

    private static final class ScrollablePage extends JPanel implements Scrollable {
        ScrollablePage() { super(new StackLayout(0)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(820, 510); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(20, visible.height - 20); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Measure children at the actual viewport width before assigning their natural height. */
    private static final class StackLayout implements LayoutManager {
        private final int gap;
        StackLayout(int gap) { this.gap = gap; }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(1, (parent.getWidth() > 0 ? parent.getWidth() : 800) - insets.left - insets.right);
            int height = insets.top + insets.bottom;
            int count = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                child.setSize(width, child.getHeight());
                height += child.getPreferredSize().height;
                count++;
            }
            return new Dimension(width + insets.left + insets.right, height + Math.max(0, count - 1) * gap);
        }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(1, parent.getWidth() - insets.left - insets.right);
            int y = insets.top;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                child.setSize(width, child.getHeight());
                int height = child.getPreferredSize().height;
                child.setBounds(insets.left, y, width, height);
                if (child instanceof Container container) container.doLayout();
                y += height + gap;
            }
        }
    }

    private static final class ActionLayout implements LayoutManager {
        private final int gap;
        ActionLayout(int gap) { this.gap = gap; }
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public Dimension preferredLayoutSize(Container parent) { return arrange(parent, false); }
        @Override public void layoutContainer(Container parent) { arrange(parent, true); }
        private Dimension arrange(Container parent, boolean apply) {
            Insets insets = parent.getInsets();
            int available = Math.max(1, (parent.getWidth() > 0 ? parent.getWidth() : 800) - insets.left - insets.right);
            int x = 0, y = 0, rowHeight = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                Dimension preferred = child.getPreferredSize();
                int width = Math.min(available, preferred.width);
                if (x > 0 && x + width > available) { x = 0; y += rowHeight + gap; rowHeight = 0; }
                if (apply) child.setBounds(insets.left + x, insets.top + y, width, preferred.height);
                x += width + gap;
                rowHeight = Math.max(rowHeight, preferred.height);
            }
            return new Dimension(available + insets.left + insets.right, insets.top + insets.bottom + y + rowHeight);
        }
    }
}
