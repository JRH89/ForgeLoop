package io.forgeloop.runner;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Headless-safe layout acceptance: synthetic tabs contain no runner, credentials, or network actions. */
class DesktopLayoutTest {
    private static final String LONG_REQUIREMENT = "Docker is installed but not ready. Start runner can try to start your local Docker engine. "
            + "Complete Docker Desktop first-run dialogs and confirm that your account has permission to use Linux containers. "
            + "If your selected engine is remote, start that engine on its host and try again.";
    private static final String LONG_PRICE = "Automatic estimate: $3.00 input and $15.00 output per million tokens, checked against the public model catalog. "
            + "Account-specific rates may differ, and cached tokens or additional provider features can change the final bill. "
            + "The app shows an estimate so you can choose your model before starting work.";

    @Test void allThreeTabsFitAtNarrowAndWideSizesWithNormalAndScaledFonts() throws Exception {
        onEdt(() -> {
            for (int fontSize : List.of(14, 21, 28)) {
                for (int width : List.of(640, 900)) {
                    DesktopTheme.install();
                    UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
                    JPanel shell = new JPanel(new java.awt.BorderLayout());
                    JTabbedPane tabs = new JTabbedPane();
                    tabs.addTab("1. Connect", DesktopLayout.scroll(connection()));
                    tabs.addTab("2. Provider", DesktopLayout.scroll(provider()));
                    tabs.addTab("3. Run", DesktopLayout.scroll(run()));
                    shell.add(tabs);
                    shell.setSize(width, 700);

                    for (int index = 0; index < tabs.getTabCount(); index++) {
                        tabs.setSelectedIndex(index);
                        settle(shell);
                        JScrollPane scroll = (JScrollPane) tabs.getComponentAt(index);
                        for (JComponent component : descendants((JComponent) scroll.getViewport().getView())) {
                            if (component instanceof JLabel label && label.getLabelFor() != null) {
                                assertEquals(fontSize, label.getFont().getSize(), "The fixture must exercise the requested font scaling");
                            }
                        }
                        assertContentFits(scroll, "width=" + width + ", font=" + fontSize + ", tab=" + index);
                        capture(shell, "desktop-layout-" + width + "-font-" + fontSize + "-tab-" + index + ".png");
                    }
                }
            }
        });
    }

    @Test void longerDynamicPrerequisiteTextWrapsAndMovesLaterActionsDown() throws Exception {
        onEdt(() -> {
            DesktopTheme.install();
            JTextArea status = DesktopLayout.text("Not checked");
            JButton connect = new JButton("Connect in browser");
            JPanel content = DesktopLayout.stack(12);
            content.add(DesktopLayout.section("Requirements", "Check your local tools before connecting.", status));
            content.add(DesktopLayout.actions(connect));
            JScrollPane scroll = DesktopLayout.scroll(content);
            scroll.setSize(640, 360);
            settle(scroll);
            int previousHeight = status.getHeight();
            int previousActionTop = inView(connect, scroll).y;

            status.setText(LONG_REQUIREMENT.repeat(3));
            status.revalidate();
            settle(scroll);

            assertTrue(status.getHeight() > previousHeight, "Long prerequisite guidance must gain wrapped height");
            assertTrue(inView(connect, scroll).y > previousActionTop, "Following actions must move down rather than overlap text");
            assertContentFits(scroll, "dynamic prerequisite report");
        });
    }

    @Test void resizedContentReflowsWithoutNeedingAnEntireNewWindow() throws Exception {
        onEdt(() -> {
            DesktopTheme.install();
            UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            JScrollPane scroll = DesktopLayout.scroll(provider());
            scroll.setSize(900, 600);
            settle(scroll);
            assertContentFits(scroll, "wide provider before resize");
            int wideHeight = scroll.getViewport().getView().getPreferredSize().height;

            scroll.setSize(640, 600);
            settle(scroll);

            assertContentFits(scroll, "narrow provider after resize");
            assertTrue(scroll.getViewport().getView().getPreferredSize().height >= wideHeight,
                    "Narrowing the form must increase natural height or preserve it");
        });
    }

