import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import rsps.runelite.ImageUtil;
import rsps.runelite.RuneLiteLAF;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The 78 Plugins sidebar: RuneLite's plugin panel next to the game view, built as RuneLite builds it (owner 2026-09-29:
 * "net als runelite in de zijbalk schakelaars ... zelfde instellingen als runelite ... maak het ook visueel exact
 * runelite"). RuneLite's look and feel (FlatLaf + RuneLiteLAF.properties + ColorScheme + the RuneScape fonts,
 * rsps.runelite), RuneLite's icons (switcher_on, config_edit_icon, config_back_icon, config_icon, star_on, search,
 * arrow_right - resources /runelite, BSD-2-Clause) and the layout of RuneLite's PluginListPanel / PluginListItem /
 * ConfigPanel (PluginPanel.PANEL_WIDTH 225 + a 17 px scrollbar, 10 px borders, 5 px row gaps, 20 px plugin rows, the
 * star / name / gear / switch row, the 22 px back button, sections with an arrow and an orange bold title, Reset).
 *
 * A navigation strip on the far right with RuneLite's configuration icon opens / closes the panel (the window grows by
 * the panel's width so the game view keeps its size). Every change takes effect in the client at once and goes to the
 * server as a `hud ...` command (sent on the game thread, RspsHud.tickInput); the server saves it with the account and
 * pushes it back, which refreshes this panel. Swing only runs on the event thread: game-thread callers use
 * {@link #openLater} / {@link #refreshLater}.
 */
final class RspsSidebar extends JPanel {

    static final int PANEL_WIDTH = 225;
    static final int SCROLLBAR_WIDTH = 17;
    static final int BORDER_OFFSET = 6;
    static final int NAV_WIDTH = 30;
    private static final int SPINNER_FIELD_WIDTH = 6;

    private static final ImageIcon ON_SWITCHER;
    private static final ImageIcon OFF_SWITCHER;
    private static final ImageIcon ON_STAR;
    private static final ImageIcon OFF_STAR;
    private static final ImageIcon CONFIG_ICON;
    private static final ImageIcon BACK_ICON;
    private static final ImageIcon SECTION_EXPAND_ICON;
    private static final ImageIcon SECTION_RETRACT_ICON;
    private static final ImageIcon NAV_ICON;
    private static final ImageIcon SEARCH_ICON;

    static {
        // As RuneLite derives them (PluginToggleButton, PluginListItem, ConfigPanel).
        BufferedImage onSwitcher = ImageUtil.load("switcher_on.png");
        ON_SWITCHER = new ImageIcon(onSwitcher);
        OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(ImageUtil.luminanceScale(ImageUtil.grayscaleImage(onSwitcher), 0.61f), true, false));
        BufferedImage onStar = ImageUtil.load("star_on.png");
        ON_STAR = new ImageIcon(onStar);
        OFF_STAR = new ImageIcon(ImageUtil.luminanceScale(ImageUtil.grayscaleImage(onStar), 0.77f));
        CONFIG_ICON = new ImageIcon(ImageUtil.load("config_edit_icon.png"));
        BACK_ICON = new ImageIcon(ImageUtil.load("config_back_icon.png"));
        BufferedImage retract = ImageUtil.luminanceOffset(ImageUtil.load("arrow_right.png"), -121);
        SECTION_EXPAND_ICON = new ImageIcon(retract);
        SECTION_RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(retract, Math.PI / 2));
        NAV_ICON = new ImageIcon(ImageUtil.load("config_icon.png"));
        SEARCH_ICON = new ImageIcon(ImageUtil.load("search.png"));
    }

    static RspsSidebar instance;

    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel();
    private final Frame frame;
    private boolean expanded;
    private String plugin;
    private String search = "";
    private final Set<String> collapsedSections = new LinkedHashSet<String>();
    private JButton lootNav;
    /** The Loot Tracker tab is the open view. */
    private volatile boolean lootView;
    private JButton questNav;
    /** The Quest Helper tab is the open view. */
    private volatile boolean questView;

    /** Controls of the open view, to refresh their values from a server push. */
    private final Map<String, Runnable> refreshers = new HashMap<String, Runnable>();
    private final List<Runnable> listRefreshers = new ArrayList<Runnable>();

    /** RuneLite's look and feel, then the sidebar, on the event thread; call once from the loader. */
    static void install(final Frame frame) {
        Runnable create = () -> {
            try {
                RuneLiteLAF.setup();
            } catch (RuntimeException noLaf) {
                // Swing's own look: the sidebar still works
            }
            instance = new RspsSidebar(frame);
            frame.add(instance, BorderLayout.EAST);
            frame.validate();
        };
        try {
            SwingUtilities.invokeAndWait(create);
        } catch (Exception e) {
            SwingUtilities.invokeLater(create);
        }
    }

    RspsSidebar(Frame frame) {
        super(new BorderLayout());
        this.frame = frame;
        loadPins();
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setPreferredSize(new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, 10));
        content.setVisible(false);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        navigation.setPreferredSize(new Dimension(NAV_WIDTH, 10));
        navigation.setBorder(new EmptyBorder(6, 0, 6, 0));
        JButton nav = iconButton(NAV_ICON, "78 Plugins");
        nav.setName("navigation");
        nav.setAlignmentX(Component.CENTER_ALIGNMENT);
        nav.setMaximumSize(new Dimension(NAV_WIDTH, 30));
        nav.addActionListener(e -> {
            if (expanded && !lootView && !questView) {
                setExpanded(false);
            } else {
                setExpanded(true);
                showList();
            }
        });
        navigation.add(nav);
        // RuneLite Loot Tracker: its own navigation button (panel_icon), shown while the plugin is on.
        lootNav = iconButton(RspsLootTracker.PANEL_ICON, "Loot Tracker");
        lootNav.setName("navigation.loottracker");
        lootNav.setAlignmentX(Component.CENTER_ALIGNMENT);
        lootNav.setMaximumSize(new Dimension(NAV_WIDTH, 30));
        lootNav.addActionListener(e -> {
            if (expanded && lootView) {
                setExpanded(false);
            } else {
                setExpanded(true);
                showLoot();
            }
        });
        lootNav.setVisible(RspsHudConfig.enabled("loottracker"));
        navigation.add(lootNav);
        // RuneLite Quest Helper: its own navigation button (quest_icon), shown while the plugin is on (owner 2026-10-07).
        questNav = iconButton(RspsQuestHelper.PANEL_ICON, "Quest Helper");
        questNav.setName("navigation.questhelper");
        questNav.setAlignmentX(Component.CENTER_ALIGNMENT);
        questNav.setMaximumSize(new Dimension(NAV_WIDTH, 30));
        questNav.addActionListener(e -> {
            if (expanded && questView) {
                setExpanded(false);
            } else {
                setExpanded(true);
                showQuest();
            }
        });
        questNav.setVisible(RspsHudConfig.enabled("questhelper"));
        navigation.add(questNav);
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

    // ---- frame --------------------------------------------------------------------------------------------------------

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
            int panel = PANEL_WIDTH + SCROLLBAR_WIDTH;
            frame.setSize(size.width + (open ? panel : -panel), size.height);
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

    /** A loot event or a new item icon: the open Loot Tracker view shows it. */
    static void refreshLootLater() {
        if (instance == null || !instance.lootView) {
            return;
        }
        // Loot events and new icons can come several per frame: one rebuild waits at most (the next one sees them all).
        if (lootRefreshQueued.getAndSet(true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            lootRefreshQueued.set(false);
            if (instance.lootView) {
                instance.showLoot();
            }
        });
    }

    private static final java.util.concurrent.atomic.AtomicBoolean lootRefreshQueued = new java.util.concurrent.atomic.AtomicBoolean();

    // ---- Quest Helper (RuneLite QuestHelperPanel) ---------------------------------------------------------------------

    /** New quest data from the server: the open Quest Helper view shows it. */
    static void refreshQuestLater() {
        if (instance == null || !instance.questView) {
            return;
        }
        if (questRefreshQueued.getAndSet(true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            questRefreshQueued.set(false);
            if (instance.questView) {
                instance.showQuest();
            }
        });
    }

    private static final java.util.concurrent.atomic.AtomicBoolean questRefreshQueued = new java.util.concurrent.atomic.AtomicBoolean();

    /** After a rebuild while typing: the search field keeps the focus and the caret at the end. */
    static void focusQuestSearch() {
        if (instance == null) {
            return;
        }
        Component c = instance.find("questSearch");
        if (c instanceof JTextField) {
            JTextField f = (JTextField) c;
            f.requestFocusInWindow();
            f.setCaretPosition(f.getText().length());
        }
    }

    void showQuest() {
        questView = true;
        lootView = false;
        plugin = null;
        refreshers.clear();
        listRefreshers.clear();
        JScrollPane old = (JScrollPane) find("questScroll");
        int scrollY = old != null ? old.getVerticalScrollBar().getValue() : 0;
        content.removeAll();
        JPanel panel = RspsQuestHelper.build(this::showQuest);
        panel.setName("questHelper");
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrap.add(panel, BorderLayout.NORTH);
        JScrollPane scroll = (JScrollPane) scroll(wrap);
        scroll.setName("questScroll");
        content.add(scroll, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
        final int y = scrollY;
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y));
    }

    // ---- Loot Tracker (RuneLite LootTrackerPanel) ---------------------------------------------------------------------

    void showLoot() {
        lootView = true;
        questView = false;
        plugin = null;
        refreshers.clear();
        listRefreshers.clear();
        JScrollPane old = (JScrollPane) find("lootScroll");
        int scrollY = old != null ? old.getVerticalScrollBar().getValue() : 0;
        content.removeAll();
        JPanel panel = RspsLootTracker.build(this::showLoot);
        panel.setName("lootTracker");
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrap.add(panel, BorderLayout.NORTH);
        JScrollPane scroll = (JScrollPane) scroll(wrap);
        scroll.setName("lootScroll");
        content.add(scroll, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
        final int y = scrollY;
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y));
    }

    // ---- plugin list (RuneLite PluginListPanel / PluginListItem) ------------------------------------------------------

    void showList() {
        lootView = false;
        questView = false;
        plugin = null;
        refreshers.clear();
        listRefreshers.clear();
        content.removeAll();

        JPanel top = new JPanel(new BorderLayout(0, BORDER_OFFSET));
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);
        top.setBorder(new EmptyBorder(10, 10, 10, 10));
        top.add(searchBar(), BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        JPanel list = column(new EmptyBorder(8, 10, 10, 10));
        list.setName("pluginList");
        fillPluginRows(list);
        content.add(scroll(list), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    /** RuneLite IconTextField: a 30 px darker field with the search icon on the left and a red "x" to clear it. */
    private JComponent searchBar() {
        final JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bar.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
        JLabel icon = new JLabel(SEARCH_ICON);
        icon.setPreferredSize(new Dimension(30, 0));
        bar.add(icon, BorderLayout.WEST);
        final JTextField field = new JTextField(search);
        field.setName("search");
        field.setBorder(null);
        field.setOpaque(false);
        field.setForeground(ColorScheme.TEXT_COLOR);
        bar.add(field, BorderLayout.CENTER);
        final JButton clear = new JButton("×");
        clear.setFont(FontManager.getRunescapeBoldFont());
        clear.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        clear.setBorder(null);
        clear.setContentAreaFilled(false);
        clear.setFocusPainted(false);
        clear.setPreferredSize(new Dimension(30, 0));
        clear.setVisible(search.length() > 0);
        clear.addActionListener(e -> field.setText(""));
        clear.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                clear.setForeground(Color.PINK);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clear.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
            }
        });
        bar.add(clear, BorderLayout.EAST);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                bar.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        };
        bar.addMouseListener(hover);
        field.addMouseListener(hover);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                search = field.getText();
                clear.setVisible(search.length() > 0);
                JPanel list = (JPanel) find("pluginList");
                if (list != null) {
                    list.removeAll();
                    fillPluginRows(list);
                    list.revalidate();
                    list.repaint();
                }
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
        return bar;
    }

    private void fillPluginRows(JPanel list) {
        String query = search.trim().toLowerCase();
        List<String[]> order = new ArrayList<String[]>();
        // RuneLite: pinned plugins first, then by name.
        for (String[] p : RspsHudConfig.PLUGINS) {
            if (pinned.contains(p[0])) {
                order.add(p);
            }
        }
        for (String[] p : RspsHudConfig.PLUGINS) {
            if (!pinned.contains(p[0])) {
                order.add(p);
            }
        }
        boolean first = true;
        for (final String[] p : order) {
            if (query.length() > 0 && !p[1].toLowerCase().contains(query) && !p[2].toLowerCase().contains(query)) {
                continue;
            }
            if (!first) {
                list.add(Box.createVerticalStrut(5));
            }
            first = false;
            list.add(pluginRow(p));
        }
    }

    /** RuneLite PluginListItem: BorderLayout(3, 0), 20 px high - star, name, then the gear and the switch. */
    private JComponent pluginRow(final String[] p) {
        JPanel row = new JPanel(new BorderLayout(3, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(PANEL_WIDTH, 20));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JToggleButton pin = new JToggleButton(OFF_STAR);
        pin.setSelectedIcon(ON_STAR);
        pin.setSelected(pinned.contains(p[0]));
        pin.setToolTipText(pin.isSelected() ? "Unpin plugin" : "Pin plugin");
        pin.putClientProperty("JButton.buttonType", "borderless");
        undecorate(pin);
        pin.setPreferredSize(new Dimension(21, 0));
        pin.addActionListener(e -> {
            if (pin.isSelected()) {
                pinned.add(p[0]);
            } else {
                pinned.remove(p[0]);
            }
            savePins();
            showList();
        });
        row.add(pin, BorderLayout.LINE_START);

        JLabel name = new JLabel(p[1]);
        name.setForeground(Color.WHITE);
        name.setToolTipText("<html>" + p[1] + ":<br>" + p[2] + "</html>");
        row.add(name, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2));
        buttons.setOpaque(false);
        JButton config = iconButton(CONFIG_ICON, "Edit plugin configuration");
        config.setName("config." + p[0]);
        config.addActionListener(e -> showConfig(p[0]));
        buttons.add(config);
        final Toggle toggle = new Toggle(RspsHudConfig.enabled(p[0]));
        toggle.setName("toggle." + p[0]);
        toggle.onChange = on -> set(p[0] + ".enabled", Boolean.toString(on));
        refreshers.put(p[0] + ".enabled", () -> toggle.setOn(RspsHudConfig.enabled(p[0])));
        buttons.add(toggle);
        row.add(buttons, BorderLayout.LINE_END);
        return row;
    }

    // ---- configuration (RuneLite ConfigPanel) -------------------------------------------------------------------------

    void showConfig(final String pluginId) {
        lootView = false;
        questView = false;
        plugin = pluginId;
        refreshers.clear();
        listRefreshers.clear();
        content.removeAll();

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);
        top.setBorder(new EmptyBorder(10, 10, 10, 10));
        JButton back = iconButton(BACK_ICON, "Back");
        back.setName("back");
        back.setPreferredSize(new Dimension(22, 0));
        back.addActionListener(e -> showList());
        top.add(back, BorderLayout.WEST);
        JLabel title = new JLabel(pluginName(pluginId));
        title.setForeground(Color.WHITE);
        title.setToolTipText("<html>" + pluginName(pluginId) + ":<br>" + description(pluginId) + "</html>");
        top.add(title, BorderLayout.CENTER);
        final Toggle toggle = new Toggle(RspsHudConfig.enabled(pluginId));
        toggle.setName("toggle." + pluginId);
        toggle.onChange = on -> set(pluginId + ".enabled", Boolean.toString(on));
        refreshers.put(pluginId + ".enabled", () -> toggle.setOn(RspsHudConfig.enabled(pluginId)));
        top.add(toggle, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        JPanel main = column(new EmptyBorder(8, 10, 10, 10));
        Map<String, JPanel> sections = new HashMap<String, JPanel>();
        boolean first = true;
        for (RspsHudConfig.Setting setting : RspsHudConfig.SETTINGS) {
            if (!setting.plugin.equals(pluginId)) {
                continue;
            }
            JPanel target = main;
            if (setting.section != null) {
                JPanel section = sections.get(setting.section);
                if (section == null) {
                    if (!first) {
                        main.add(Box.createVerticalStrut(5));
                    }
                    first = false;
                    section = section(main, setting.section);
                    sections.put(setting.section, section);
                }
                target = section;
            } else {
                if (!first) {
                    main.add(Box.createVerticalStrut(5));
                }
                first = false;
            }
            if (target != main && target.getComponentCount() > 0) {
                target.add(Box.createVerticalStrut(5));
            }
            target.add(item(setting));
        }
        main.add(Box.createVerticalStrut(5));
        JButton reset = new JButton("Reset");
        reset.setName("reset");
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        reset.addActionListener(e -> {
            int answer = GraphicsEnvironmentCheck.headless() ? JOptionPane.YES_OPTION : JOptionPane.showOptionDialog(this,
                "Are you sure you want to reset this plugin's configuration?", "Are you sure?", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new String[]{"Yes", "No"}, "No");
            if (answer == JOptionPane.YES_OPTION) {
                RspsHud.command("hud reset " + pluginId);
            }
        });
        main.add(reset);
        content.add(scroll(main), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private static String description(String id) {
        for (String[] p : RspsHudConfig.PLUGINS) {
            if (p[0].equals(id)) {
                return p[2];
            }
        }
        return "";
    }

    /** RuneLite config section: an arrow and the orange bold title, the contents under a medium-grey rule. */
    private JPanel section(JPanel main, final String name) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        header.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(0, 0, 3, 1)));
        final boolean open = !collapsedSections.contains(name);
        final JButton arrow = iconButton(open ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON, open ? "Retract" : "Expand");
        arrow.setPreferredSize(new Dimension(18, 0));
        header.add(arrow, BorderLayout.WEST);
        JLabel title = new JLabel(name);
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(FontManager.getRunescapeBoldFont());
        String description = RspsHudConfig.SECTION_DESCRIPTIONS.get(name);
        if (description != null) {
            title.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
        }
        header.add(title, BorderLayout.CENTER);
        box.add(header);

        final JPanel contents = new JPanel();
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
        contents.setOpaque(false);
        contents.setAlignmentX(Component.LEFT_ALIGNMENT);
        contents.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
        contents.setVisible(open);
        box.add(contents);
        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (collapsedSections.contains(name)) {
                    collapsedSections.remove(name);
                } else {
                    collapsedSections.add(name);
                }
                boolean nowOpen = !collapsedSections.contains(name);
                contents.setVisible(nowOpen);
                arrow.setIcon(nowOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
                box.revalidate();
            }
        };
        header.addMouseListener(toggle);
        title.addMouseListener(toggle);
        arrow.addActionListener(e -> toggle.mouseClicked(null));
        main.add(box);
        return contents;
    }

    /** One RuneLite config item: a BorderLayout row, the white name, the control on the right (a text area below). */
    private JComponent item(final RspsHudConfig.Setting setting) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(setting.name);
        name.setForeground(Color.WHITE);
        name.setToolTipText("<html>" + setting.name + ":<br>" + setting.description + "</html>");
        // A long name gives way ("..."; the tooltip has it all) so the control on the right always shows.
        name.setMinimumSize(new Dimension(20, 16));
        name.setPreferredSize(new Dimension(20, 16));
        final String key = setting.key;
        switch (setting.type) {
            case RspsHudConfig.BOOL: {
                final JCheckBox box = new JCheckBox();
                box.setName(key);
                box.setSelected(RspsHudConfig.bool(key));
                box.addActionListener(e -> set(key, Boolean.toString(box.isSelected())));
                refreshers.put(key, () -> box.setSelected(RspsHudConfig.bool(key)));
                row.add(name, BorderLayout.CENTER);
                row.add(box, BorderLayout.EAST);
                break;
            }
            case RspsHudConfig.INT: {
                final JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(setting.min, Math.min(setting.max, RspsHudConfig.integer(key))), setting.min, setting.max, 1));
                spinner.setName(key);
                Component editor = spinner.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    ((JSpinner.DefaultEditor) editor).getTextField().setColumns(SPINNER_FIELD_WIDTH);
                }
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
                combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, 25));
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
                // RuneLite ColorJButton: the colour as the button, its hex code on it in a readable text colour.
                final JButton swatch = new JButton();
                swatch.setName(key);
                swatch.setFocusPainted(false);
                colourButton(swatch, RspsHudConfig.colour(key));
                swatch.addActionListener(e -> {
                    Color picked = GraphicsEnvironmentCheck.headless() ? null : JColorChooser.showDialog(this, setting.name, swatch.getBackground());
                    if (picked != null) {
                        set(key, String.format("%06x", picked.getRGB() & 0xFFFFFF));
                        colourButton(swatch, 0xFF000000 | picked.getRGB());
                    }
                });
                refreshers.put(key, () -> colourButton(swatch, RspsHudConfig.colour(key)));
                row.add(name, BorderLayout.CENTER);
                row.add(swatch, BorderLayout.EAST);
                break;
            }
            default: {
                // RuneLite's multi-line text box ("item1, item2, item3"), saved when it loses focus.
                final boolean hidden = key.endsWith("hiddenItems");
                final JTextArea area = new JTextArea(String.join(", ", RspsHudConfig.names(hidden)));
                area.setName(key);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setRows(3);
                area.setBorder(new EmptyBorder(4, 6, 4, 6));
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
                name.setPreferredSize(null);
                row.add(name, BorderLayout.NORTH);
                row.add(area, BorderLayout.SOUTH);
                break;
            }
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static void colourButton(JButton button, int argb) {
        Color colour = new Color(argb & 0xFFFFFF);
        button.setBackground(colour);
        button.setText("#" + String.format("%06X", argb & 0xFFFFFF));
        double luminance = 0.299 * colour.getRed() + 0.587 * colour.getGreen() + 0.114 * colour.getBlue();
        button.setForeground(luminance > 128 ? Color.BLACK : Color.WHITE);
        // As wide as the hex code needs (a fixed width cut it off), RuneLite's 25 px high.
        button.setPreferredSize(null);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 25));
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
        boolean lootOn = RspsHudConfig.enabled("loottracker");
        if (lootNav != null && lootNav.isVisible() != lootOn) {
            lootNav.setVisible(lootOn);
            navigation.revalidate();
            if (!lootOn && lootView) {
                showList();
            }
        }
        boolean questOn = RspsHudConfig.enabled("questhelper");
        if (questNav != null && questNav.isVisible() != questOn) {
            questNav.setVisible(questOn);
            navigation.revalidate();
            if (!questOn && questView) {
                showList();
            }
        }
        if (lootView) {
            // Price type / show price type changed: the loot view is rebuilt.
            showLoot();
            return;
        }
        if (questView) {
            showQuest();
            return;
        }
        for (Runnable refresher : refreshers.values()) {
            refresher.run();
        }
        for (Runnable refresher : listRefreshers) {
            refresher.run();
        }
    }

    // ---- pinned plugins (RuneLite keeps them on the profile; here next to the overlay positions) ----------------------

    private final Set<String> pinned = new LinkedHashSet<String>();

    private static File pinFile() {
        return new File(RspsOverlayLayout.file().getParentFile(), "sidebar.properties");
    }

    private void loadPins() {
        File file = pinFile();
        if (!file.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
            for (String id : properties.getProperty("pinned", "").split(",")) {
                if (id.trim().length() > 0) {
                    pinned.add(id.trim());
                }
            }
        } catch (Exception ignored) {
            // no pins
        }
    }

    private void savePins() {
        File file = pinFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Properties properties = new Properties();
        properties.setProperty("pinned", String.join(",", pinned));
        try (FileWriter writer = new FileWriter(file)) {
            properties.store(writer, "78 Plugins sidebar");
        } catch (Exception ignored) {
            // pins are a convenience
        }
    }

    // ---- look ---------------------------------------------------------------------------------------------------------

    private static JPanel column(EmptyBorder border) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(border);
        return panel;
    }

    /** A panel that always takes the viewport's width (RuneLite PluginPanel's wrapper does the same). */
    private static final class WidthTracking extends JPanel implements javax.swing.Scrollable {
        WidthTracking() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JScrollPane scroll(JComponent view) {
        JPanel holder = new WidthTracking();
        holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
        holder.add(view, BorderLayout.NORTH);
        JScrollPane pane = new JScrollPane(holder);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return pane;
    }

    /** RuneLite SwingUtil.removeButtonDecorations. */
    private static void undecorate(javax.swing.AbstractButton button) {
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static JButton iconButton(ImageIcon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.putClientProperty("JButton.buttonType", "borderless");
        undecorate(button);
        return button;
    }

    /** RuneLite PluginToggleButton: the switcher image (the OFF one derived from it), 25 px wide. */
    static final class Toggle extends JToggleButton {
        interface Listener {
            void changed(boolean on);
        }

        Listener onChange;
        private boolean quiet;

        Toggle(boolean on) {
            super(OFF_SWITCHER);
            setSelectedIcon(ON_SWITCHER);
            undecorate(this);
            setPreferredSize(new Dimension(25, 0));
            setSelected(on);
            updateTooltip();
            addItemListener(e -> {
                updateTooltip();
                if (!quiet && onChange != null) {
                    onChange.changed(isSelected());
                }
            });
        }

        private void updateTooltip() {
            setToolTipText(isSelected() ? "Disable plugin" : "Enable plugin");
        }

        boolean isOn() {
            return isSelected();
        }

        void setOn(boolean on) {
            quiet = true;
            setSelected(on);
            quiet = false;
        }

        /** A click (also the tests' way in). */
        void click() {
            doClick();
        }
    }

    /** Headless tests have no dialogs. */
    static final class GraphicsEnvironmentCheck {
        static boolean headless() {
            return java.awt.GraphicsEnvironment.isHeadless();
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
