import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 78 Plugins sidebar - RuneLite's plugin panel next to the game view (owner 2026-09-29: "net als runelite in de
 * zijbalk schakelaars ... zelfde instellingen als runelite"). A navigation strip on the far right with a wrench button
 * opens / closes the panel (the window grows by the panel's width so the game view keeps its size). The panel lists the
 * plugins with a search field, each with its on/off switch and a gear that opens its configuration: every RuneLite config
 * item of that plugin (RspsHudConfig.SETTINGS) as a check box, number, choice, colour button or item list, and Reset.
 *
 * Every change takes effect in the client at once and goes to the server as a `hud ...` command (sent on the game thread,
 * RspsHud.tickInput); the server saves it with the account and pushes it back, which refreshes this panel. Swing only
 * runs on the event thread here: game-thread callers use {@link #openLater} / {@link #refreshLater}.
 */
final class RspsSidebar extends JPanel {

    // RuneLite ColorScheme.
    static final Color DARKER = new Color(30, 30, 30);
    static final Color DARK = new Color(40, 40, 40);
    static final Color MEDIUM = new Color(60, 60, 60);
    static final Color LIGHT = new Color(77, 77, 77);
    static final Color TEXT = new Color(198, 198, 198);
    static final Color TEXT_DIM = new Color(150, 150, 150);
    static final Color BRAND = new Color(220, 138, 0);
    static final Color ON = new Color(0, 160, 64);

    static final int PANEL_WIDTH = 225;
    static final int NAV_WIDTH = 36;

    static RspsSidebar instance;

    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel();
    private final JButton wrench = new JButton();
    private final Frame frame;
    private boolean expanded;
    private String plugin;
    private String search = "";

    /** Controls of the open view, to refresh their values from a server push. */
    private final Map<String, Runnable> refreshers = new HashMap<String, Runnable>();
    private final List<Runnable> listRefreshers = new ArrayList<Runnable>();

    RspsSidebar(Frame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setBackground(DARKER);
        content.setBackground(DARK);
        content.setPreferredSize(new Dimension(PANEL_WIDTH, 10));
        content.setVisible(false);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(DARKER);
        navigation.setPreferredSize(new Dimension(NAV_WIDTH, 10));
        navigation.setBorder(BorderFactory.createEmptyBorder(6, 3, 6, 3));
        wrench.setToolTipText("78 Plugins");
        wrench.setIcon(new WrenchIcon());
        flat(wrench);
        wrench.setMaximumSize(new Dimension(30, 30));
        wrench.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrench.addActionListener(e -> setExpanded(!expanded));
        navigation.add(wrench);
        navigation.add(Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);
        add(navigation, BorderLayout.EAST);
        showList();
    }

    // ---- entry points (any thread) ------------------------------------------------------------------------------------

    /** Opens the sidebar, at a plugin's configuration when [pluginId] is given (the `hud` command, the hud tour). */
    static void openLater(final String pluginId) {
        if (instance == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            instance.setExpanded(true);
            if (pluginId != null && pluginName(pluginId) != null) {
                instance.showConfig(pluginId);
            } else {
                instance.showList();
            }
        });
    }

    static void closeLater() {
        if (instance == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> instance.setExpanded(false));
    }

    /** A server push: the open view shows the new values. */
    static void refreshLater() {
        if (instance == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> instance.refresh());
    }

    // ---- layout -------------------------------------------------------------------------------------------------------

    boolean isExpanded() {
        return expanded;
    }

    String openPlugin() {
        return plugin;
    }

    void setExpanded(boolean open) {
        if (open == expanded) {
            return;
        }
        expanded = open;
        content.setVisible(open);
        // RuneLite: the window grows by the panel so the game view keeps its size (not when maximised).
        if (frame != null && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            Dimension size = frame.getSize();
            frame.setSize(size.width + (open ? PANEL_WIDTH : -PANEL_WIDTH), size.height);
        }
        revalidate();
        repaint();
    }

    static String pluginName(String id) {
        for (String[] p : RspsHudConfig.PLUGINS) {
            if (p[0].equals(id)) {
                return p[1];
            }
        }
        return null;
    }

    void showList() {
        plugin = null;
        refreshers.clear();
        listRefreshers.clear();
        content.removeAll();
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setBackground(DARK);
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        JLabel title = new JLabel("78 Plugins");
        title.setForeground(BRAND);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        top.add(title, BorderLayout.NORTH);
        final JTextField searchField = new JTextField(search);
        searchField.setBackground(MEDIUM);
        searchField.setForeground(TEXT);
        searchField.setCaretColor(TEXT);
        searchField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        searchField.setToolTipText("Search plugins");
        top.add(searchField, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);

        final JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(DARK);
        fillPluginRows(list);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                search = searchField.getText();
                list.removeAll();
                fillPluginRows(list);
                list.revalidate();
                list.repaint();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        content.add(scroll(list), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private void fillPluginRows(JPanel list) {
        String query = search.trim().toLowerCase();
        for (final String[] p : RspsHudConfig.PLUGINS) {
            if (query.length() > 0 && !p[1].toLowerCase().contains(query) && !p[2].toLowerCase().contains(query)) {
                continue;
            }
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBackground(MEDIUM);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DARK), BorderFactory.createEmptyBorder(6, 8, 6, 6)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel name = new JLabel(p[1]);
            name.setForeground(TEXT);
            name.setToolTipText(p[2]);
            row.add(name, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            buttons.setOpaque(false);
            JButton gear = new JButton(new GearIcon());
            flat(gear);
            gear.setToolTipText("Edit plugin configuration");
            gear.setName("config." + p[0]);
            gear.addActionListener(e -> showConfig(p[0]));
            final Toggle toggle = new Toggle(RspsHudConfig.enabled(p[0]));
            toggle.setName("toggle." + p[0]);
            toggle.setToolTipText(RspsHudConfig.enabled(p[0]) ? "Disable plugin" : "Enable plugin");
            toggle.onChange = on -> set(p[0] + ".enabled", Boolean.toString(on));
            refreshers.put(p[0] + ".enabled", () -> toggle.setOn(RspsHudConfig.enabled(p[0])));
            buttons.add(gear);
            buttons.add(toggle);
            row.add(buttons, BorderLayout.EAST);
            list.add(row);
        }
    }

    void showConfig(final String pluginId) {
        plugin = pluginId;
        refreshers.clear();
        listRefreshers.clear();
        content.removeAll();
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBackground(DARK);
        top.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 8));
        JButton back = new JButton(new BackIcon());
        flat(back);
        back.setToolTipText("Back");
        back.setName("back");
        back.addActionListener(e -> showList());
        top.add(back, BorderLayout.WEST);
        JLabel title = new JLabel(pluginName(pluginId));
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        top.add(title, BorderLayout.CENTER);
        final Toggle toggle = new Toggle(RspsHudConfig.enabled(pluginId));
        toggle.setName("toggle." + pluginId);
        toggle.onChange = on -> set(pluginId + ".enabled", Boolean.toString(on));
        refreshers.put(pluginId + ".enabled", () -> toggle.setOn(RspsHudConfig.enabled(pluginId)));
        top.add(toggle, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        JPanel items = new JPanel();
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        items.setBackground(DARK);
        items.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        String section = null;
        for (RspsHudConfig.Setting setting : RspsHudConfig.SETTINGS) {
            if (!setting.plugin.equals(pluginId)) {
                continue;
            }
            if (setting.section != null && !setting.section.equals(section)) {
                section = setting.section;
                JLabel header = new JLabel(section);
                header.setForeground(BRAND);
                header.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
                header.setAlignmentX(Component.LEFT_ALIGNMENT);
                items.add(header);
            } else if (setting.section == null && section != null) {
                section = null;
                items.add(Box.createVerticalStrut(6));
            }
            items.add(item(setting));
        }
        JButton reset = new JButton("Reset");
        reset.setName("reset");
        reset.setBackground(MEDIUM);
        reset.setForeground(TEXT);
        reset.setFocusPainted(false);
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.setToolTipText("Reset this plugin's configuration to its defaults");
        reset.addActionListener(e -> RspsHud.command("hud reset " + pluginId));
        items.add(Box.createVerticalStrut(10));
        items.add(reset);
        content.add(scroll(items), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private JComponent item(final RspsHudConfig.Setting setting) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, setting.type == RspsHudConfig.LIST ? 120 : 28));
        JLabel name = new JLabel(setting.name);
        name.setForeground(TEXT);
        name.setToolTipText("<html>" + setting.description + "</html>");
        final String key = setting.key;
        switch (setting.type) {
            case RspsHudConfig.BOOL: {
                final JCheckBox box = new JCheckBox();
                box.setName(key);
                box.setOpaque(false);
                box.setSelected(RspsHudConfig.bool(key));
                box.addActionListener(e -> set(key, Boolean.toString(box.isSelected())));
                refreshers.put(key, () -> box.setSelected(RspsHudConfig.bool(key)));
                row.add(name, BorderLayout.CENTER);
                row.add(box, BorderLayout.EAST);
                break;
            }
            case RspsHudConfig.INT: {
                final JSpinner spinner = new JSpinner(new SpinnerNumberModel(RspsHudConfig.integer(key), 0, setting.max, 1));
                spinner.setName(key);
                spinner.setPreferredSize(new Dimension(92, 22));
                spinner.addChangeListener(e -> {
                    String value = spinner.getValue().toString();
                    if (!value.equals(RspsHudConfig.get(key))) {
                        set(key, value);
                    }
                });
                refreshers.put(key, () -> {
                    int value = RspsHudConfig.integer(key);
                    if (!Integer.valueOf(value).equals(spinner.getValue())) {
                        spinner.setValue(value);
                    }
                });
                row.add(name, BorderLayout.CENTER);
                row.add(spinner, BorderLayout.EAST);
                break;
            }
            case RspsHudConfig.ENUM: {
                final JComboBox<String> combo = new JComboBox<String>(setting.optionNames);
                combo.setName(key);
                combo.setPreferredSize(new Dimension(108, 22));
                combo.setSelectedIndex(Math.max(0, indexOf(setting.options, RspsHudConfig.get(key))));
                combo.addActionListener(e -> {
                    String value = setting.options[Math.max(0, combo.getSelectedIndex())];
                    if (!value.equals(RspsHudConfig.get(key))) {
                        set(key, value);
                    }
                });
                refreshers.put(key, () -> {
                    int index = indexOf(setting.options, RspsHudConfig.get(key));
                    if (index >= 0 && index != combo.getSelectedIndex()) {
                        combo.setSelectedIndex(index);
                    }
                });
                row.add(name, BorderLayout.CENTER);
                row.add(combo, BorderLayout.EAST);
                break;
            }
            case RspsHudConfig.COLOR: {
                final JButton swatch = new JButton();
                swatch.setName(key);
                swatch.setPreferredSize(new Dimension(40, 20));
                swatch.setBorder(BorderFactory.createLineBorder(LIGHT));
                swatch.setBackground(new Color(RspsHudConfig.colour(key) & 0xFFFFFF));
                swatch.setToolTipText("#" + RspsHudConfig.get(key));
                swatch.addActionListener(e -> {
                    Color picked = JColorChooser.showDialog(this, setting.name, swatch.getBackground());
                    if (picked != null) {
                        set(key, String.format("%06x", picked.getRGB() & 0xFFFFFF));
                    }
                });
                refreshers.put(key, () -> {
                    swatch.setBackground(new Color(RspsHudConfig.colour(key) & 0xFFFFFF));
                    swatch.setToolTipText("#" + RspsHudConfig.get(key));
                });
                row.add(name, BorderLayout.CENTER);
                row.add(swatch, BorderLayout.EAST);
                break;
            }
            default: {
                // An item list: RuneLite's "item1, item2, item3" text box, saved when it loses focus.
                final boolean hidden = key.endsWith("hiddenItems");
                final JTextArea area = new JTextArea(String.join(", ", RspsHudConfig.names(hidden)), 3, 16);
                area.setName(key);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setBackground(MEDIUM);
                area.setForeground(TEXT);
                area.setCaretColor(TEXT);
                area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                area.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        saveList(hidden, area.getText());
                    }
                });
                listRefreshers.add(() -> {
                    if (!area.isFocusOwner()) {
                        area.setText(String.join(", ", RspsHudConfig.names(hidden)));
                    }
                });
                JPanel stacked = new JPanel(new BorderLayout(0, 3));
                stacked.setOpaque(false);
                stacked.add(name, BorderLayout.NORTH);
                stacked.add(area, BorderLayout.CENTER);
                row.add(stacked, BorderLayout.CENTER);
                break;
            }
        }
        return row;
    }

    private static int indexOf(String[] options, String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    // ---- changes ------------------------------------------------------------------------------------------------------

    /** One setting: set here at once, saved by the server (which pushes it back). */
    static void set(String key, String value) {
        RspsHudConfig.put(key, value);
        RspsHudGroundItems.markDirty();
        RspsHud.command("hud set " + key + " " + value);
    }

    /** The list as typed ("a, b, c"): the differences go to the server as adds and removes. */
    static void saveList(boolean hidden, String text) {
        Set<String> wanted = new LinkedHashSet<String>();
        for (String part : text.split(",")) {
            String name = part.trim().toLowerCase();
            if (name.length() > 0) {
                wanted.add(name);
            }
        }
        List<String> current = new ArrayList<String>(RspsHudConfig.names(hidden));
        String list = hidden ? "hide" : "highlight";
        for (String name : current) {
            if (!wanted.contains(name)) {
                RspsHud.command("hud list " + list + " remove " + name);
            }
        }
        for (String name : wanted) {
            if (!current.contains(name)) {
                RspsHud.command("hud list " + list + " add " + name);
            }
        }
    }

    void refresh() {
        for (Runnable refresher : refreshers.values()) {
            refresher.run();
        }
        for (Runnable refresher : listRefreshers) {
            refresher.run();
        }
    }

    // ---- look ---------------------------------------------------------------------------------------------------------

    private static JScrollPane scroll(JComponent view) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(DARK);
        holder.add(view, BorderLayout.NORTH);
        JScrollPane pane = new JScrollPane(holder);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(DARK);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    private static void flat(JButton button) {
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(26, 24));
    }

    /** RuneLite's plugin switch: a rounded pill, green with the knob right when on. */
    static final class Toggle extends JComponent {
        interface Listener {
            void changed(boolean on);
        }

        private boolean on;
        Listener onChange;

        Toggle(boolean on) {
            this.on = on;
            setPreferredSize(new Dimension(30, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    click();
                }
            });
        }

        boolean isOn() {
            return on;
        }

        void setOn(boolean on) {
            this.on = on;
            setToolTipText(on ? "Disable plugin" : "Enable plugin");
            repaint();
        }

        /** A click (also the tests' way in). */
        void click() {
            setOn(!on);
            if (onChange != null) {
                onChange.changed(on);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(on ? ON : LIGHT);
            g2.fillRoundRect(0, 1, w - 1, h - 2, h - 2, h - 2);
            g2.setColor(Color.WHITE);
            int knob = h - 6;
            g2.fillOval(on ? w - knob - 4 : 3, 3, knob, knob);
            g2.dispose();
        }
    }

    private abstract static class PaintedIcon implements javax.swing.Icon {
        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        Graphics2D prepare(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TEXT);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            return g2;
        }
    }

    /** The navigation button: a wrench, RuneLite's configuration icon. */
    static final class WrenchIcon extends PaintedIcon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            g2.setColor(BRAND);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 4, y + 12, x + 10, y + 6);
            g2.setStroke(new BasicStroke(2f));
            g2.drawArc(x + 8, y + 1, 7, 7, 200, 250);
            g2.dispose();
        }
    }

    /** The gear that opens a plugin's configuration. */
    static final class GearIcon extends PaintedIcon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            g2.drawOval(x + 5, y + 5, 6, 6);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                g2.drawLine(x + 8 + (int) Math.round(Math.cos(a) * 5), y + 8 + (int) Math.round(Math.sin(a) * 5),
                    x + 8 + (int) Math.round(Math.cos(a) * 7), y + 8 + (int) Math.round(Math.sin(a) * 7));
            }
            g2.dispose();
        }
    }

    static final class BackIcon extends PaintedIcon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            g2.drawLine(x + 10, y + 3, x + 5, y + 8);
            g2.drawLine(x + 5, y + 8, x + 10, y + 13);
            g2.dispose();
        }
    }

    /** Tests: the named component of the open view. */
    Component find(String name) {
        return find(content, name);
    }

    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                Component found = find(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
