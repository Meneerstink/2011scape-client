import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Preset item IDs on Swing; cache names are resolved by RspsGearSets on the client thread. */
final class RspsPresetItemsRow extends JPanel {
    private static final List<WeakReference<RspsPresetItemsRow>> LIVE =
        new ArrayList<WeakReference<RspsPresetItemsRow>>();
    private static final AtomicBoolean REFRESH_QUEUED = new AtomicBoolean();
    private static boolean listenerRegistered;
    private final int preset;
    private final JTextField ids = new JTextField();
    private final JLabel names = label(" ", ColorScheme.LIGHT_GRAY_COLOR);
    private final JLabel message = label(" ", ColorScheme.BRAND_ORANGE);
    private boolean refreshing;
    private boolean dirty;

    /** Construct on the Swing thread, like RspsGearSetEditor. Empty items use the selected gear set. */
    RspsPresetItemsRow(int preset, Runnable openGearEditor) {
        if (preset < 1 || preset > RspsGearSets.PRESETS) {
            throw new IllegalArgumentException("Invalid preset: " + preset);
        }
        this.preset = preset;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow(label("Items (IDs)", Color.WHITE));
        ids.setFont(FontManager.getRunescapeSmallFont());
        ids.setForeground(Color.WHITE);
        ids.setCaretColor(Color.WHITE);
        ids.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        ids.setMinimumSize(new Dimension(20, ids.getPreferredSize().height));
        ids.setPreferredSize(new Dimension(170, ids.getPreferredSize().height));
        ids.setToolTipText("Comma or space separated item IDs, e.g. 4151, 8850");
        ids.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { edited(); }
            public void removeUpdate(DocumentEvent e) { edited(); }
            public void changedUpdate(DocumentEvent e) { edited(); }
        });
        ids.addActionListener(e -> save());
        ids.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { save(); }
        });
        addRow(ids);
        addRow(message);
        addRow(names);
        addRow(label(wrap("Empty = use the Gear set above. Order = click order (weapon before shield)."),
            ColorScheme.LIGHT_GRAY_COLOR));
        JButton open = new JButton("Open gear editor");
        open.setFont(FontManager.getRunescapeSmallFont());
        open.setMargin(new Insets(2, 4, 2, 4));
        open.setFocusPainted(false);
        open.addActionListener(e -> {
            if (openGearEditor != null) openGearEditor.run();
        });
        addRow(open);
        // The static registry holds no row strongly; discarded sidebar rows can be collected.
        pruneRows();
        LIVE.add(new WeakReference<RspsPresetItemsRow>(this));
        registerListener();
        refresh();
    }

    private void edited() {
        if (!refreshing) {
            dirty = true;
            message.setText(" ");
            fitHeight();
        }
    }

    /** Junk tokens are ignored; invalid numeric IDs reject the save so the draft can be corrected. */
    private void save() {
        if (refreshing || !dirty) return;
        Set<Integer> parsed = new LinkedHashSet<Integer>();
        boolean tooMany = false;
        for (String token : ids.getText().trim().split("[,\\s]+")) {
            if (!token.matches("[0-9]+")) continue;
            final int id;
            try {
                id = Integer.parseInt(token);
            } catch (NumberFormatException overflow) {
                rejectId();
                return;
            }
            if (!RspsGearSets.validId(id)) {
                rejectId();
                return;
            }
            if (parsed.contains(id)) continue;
            if (parsed.size() >= RspsGearSets.MAX_ITEMS) {
                tooMany = true;
            } else {
                parsed.add(id);
            }
        }
        int[] values = new int[parsed.size()];
        int index = 0;
        for (Integer id : parsed) values[index++] = id;
        if (!Arrays.equals(values, RspsGearSets.presetItems(preset))) {
            RspsGearSets.setPresetItems(preset, values);
        }
        dirty = false;
        message.setText(tooMany ? wrap("Only the first " + RspsGearSets.MAX_ITEMS + " unique IDs were saved.") : " ");
        refresh();
    }

    private void rejectId() {
        message.setText(wrap("Item IDs must be 0-65535. Nothing saved."));
        fitHeight();
    }

    /** Refresh names and model state without replacing an unsaved draft, even after a rejected focus-loss save. */
    private void refresh() {
        int[] stored = RspsGearSets.presetItems(preset);
        if (!dirty) {
            refreshing = true;
            try {
                ids.setText(join(stored));
            } finally {
                refreshing = false;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int id : stored) {
            if (text.length() > 0) text.append(", ");
            text.append(RspsGearSets.itemName(id));
        }
        names.setText(wrap(text.length() == 0 ? "No preset items - using the Gear set above." : text.toString()));
        names.setToolTipText(text.length() == 0 ? null : text.toString());
        fitHeight();
    }

    private static String join(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int value : values) {
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }

    /** One model listener for all live rows. Model callbacks only queue Swing work. */
    private static synchronized void registerListener() {
        if (listenerRegistered) return;
        RspsGearSets.addListener(() -> {
            if (REFRESH_QUEUED.getAndSet(true)) return;
            SwingUtilities.invokeLater(() -> {
                REFRESH_QUEUED.set(false);
                Iterator<WeakReference<RspsPresetItemsRow>> rows = LIVE.iterator();
                while (rows.hasNext()) {
                    RspsPresetItemsRow row = rows.next().get();
                    if (row == null) rows.remove();
                    else row.refresh();
                }
            });
        });
        listenerRegistered = true;
    }

    /** Registry access, like construction and refresh, stays on the EDT. */
    private static void pruneRows() {
        Iterator<WeakReference<RspsPresetItemsRow>> rows = LIVE.iterator();
        while (rows.hasNext()) {
            if (rows.next().get() == null) rows.remove();
        }
    }

    private static String wrap(String text) {
        return "<html><body style=\"width: 174px\">" + text.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
    }

    private void addRow(JComponent component) {
        if (getComponentCount() > 0) add(Box.createVerticalStrut(4));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        add(component);
    }

    private void fitHeight() {
        for (Component child : getComponents()) {
            if (child instanceof JComponent && !(child instanceof Box.Filler)) {
                child.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
            }
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        revalidate();
        repaint();
    }

    private static JLabel label(String text, Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(colour);
        return label;
    }
}