    @Test void wrappedHelpIsReadOnlyTransparentAndPreservesPlainText() throws Exception {
        onEdt(() -> {
            DesktopTheme.install();
            String value = "Model <custom-id> costs $3 & $15 per million tokens.";
            JTextArea help = DesktopLayout.text(value);
            assertEquals(value, help.getText());
            assertFalse(help.isEditable());
            assertFalse(help.isOpaque());
            assertTrue(help.getLineWrap());
            assertTrue(help.getWrapStyleWord());
        });
    }

    private static JPanel connection() {
        JPanel requirements = DesktopLayout.stack(10);
        requirements.add(DesktopLayout.field("Git", DesktopLayout.text("Git is ready. Installed version 2.46.0 is available to this account."), ""));
        requirements.add(DesktopLayout.field("Docker Engine", DesktopLayout.text(LONG_REQUIREMENT), ""));
        requirements.add(DesktopLayout.actions(new JButton("Install Git"), new JButton("Install Docker"), new JButton("Check requirements")));
        JPanel fields = DesktopLayout.stack(12);
        fields.add(DesktopLayout.field("ForgeLoop address", new JTextField("https://forgeloop.example/" + "long-address-".repeat(10)),
                "Use the address of your ForgeLoop account. Browser approval connects this computer without an enrollment token to copy."));
        fields.add(DesktopLayout.field("Runner name", new JTextField("My runner on the Windows desktop"), "A recognizable name helps you identify this machine."));
        fields.add(DesktopLayout.text("Fingerprint: 154f4500c331. Match this value with the browser approval page before approving this runner."));
        fields.add(DesktopLayout.actions(new JButton("Connect in browser"), new JButton("Reopen approval page"), new JButton("Cancel connection")));
        fields.add(DesktopLayout.actions(new JButton("Check saved connection")));
        JPanel body = DesktopLayout.stack(18);
        body.add(DesktopLayout.section("Before connecting", "Git and Docker with Linux containers are required.", requirements));
        body.add(DesktopLayout.section("Connect your runner", "Approve the request in your browser on a computer you trust.", fields));
        return body;
    }

    private static JPanel provider() {
        JPanel fields = DesktopLayout.stack(12);
        fields.add(DesktopLayout.field("Provider", new JComboBox<>(new String[]{"Anthropic", "OpenAI", "Gemini"}), "Your API key stays on this computer."));
        JComboBox<String> model = new JComboBox<>(new String[]{"a-long-custom-model-identifier-that-must-not-expand-the-entire-form"});
        model.setEditable(true);
        fields.add(DesktopLayout.field("Model (choose or enter an ID)", model, "Choose a model or enter the provider's model ID."));
        fields.add(DesktopLayout.field("API key", new JPasswordField(), "Leave this field blank to keep your saved key. Enter a key only when you want to replace it."));
        fields.add(DesktopLayout.text(LONG_PRICE));
        fields.add(DesktopLayout.field("Input USD / million tokens", new JTextField("3.00"), "An optional manual override for your account-specific input rate."));
        fields.add(DesktopLayout.field("Output USD / million tokens", new JTextField("15.00"), "An optional manual override for your account-specific output rate."));
        fields.add(DesktopLayout.actions(new JButton("Use automatic prices"), new JButton("Check saved key locally")));
        fields.add(new JCheckBox("Start work at sign-in"));
        fields.add(DesktopLayout.text("Starting work at sign-in can incur API charges. You can pause the runner after its current work finishes."));
        fields.add(DesktopLayout.actions(new JButton("Save provider settings")));
        return DesktopLayout.section("Provider settings", "Choose your model and review its estimated costs before starting work.", fields);
    }

