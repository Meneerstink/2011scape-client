import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import java.awt.*;
import java.awt.Component;
import java.awt.Cursor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Compact template/wipe controls. All UI entry points belong to the EDT. */
final class RspsScriptTools extends JPanel {
    private final JComboBox<String> template = combo(templateNames());
    private final JButton apply = button("Apply");
    private final JButton newScript = button("+ Script");
    private final JLabel used = label("");
    private final JButton wipe = button("Wipe Script 1");
    private final Timer expiry = new Timer(3000, e -> cancelConfirmation());
    private boolean refreshing;
    private int armedScript, armedTemplate = -1;
    private String armedAction = "";
    private long deadline;
    private List<RspsScripts.Step> expected;

    RspsScriptTools() {
        column(this);
        // v5 live test: two rows - Template + Apply, then "N of 15 used" + "+ Script" (one row was too cramped at 200 px).
        JPanel row = row();
        row.add(template, BorderLayout.CENTER);
        apply.setMargin(new Insets(1, 1, 1, 1));
        apply.setPreferredSize(new Dimension(52, 22));
        row.add(apply, BorderLayout.EAST);
        JPanel second = row();
        used.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        second.add(used, BorderLayout.CENTER);
        newScript.setMargin(new Insets(1, 1, 1, 1));
        newScript.setPreferredSize(new Dimension(70, 22));
        second.add(newScript, BorderLayout.EAST);
        newScript.setName("scripts.newScript");
        newScript.setToolTipText("Reveal the first hidden empty script and make it the Add to script.");
        newScript.addActionListener(e -> {
            if (refreshing) return;
            int n = firstHiddenEmpty();
            if (n == 0) return;
            clearConfirmation();
            RspsScriptBox.reveal(n);
            RspsSidebar.set("pkhotkeys.addTo", Integer.toString(n));
            refreshViews();
            refresh();
        });
        template.setName("scripts.template");
        apply.setToolTipText(tooltip("Replace the selected script with this template. Click twice within 3 seconds if it has steps."));
        template.addActionListener(e -> { if (!refreshing) cancelConfirmation(); });
        apply.addActionListener(e -> change("apply"));
        wipe.setName("scripts.wipe");
        wipe.setToolTipText(tooltip("Remove all steps from the Add to script. Click twice within 3 seconds."));
        wipe.addActionListener(e -> change("wipe"));
        add(row);
        add(Box.createVerticalStrut(3));
        add(second);
        add(Box.createVerticalStrut(3));
        add(wipe);
        expiry.setRepeats(false);
        watch(this, false);
        refresh();
    }

    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        refreshing = true;
        try {
            int n = selectedScript();
            int count = 0;
            for (int i = 1; i <= RspsScripts.COUNT; i++) if (!RspsScripts.steps(i).isEmpty()) count++;
            used.setText(count + " of " + RspsScripts.COUNT + " used");
            used.setToolTipText(used.getText());
            newScript.setEnabled(firstHiddenEmpty() != 0);
            if (!armedAction.isEmpty() && (n != armedScript || template.getSelectedIndex() != armedTemplate
                || System.nanoTime() >= deadline || !RspsScripts.steps(n).equals(expected))) clearConfirmation();
            apply.setEnabled(n > 0 && template.getSelectedIndex() >= 0);
            wipe.setEnabled(n > 0);
            apply.setText("apply".equals(armedAction) ? "Sure?" : "Apply");
            wipe.setText("wipe".equals(armedAction) ? "Sure?" : n > 0 ? "Wipe Script " + n : "Wipe Script");
            wipe.setBackground(RspsSidebar.accent());
            wipe.setOpaque(true);
            Color accent = wipe.getBackground();
            wipe.setForeground(0.299 * accent.getRed() + 0.587 * accent.getGreen() + 0.114 * accent.getBlue() > 145 ? Color.BLACK : Color.WHITE);
            String destination = n > 0 ? "Script " + n + ": " + RspsScripts.name(n) : "Choose a valid Add to script first.";
            template.setToolTipText(tooltip(destination + ". " + String.valueOf(template.getSelectedItem())));
        } finally { refreshing = false; }
        fit(this);
    }

    private void change(String action) {
        if (refreshing) return;
        int n = selectedScript(), t = template.getSelectedIndex();
        if (n < 1 || ("apply".equals(action) && t < 0)) return;
        // The model serializes step mutations on this lock: check and replace together.
        synchronized (RspsScripts.class) {
            List<RspsScripts.Step> current = RspsScripts.steps(n);
            boolean confirmed = action.equals(armedAction) && n == armedScript && t == armedTemplate
                && System.nanoTime() < deadline && current.equals(expected);
            if (("wipe".equals(action) || !current.isEmpty()) && !confirmed) {
                armedScript = n;
                armedTemplate = t;
                armedAction = action;
                expected = current;
                deadline = System.nanoTime() + 3000000000L;
                expiry.restart();
            } else {
                clearConfirmation();
                if ("wipe".equals(action)) RspsScripts.wipe(n);
                else RspsScripts.applyTemplate(n, t);
            }
        }
        refresh();
    }

    private static int firstHiddenEmpty() {
        for (int n = 1; n <= RspsScripts.COUNT; n++) if (RspsScriptBox.hiddenEmpty(n)) return n;
        return 0;
    }

    private static int selectedScript() {
        try {
            int n = Integer.parseInt(RspsHudConfig.get("pkhotkeys.addTo"));
            return n >= 1 && n <= RspsScripts.COUNT ? n : 0;
        } catch (NumberFormatException invalid) { return 0; }
    }

    private void clearConfirmation() {
        expiry.stop();
        armedAction = "";
        expected = null;
    }

    private void cancelConfirmation() { clearConfirmation(); refresh(); }
    @Override public void removeNotify() { clearConfirmation(); super.removeNotify(); }
    @Override public Dimension getPreferredSize() { return new Dimension(200, super.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }

    private static String[] templateNames() {
        String[] out = new String[RspsScripts.TEMPLATES.length];
        for (int i = 0; i < out.length; i++) out[i] = RspsScripts.TEMPLATES[i][0];
        return out;
    }

    // One listener per model, shared by these three components; no model retains a panel.
    private static final List<WeakReference<JPanel>> VIEWS = new ArrayList<WeakReference<JPanel>>();
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static final AtomicLong PROFILE_REVISION = new AtomicLong();
    private static boolean scriptsListening, profilesListening;

    static void watch(JPanel panel, boolean profiles) {
        Iterator<WeakReference<JPanel>> it = VIEWS.iterator();
        while (it.hasNext()) if (it.next().get() == null) it.remove();
        VIEWS.add(new WeakReference<JPanel>(panel));
        if (!scriptsListening) {
            scriptsListening = true;
            RspsScripts.addListener(RspsScriptTools::queueRefresh);
            RspsGearSets.addListener(RspsScriptTools::queueRefresh);
        }
        if (profiles && !profilesListening) {
            profilesListening = true;
            RspsProfiles.addListener(() -> { PROFILE_REVISION.incrementAndGet(); queueRefresh(); });
        }
    }

    static long profileRevision() { return PROFILE_REVISION.get(); }
    static void refreshViews() { queueRefresh(); }

    private static void queueRefresh() {
        if (QUEUED.getAndSet(true)) return;
        SwingUtilities.invokeLater(() -> {
            QUEUED.set(false);
            Iterator<WeakReference<JPanel>> it = VIEWS.iterator();
            while (it.hasNext()) {
                JPanel panel = it.next().get();
                if (panel == null) { it.remove(); continue; }
                if (panel instanceof RspsScriptBox) ((RspsScriptBox) panel).refresh();
                else if (panel instanceof RspsScriptTools) ((RspsScriptTools) panel).refresh();
                else if (panel instanceof RspsProfilesPanel) {
                    RspsProfilesPanel profiles = (RspsProfilesPanel) panel;
                    profiles.refresh();
                }
            }
        });
    }

    static void column(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    static JPanel row() {
        JPanel panel = new JPanel(new BorderLayout(3, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMinimumSize(new Dimension(0, 22));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return panel;
    }
    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.WHITE);
        label.setMinimumSize(new Dimension(0, 16));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    static JButton button(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(Color.WHITE);
        button.setMargin(new Insets(1, 3, 1, 3));
        button.setFocusPainted(false);
        button.setMinimumSize(new Dimension(0, 22));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }
    static JTextField field() {
        JTextField field = new JTextField();
        field.setFont(FontManager.getRunescapeSmallFont());
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setMinimumSize(new Dimension(0, 22));
        field.setPreferredSize(new Dimension(60, 22));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }
    static JComboBox<String> combo(String[] values) {
        JComboBox<String> combo = new JComboBox<String>(values);
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        combo.setForeground(Color.WHITE);
        combo.setMinimumSize(new Dimension(0, 22));
        combo.setPreferredSize(new Dimension(65, 22));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                cell.putClientProperty("html.disable", Boolean.TRUE);
                cell.setFont(FontManager.getRunescapeSmallFont());
                cell.setToolTipText(tooltip(String.valueOf(value)));
                return cell;
            }
        });
        return combo;
    }
    static String tooltip(String text) {
        return "<html><body style=\"width: 200px\">" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</body></html>";
    }
    static void fit(JPanel panel) {
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        panel.revalidate();
        panel.repaint();
    }
}




