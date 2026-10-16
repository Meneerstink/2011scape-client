import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Compact preset editor. Construction, settings changes and refresh belong to the Swing event thread. */
final class RspsPresetCard extends JPanel {
    private static final Map<Integer, Boolean> FOLDED = new HashMap<Integer, Boolean>();
    private static final List<WeakReference<RspsPresetCard>> LIVE = new ArrayList<WeakReference<RspsPresetCard>>();
    private static final AtomicBoolean REFRESH_QUEUED = new AtomicBoolean();
    private static boolean listenerRegistered;
    private static final String[] PRAYER_IDS = RspsPkData.withNone(RspsPkData.PRAYER_IDS, "NONE");
    private final int number;
    private final String prefix;
    private final JButton fold = new JButton();
    private final JCheckBox on = check("");
    private final RspsKeybindButton key;
    private final JPanel body = new JPanel();
    private final JLabel summary = RspsSpellRow.label(" ");
    private final JLabel message = RspsSpellRow.label(" ");
    private final JComboBox<String> gear = RspsSpellRow.combo(shortGearNames());
    private final JTextField items = new JTextField();
    private final JComboBox<String> prayerOne = RspsSpellRow.combo(RspsPkData.withNone(RspsPkData.PRAYER_NAMES, "None"));
    private final JComboBox<String> prayerTwo = RspsSpellRow.combo(RspsPkData.withNone(RspsPkData.PRAYER_NAMES, "None"));
    private final JCheckBox toggle = check("Key turns the prayers off too");
    private final JCheckBox target = check("Target");
    private final JComboBox<String> action = RspsSpellRow.combo(RspsPkHotkeys.TARGET_DO_NAMES);
    private final JComboBox<String> spell = RspsSpellRow.combo(RspsPkData.SPELL_NAMES);
    private boolean refreshing;
    private boolean dirtyItems;
    private String itemNotice = "";

