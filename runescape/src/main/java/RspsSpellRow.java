import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

/** Two compact spell controls. Construct and refresh on the Swing event thread. */
final class RspsSpellRow extends JPanel {
    private final String spellKey;
    private final String bindKey;
    private final String modeKey;
    private final RspsKeybindButton key;
    private final JComboBox<String> spell = combo(RspsPkData.SPELL_NAMES);
    private final JComboBox<String> mode = combo(new String[] {"Cast", "Select"});
    private final JLabel shared = label(" ");
    private boolean refreshing;

    RspsSpellRow(String letter) {
        if (!"A".equals(letter) && !"B".equals(letter) && !"C".equals(letter) && !"D".equals(letter)) {
            throw new IllegalArgumentException("Spell letter must be A-D");
        }
        spellKey = "pkhotkeys.spell" + letter;
        bindKey = "pkhotkeys.bind.spell" + letter + "Key";
        modeKey = spellKey + "Mode";
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        key = new RspsKeybindButton(RspsHudConfig.get(bindKey), value -> write(bindKey, value));
        key.setName(bindKey);
        key.setMinimumSize(new Dimension(0, 25));
        JPanel header = new JPanel(new java.awt.GridBagLayout());
        header.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        c.insets = new java.awt.Insets(0, 0, 0, 4);
        header.add(label("Spell " + letter), c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        header.add(key, c);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(4));
        JPanel choices = new JPanel(new GridLayout(1, 2, 4, 0));
        choices.setOpaque(false);
        choices.setAlignmentX(Component.LEFT_ALIGNMENT);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        choices.add(spell);
        choices.add(mode);
        add(choices);
        shared.setForeground(ColorScheme.BRAND_ORANGE);
        shared.setAlignmentX(Component.LEFT_ALIGNMENT);
        shared.setMinimumSize(new Dimension(0, 16));
        add(shared);
        spell.setName(spellKey);
        mode.setName(modeKey);
        spell.addActionListener(e -> {
            int index = spell.getSelectedIndex();
            if (!refreshing && index >= 0) write(spellKey, RspsPkData.SPELL_IDS[index]);
        });
        mode.addActionListener(e -> {
            int index = mode.getSelectedIndex();
            if (!refreshing && index >= 0) write(modeKey, RspsPkHotkeys.SPELL_MODES[index]);
        });
        refresh();
    }

    void refresh() {
        refreshing = true;
        try {
            key.setValue(RspsHudConfig.get(bindKey));
            key.setToolTipText(tooltip("Binding: " + RspsKeyBind.display(RspsHudConfig.get(bindKey))
                + ". Click, then press a key (Ctrl/Alt/Shift allowed). Esc, Backspace or right-click clears it."));
            spell.setSelectedIndex(indexOf(RspsPkData.SPELL_IDS, RspsHudConfig.get(spellKey)));
            mode.setSelectedIndex(indexOf(RspsPkHotkeys.SPELL_MODES, RspsHudConfig.get(modeKey)));
            spell.setToolTipText(tooltip("Choose this key's spell. " + selected(spell)));
            mode.setToolTipText(tooltip("Cast: use the last target. Select: choose the spell, then click a target yourself."));
            String others = RspsSidebar.sharedWithKey(bindKey);
            shared.setText(others.isEmpty() ? " " : "Same key: " + others);
            shared.setToolTipText(others.isEmpty() ? null : tooltip("Also on this key: " + others + ". The first one that fits your prayer book / spellbook runs."));
            shared.setVisible(!others.isEmpty());
        } finally {
            refreshing = false;
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        revalidate();
        repaint();
    }

    private void write(String setting, String value) {
        if (refreshing) return;
        if (!value.equals(RspsHudConfig.get(setting))) RspsSidebar.set(setting, value);
        refresh();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(180, size.height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    /** Shared compact styling; popup entries retain their full names, selected values may be clipped. */
    static JComboBox<String> combo(String[] names) {
        final JComboBox<String> combo = new JComboBox<String>(names);
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.setForeground(Color.WHITE);
        combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        combo.setMinimumSize(new Dimension(0, 22));
        combo.setPreferredSize(new Dimension(65, 22));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focus) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                cell.putClientProperty("html.disable", Boolean.TRUE);
                cell.setFont(FontManager.getRunescapeSmallFont());
                cell.setToolTipText(tooltip(String.valueOf(value)));
                if (index < 0) cell.setForeground(combo.getForeground());
                return cell;
            }
        });
        return combo;
    }

    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.WHITE);
        return label;
    }

    static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return -1; // Unknown settings are displayed empty, never saved as a guessed default.
    }

    static String selected(JComboBox<String> combo) {
        Object value = combo.getSelectedItem();
        return value == null ? "None" : value.toString();
    }

    static String tooltip(String text) {
        return "<html><body style=\"width: 220px\">" + text.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
    }
}
