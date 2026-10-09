package io.forgeloop.runner;

import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** A single page model drives sidebar navigation and asynchronous setup transitions. */
final class DesktopPages extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final List<JComponent> pages = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private int selected;

    DesktopPages() { setLayout(cards); }
    void addPage(JComponent page) {
        add(page, Integer.toString(pages.size()));
        pages.add(page);
    }
    int getPageCount() { return pages.size(); }
    int getSelectedIndex() { return selected; }
    JComponent getSelectedComponent() { return pages.get(selected); }
    void setSelectedIndex(int index) {
        if (index < 0 || index >= pages.size()) throw new IllegalArgumentException("Unknown desktop page");
        selected = index;
        cards.show(this, Integer.toString(index));
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) listener.stateChanged(event);
    }
    void addChangeListener(ChangeListener listener) { listeners.add(listener); }
}