    RspsPresetCard(int n, Runnable openGearEditor) {
        if (n < 1 || n > RspsPkHotkeys.PRESETS) throw new IllegalArgumentException("Invalid preset: " + n);
        number = n;
        prefix = "pkhotkeys.preset" + n;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(4, 4, 4, 4)));
        key = new RspsKeybindButton(RspsHudConfig.get(bindKey()), value -> write(bindKey(), value));
        key.setName(bindKey());
        key.setMinimumSize(new Dimension(0, 25));
        // v3.2 (Claude): the sidebar's own section arrows (the RuneScape font has no arrow glyphs).
        fold.putClientProperty("JButton.buttonType", "borderless");
        RspsSidebar.undecorate(fold);
        fold.setMargin(new Insets(0, 0, 0, 0));
        fold.setFocusPainted(false);
        fold.setPreferredSize(new Dimension(16, 22));
        fold.setMinimumSize(new Dimension(16, 22));
        fold.addActionListener(e -> {
            FOLDED.put(number, !isFolded());
            refresh();
        });
        on.setToolTipText("Enable this preset (its settings stay editable when switched off).");
        on.setName(prefix + "On");
        on.addActionListener(e -> write(prefix + "On", Boolean.toString(on.isSelected())));
        JPanel heading = new JPanel(new java.awt.GridBagLayout());
        heading.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 4);
        heading.add(fold, c);
        c.gridx = 1;
        heading.add(on, c);
        c.gridx = 2;
        heading.add(RspsSpellRow.label("Preset " + n), c);
        c.gridx = 3;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 0, 0);
        heading.add(key, c);
        addLine(heading, 25);
        summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        summary.setMinimumSize(new Dimension(0, 16));
        summary.setPreferredSize(new Dimension(170, 16));
        addLine(summary, 16);
        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        add(body);

        items.setName(prefix + "Items");
        items.setFont(FontManager.getRunescapeSmallFont());
        items.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        items.setForeground(Color.WHITE);
        items.setCaretColor(Color.WHITE);
        items.setMinimumSize(new Dimension(0, 22));
        items.setPreferredSize(new Dimension(65, 22));
        items.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { edited(); }
            public void removeUpdate(DocumentEvent e) { edited(); }
            public void changedUpdate(DocumentEvent e) { edited(); }
        });
        items.addActionListener(e -> saveItems());
        items.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { saveItems(); }
        });
        items.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { open(e); }
            @Override
            public void mouseReleased(MouseEvent e) { open(e); }
            private void open(MouseEvent e) {
                if (e.isPopupTrigger() && openGearEditor != null) openGearEditor.run();
            }
        });
        JPanel gearControls = pair(gear, items);
        addBodyLine(line(RspsSpellRow.label("Gear"), gearControls), 22);
        JPanel prayers = pair(prayerOne, prayerTwo);
        toggle.setToolTipText(RspsSpellRow.tooltip("Ticked: the key turns the prayers on and off. Not ticked: the key only turns them on."));
        addBodyLine(line(RspsSpellRow.label("Pray"), prayers), 22);
        // v3.2 (Claude, after the live look): the toggle on its own slim line, so the prayer names keep their width.
        addBodyLine(toggle, 18);
        addBodyLine(line(target, pair(action, spell)), 22);
        message.setForeground(ColorScheme.BRAND_ORANGE);
        message.setPreferredSize(new Dimension(170, 16));
        addBodyLine(message, 16);

        bind(gear, RspsPkHotkeys.PRESET_GEAR, "Gear");
        bind(prayerOne, PRAYER_IDS, "PrayerOne");
        bind(prayerTwo, PRAYER_IDS, "PrayerTwo");
        bind(action, RspsPkHotkeys.TARGET_DO, "TargetDo");
        bind(spell, RspsPkData.SPELL_IDS, "Spell");
        toggle.setName(prefix + "PrayerToggle");
        toggle.addActionListener(e -> write(prefix + "PrayerToggle", Boolean.toString(toggle.isSelected())));
        target.setName(prefix + "Target");
        target.setToolTipText("Perform the chosen last action after gear and prayers.");
        target.addActionListener(e -> write(prefix + "Target", Boolean.toString(target.isSelected())));
        prune();
        LIVE.add(new WeakReference<RspsPresetCard>(this));
        registerListener();
        refresh();
    }

    /** Re-read settings without firing writes or losing a draft; called by sidebar refreshers on the EDT. */
    void refresh() {
        refreshing = true;
        try {
            on.setSelected(RspsHudConfig.bool(prefix + "On"));
            key.setValue(RspsHudConfig.get(bindKey()));
            key.setToolTipText(RspsSpellRow.tooltip("Binding: " + RspsKeyBind.display(RspsHudConfig.get(bindKey()))
                + ". Click, then press a key (Ctrl/Alt/Shift allowed). Esc, Backspace or right-click clears it."));
            String[] titles = shortGearNames();
            for (int i = 0; i < titles.length; i++) {
                if (!titles[i].equals(gear.getItemAt(i))) {
                    gear.removeItemAt(i);
                    gear.insertItemAt(titles[i], i);
                }
            }
            select(gear, RspsPkHotkeys.PRESET_GEAR, "Gear");
            select(prayerOne, PRAYER_IDS, "PrayerOne");
            select(prayerTwo, PRAYER_IDS, "PrayerTwo");
            toggle.setSelected(RspsHudConfig.bool(prefix + "PrayerToggle"));
            target.setSelected(RspsHudConfig.bool(prefix + "Target"));
            select(action, RspsPkHotkeys.TARGET_DO, "TargetDo");
            select(spell, RspsPkData.SPELL_IDS, "Spell");
            int[] stored = RspsGearSets.presetItems(number);
            if (!dirtyItems) items.setText(join(stored));
            gear.setEnabled(stored.length == 0);
            gear.setToolTipText(RspsSpellRow.tooltip(stored.length > 0 ? "Items above replace the gear set"
                : "Gear set used when item IDs are empty. " + RspsSpellRow.selected(gear)));
            items.setToolTipText(RspsSpellRow.tooltip("Item IDs: comma/space separated, first " + RspsGearSets.MAX_ITEMS
                + " unique IDs. Enter or leave the field to save. Right-click opens the gear editor. Saved: " + itemNames(stored)));
            prayerOne.setToolTipText(RspsSpellRow.tooltip("First prayer. " + RspsSpellRow.selected(prayerOne)));
            prayerTwo.setToolTipText(RspsSpellRow.tooltip("Second prayer. " + RspsSpellRow.selected(prayerTwo)));
            action.setToolTipText(RspsSpellRow.tooltip("Last action: " + RspsSpellRow.selected(action)));
            spell.setToolTipText(RspsSpellRow.tooltip("Spell for Cast or Select. " + RspsSpellRow.selected(spell)));
            String doWhat = RspsHudConfig.get(prefix + "TargetDo");
            spell.setVisible("CAST".equals(doWhat) || "SELECT".equals(doWhat));
            grey(body, on.isSelected() ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
            items.setCaretColor(on.isSelected() ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
            // v3.2 (Claude): a key shared with another action shows here, like the sidebar's "Same key" note.
            String others = on.isSelected() ? RspsSidebar.sharedWithKey(bindKey()) : "";
            String notice = !itemNotice.isEmpty() ? itemNotice : others.isEmpty() ? "" : "Same key: " + others;
            message.setForeground(ColorScheme.BRAND_ORANGE);
            message.setText(notice.isEmpty() ? " " : notice);
            message.setToolTipText(notice.isEmpty() ? null : RspsSpellRow.tooltip(notice
                + (others.isEmpty() ? "" : ". The first one that fits your prayer book / spellbook runs.")));
            message.setVisible(!notice.isEmpty());
            boolean folded = isFolded();
            body.setVisible(!folded);
            summary.setVisible(folded);
            String text = describe(stored);
            summary.setText(text);
            summary.setForeground(notice.isEmpty() ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
            summary.setToolTipText(RspsSpellRow.tooltip(text + (notice.isEmpty() ? "" : ". " + notice)));
            fold.setIcon(folded ? RspsSidebar.SECTION_EXPAND_ICON : RspsSidebar.SECTION_RETRACT_ICON);
            fold.setToolTipText(folded ? "Expand preset" : "Fold preset");
        } finally {
            refreshing = false;
        }
        fitHeight();
    }

    private void edited() {
        if (!refreshing) {
            dirtyItems = true;
            itemNotice = "";
            message.setVisible(false);
            fitHeight();
        }
    }

    private void saveItems() {
        if (refreshing || !dirtyItems) return;
        Set<Integer> parsed = new LinkedHashSet<Integer>();
        boolean capped = false;
        for (String token : items.getText().trim().split("[,\\s]+")) {
            if (!token.matches("[0-9]+")) continue;
            final int id;
            try {
                id = Integer.parseInt(token);
            } catch (NumberFormatException overflow) {
                invalidItems();
                return;
            }
            if (!RspsGearSets.validId(id)) {
                invalidItems();
                return;
            }
            if (parsed.contains(id)) continue;
            if (parsed.size() < RspsGearSets.MAX_ITEMS) parsed.add(id);
            else capped = true;
        }
        int[] values = new int[parsed.size()];
        int i = 0;
        for (Integer id : parsed) values[i++] = id;
        if (!Arrays.equals(values, RspsGearSets.presetItems(number))) RspsGearSets.setPresetItems(number, values);
        dirtyItems = false;
        itemNotice = capped ? "First " + RspsGearSets.MAX_ITEMS + " IDs saved." : "";
        refresh();
    }

    private void invalidItems() {
        itemNotice = "IDs must be 0-65535; not saved.";
        refresh(); // dirtyItems keeps the rejected draft intact.
    }

    private void bind(JComboBox<String> combo, String[] values, String suffix) {
        combo.setName(prefix + suffix);
        combo.addActionListener(e -> {
            int index = combo.getSelectedIndex();
            if (!refreshing && index >= 0) write(prefix + suffix, values[index]);
        });
    }

    private void select(JComboBox<String> combo, String[] values, String suffix) {
        combo.setSelectedIndex(RspsSpellRow.indexOf(values, RspsHudConfig.get(prefix + suffix)));
    }

    private void write(String setting, String value) {
        if (refreshing) return;
        if (!value.equals(RspsHudConfig.get(setting))) RspsSidebar.set(setting, value);
        refresh();
    }

    private String bindKey() { return "pkhotkeys.bind.preset" + number + "Key"; }
    private boolean isFolded() {
        Boolean stored = FOLDED.get(number);
        return stored == null ? number >= 4 : stored.booleanValue();
    }

    private String describe(int[] stored) {
        List<String> parts = new ArrayList<String>();
        if (stored.length > 0) parts.add(stored.length + (stored.length == 1 ? " item" : " items"));
        else if (gear.getSelectedIndex() > 0) parts.add(RspsSpellRow.selected(gear));
        String one = RspsHudConfig.get(prefix + "PrayerOne");
        String two = RspsHudConfig.get(prefix + "PrayerTwo");
        if (RspsPkData.prayer(one) >= 0) parts.add(RspsSpellRow.selected(prayerOne));
        if (!two.equals(one) && RspsPkData.prayer(two) >= 0) parts.add(RspsSpellRow.selected(prayerTwo));
        if (toggle.isSelected() && (RspsPkData.prayer(one) >= 0 || RspsPkData.prayer(two) >= 0)) {
            int last = parts.size() - 1;
            parts.set(last, parts.get(last) + " (toggle)");
        }
        if (target.isSelected()) {
            String doWhat = RspsHudConfig.get(prefix + "TargetDo");
            if ("CAST".equals(doWhat)) parts.add("Cast " + RspsSpellRow.selected(spell));
            else if ("SELECT".equals(doWhat)) parts.add("Select " + RspsSpellRow.selected(spell));
            else if ("STEP".equals(doWhat)) parts.add("Step under target");
            else if ("ATTACK".equals(doWhat)) parts.add("Attack target");
        }
        return parts.isEmpty() ? "No actions selected" : String.join(" \u00b7 ", parts);
    }

    private static String[] shortGearNames() {
        String[] ids = RspsPkHotkeys.PRESET_GEAR;
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if ("NONE".equals(ids[i])) names[i] = "None";
            else {
                int n = Integer.parseInt(ids[i]);
                String basic = "Set " + n;
                String custom = RspsGearSets.name(n);
                names[i] = basic.equals(custom) ? basic : basic + " (" + custom + ")";
            }
        }
        return names;
    }

    private static String join(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int id : values) {
            if (out.length() > 0) out.append(", ");
            out.append(id);
        }
        return out.toString();
    }

    private static String itemNames(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int id : values) {
            if (out.length() > 0) out.append(", ");
            out.append(RspsGearSets.itemName(id)).append(" (").append(id).append(')');
        }
        return out.length() == 0 ? "empty (use gear set)" : out.toString();
    }

    private static synchronized void registerListener() {
        if (listenerRegistered) return;
        RspsGearSets.addListener(() -> {
            if (REFRESH_QUEUED.getAndSet(true)) return;
            SwingUtilities.invokeLater(() -> {
                REFRESH_QUEUED.set(false);
                Iterator<WeakReference<RspsPresetCard>> cards = LIVE.iterator();
                while (cards.hasNext()) {
                    RspsPresetCard card = cards.next().get();
                    if (card == null) cards.remove();
                    else card.refresh();
                }
            });
        });
        listenerRegistered = true;
    }

    private static void prune() {
        Iterator<WeakReference<RspsPresetCard>> cards = LIVE.iterator();
        while (cards.hasNext()) if (cards.next().get() == null) cards.remove();
    }

    private static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setOpaque(false);
        box.setMargin(new Insets(0, 0, 0, 0));
        box.setForeground(Color.WHITE);
        return box;
    }

    private static JPanel pair(JComponent left, JComponent right) {
        JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        return row;
    }

    private static JPanel line(JComponent title, JComponent controls) {
        if (title instanceof JLabel) {
            // Same column for "Gear" and "Pray" (Claude, after the live look).
            title.setPreferredSize(new Dimension(24, 16));
        }
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(title, BorderLayout.WEST);
        row.add(controls, BorderLayout.CENTER);
        return row;
    }

    private void addLine(JComponent component, int height) {
        if (getComponentCount() > 0) add(Box.createVerticalStrut(4));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        add(component);
    }

    private void addBodyLine(JComponent component, int height) {
        if (body.getComponentCount() > 0) body.add(Box.createVerticalStrut(4));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setMinimumSize(new Dimension(0, height));
        body.add(component);
    }

    private static void grey(Component component, Color colour) {
        component.setForeground(colour);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) grey(child, colour);
        }
    }

    private void fitHeight() {
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, body.getPreferredSize().height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }
}
