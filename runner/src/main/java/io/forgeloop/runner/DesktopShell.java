package io.forgeloop.runner;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Application navigation stays separate from the runner's execution and credential state. */
final class DesktopShell extends JPanel {
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final List<JButton> navigation = new ArrayList<>();
    private final JLabel brand = new JLabel("ForgeLoop");
    private final JLabel version = new JLabel("Runner " + DesktopVersion.current());
    private final String[] titles = {"Connection", "Provider", "Runner", "Activity"};
    private Boolean compactMode;

    DesktopShell(DesktopPages pages, JComponent status) {
        super(new BorderLayout());
        sidebar.setBackground(new Color(0x0F1620));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, DesktopTheme.BORDER),
                BorderFactory.createEmptyBorder(24, 12, 18, 12)));
        JPanel top = DesktopLayout.stack(26);
        top.setOpaque(false);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 18f));
        var logo = DesktopShell.class.getResource("/desktop/favicon.png");
        if (logo != null) brand.setIcon(new ImageIcon(new ImageIcon(logo).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));
        brand.setIconTextGap(9);
        top.add(brand);
        JPanel links = DesktopLayout.stack(7);
        links.setOpaque(false);
        // Preserve the page indices used by pairing and save transitions.
        for (int index : new int[]{2, 0, 1, 3}) {
            JButton link = new JButton(titles[index], new NavigationIcon(index));
            link.setFont(link.getFont().deriveFont(14f));
            link.setName("navigation-" + index);
            link.getAccessibleContext().setAccessibleName(titles[index]);
            link.setToolTipText(titles[index]);
            link.setHorizontalAlignment(SwingConstants.LEFT);
            link.setIconTextGap(10);
            link.setMargin(new Insets(11, 12, 11, 12));
            link.putClientProperty("JButton.buttonType", "borderless");
            link.addActionListener(event -> pages.setSelectedIndex(index));
            navigation.add(link);
            links.add(link);
        }
        top.add(links);
        sidebar.add(top, BorderLayout.NORTH);
        version.setForeground(DesktopTheme.MUTED);
        version.setFont(version.getFont().deriveFont(11f));
        sidebar.add(version, BorderLayout.SOUTH);
        JPanel main = new JPanel(new BorderLayout());
        main.add(pages, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopTheme.BORDER),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));
        footer.add(status);
        main.add(footer, BorderLayout.SOUTH);
        add(sidebar, BorderLayout.WEST);
        add(main, BorderLayout.CENTER);
        pages.addChangeListener(event -> refreshSelection(pages.getSelectedIndex()));
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { resizeNavigation(); }
        });
        refreshSelection(pages.getSelectedIndex());
        resizeNavigation();
    }

    private void refreshSelection(int selected) {
        for (JButton link : navigation) {
            boolean active = link.getName().equals("navigation-" + selected);
            link.setBackground(active ? new Color(0x183A34) : sidebar.getBackground());
            link.setForeground(active ? DesktopTheme.ACCENT : DesktopTheme.MUTED);
            link.putClientProperty("JButton.buttonType", active ? null : "borderless");
        }
    }

    /** At narrow widths retain labelled accessibility and tooltips, with an icon rail visually. */
    private void resizeNavigation() {
        boolean compact = getWidth() > 0 && getWidth() < 820;
        if (compactMode != null && compactMode == compact) return;
        compactMode = compact;
        sidebar.setPreferredSize(new Dimension(compact ? 68 : 186, 0));
        brand.setText(compact ? "" : "ForgeLoop");
        version.setText(compact ? "" : "Runner " + DesktopVersion.current());
        for (JButton link : navigation) {
            int index = Integer.parseInt(link.getName().substring("navigation-".length()));
            link.setText(compact ? "" : titles[index]);
            link.setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
            link.setMargin(new Insets(11, compact ? 5 : 12, 11, compact ? 5 : 12));
        }
        revalidate();
    }

    @Override public void doLayout() {
        // Layout can precede Swing's queued resize event; reflow before measuring the page.
        resizeNavigation();
        super.doLayout();
    }

    /** Small vector icons use the current button colour and remain sharp at HiDPI scale. */
    private record NavigationIcon(int page) implements Icon {
        public int getIconWidth() { return 18; }
        public int getIconHeight() { return 18; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (page) {
                case 0 -> { g.drawRoundRect(1, 4, 7, 10, 4, 4); g.drawRoundRect(10, 4, 7, 10, 4, 4); g.drawLine(6, 9, 12, 9); }
                case 1 -> { g.drawRoundRect(3, 3, 12, 12, 3, 3); for (int p : new int[]{6, 12}) { g.drawLine(p, 0, p, 3); g.drawLine(p, 15, p, 18); g.drawLine(0, p, 3, p); g.drawLine(15, p, 18, p); } }
                case 2 -> { g.drawRoundRect(1, 2, 16, 14, 3, 3); g.drawLine(5, 6, 8, 9); g.drawLine(8, 9, 5, 12); g.drawLine(10, 12, 13, 12); }
                default -> { g.drawLine(1, 9, 4, 9); g.drawLine(4, 9, 7, 3); g.drawLine(7, 3, 11, 15); g.drawLine(11, 15, 14, 9); g.drawLine(14, 9, 17, 9); }
            }
            g.dispose();
        }
    }
}