    private static JPanel run() {
        JPanel tools = DesktopLayout.stack(12);
        tools.add(DesktopLayout.actions(new JButton("Check Git and Docker")));
        tools.add(DesktopLayout.text("Git and Docker are required. Java is bundled, and checking tools does not call a model."));
        tools.add(DesktopLayout.actions(new JButton("Start runner"), new JButton("Pause after current work"), new JButton("Cancel Docker startup")));
        tools.add(DesktopLayout.text(LONG_REQUIREMENT));
        JPanel maintenance = DesktopLayout.stack(12);
        maintenance.add(DesktopLayout.actions(new JButton("Check for updates"), new JButton("Export safe diagnostics")));
        maintenance.add(DesktopLayout.text("Diagnostics do not include API keys, credentials, repository names, or raw task logs. "
                + "Update checks show your installed version and download checksum without launching an installer."));
        JPanel body = DesktopLayout.stack(18);
        body.add(DesktopLayout.section("Run", "You control when the runner starts processing eligible issues.", tools));
        body.add(DesktopLayout.section("Maintenance", "Check the installed package or export a safe diagnostic summary.", maintenance));
        JTextArea log = DesktopLayout.text("Saved settings restored.\nExisting connection restored.\nWaiting for Docker with Linux containers.\n".repeat(8));
        body.add(DesktopLayout.section("Activity", "Recent activity on this computer.", log));
        return body;
    }

    private static void assertContentFits(JScrollPane scroll, String context) {
        JComponent view = (JComponent) scroll.getViewport().getView();
        int width = scroll.getViewport().getExtentSize().width;
        assertTrue(width > 0, context + ": viewport must have usable width");
        assertEquals(width, view.getWidth(), context + ": scroll content must track viewport width");
        assertFalse(scroll.getHorizontalScrollBar().isVisible(), context + ": horizontal scrolling must not be required");
        List<JButton> buttons = new ArrayList<>();
        for (JComponent component : descendants(view)) {
            if (!component.isVisible() || component.getWidth() == 0) continue;
            Rectangle bounds = inView(component, scroll);
            assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= width,
                    context + ": " + description(component) + " overflows horizontally: " + bounds + ", viewport width=" + width);
            if (component instanceof JLabel label && label.getLabelFor() != null) {
                assertTrue(label.getPreferredSize().width <= label.getWidth(),
                        context + ": complete field caption must remain readable: " + label.getText());
            }
            if (component instanceof JButton button && !button.getText().isBlank()) {
                assertTrue(button.getPreferredSize().width <= button.getWidth(),
                        context + ": button text must not be clipped: " + button.getText());
            }
            if (component instanceof JButton button) buttons.add(button);
        }
        assertFalse(buttons.isEmpty(), context + ": fixture must contain actual actions");
        for (JButton button : buttons) {
            Rectangle bounds = inView(button, scroll);
            view.scrollRectToVisible(bounds);
            assertTrue(scroll.getViewport().getViewRect().contains(bounds),
                    context + ": full action must be reachable by vertical scrolling: " + button.getText());
        }
        scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
    }

    private static Rectangle inView(JComponent component, JScrollPane scroll) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), scroll.getViewport().getView());
    }

    private static List<JComponent> descendants(Container parent) {
        var components = new ArrayList<JComponent>();
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component) components.add(component);
            if (child instanceof Container container) components.addAll(descendants(container));
        }
        return components;
    }

    private static String description(JComponent component) {
        if (component instanceof JButton button) return "button " + button.getText();
        if (component instanceof JLabel label) return "label " + label.getText();
        return component.getClass().getSimpleName();
    }

    /** Several passes let text wrapping update its preferred height after the viewport receives its width. */
    private static void settle(Container root) {
        for (int index = 0; index < 8; index++) layoutTree(root);
    }

    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) layoutTree(container);
        }
    }

    private static void capture(JPanel shell, String name) throws Exception {
        BufferedImage screenshot = new BufferedImage(shell.getWidth(), shell.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = screenshot.createGraphics();
        shell.printAll(painter);
        painter.dispose();
        Path output = Path.of("target", name);
        Files.createDirectories(output.getParent());
        ImageIO.write(screenshot, "png", output.toFile());
    }

    private static void onEdt(CheckedWork work) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { work.run(); } catch (Throwable error) { failure.set(error); }
            finally { DesktopTheme.install(); }
        });
        if (failure.get() instanceof Exception error) throw error;
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @FunctionalInterface private interface CheckedWork { void run() throws Exception; }
}
