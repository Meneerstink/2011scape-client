import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import rsps.runelite.ImageUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Quest Helper side panel (owner 2026-10-07 night run: "full RuneLite-style panel"), the RuneLite Quest Helper plugin's
 * QuestHelperPanel (title, search, quest list grouped by state), QuestOverviewPanel (quest name with "Close helper",
 * requirement / enemy / reward sections) and QuestStepPanel (one section per step: the section holding the current step
 * has the BRAND_ORANGE header and expanded body, the current step text is orange, the others light grey; the others start
 * collapsed and toggle on a header click). Colours from the plugin (Zoinkwiz/quest-helper) and RuneLite's ColorScheme:
 * quest names white (not started), (240,207,123) in progress, green finished; requirement met green, missing red.
 * Owner additions: a green tick in front of finished steps, the step's place, a red Dangerous line and the step's tip.
 *
 * Only our playable quests, and only what the server sends (QuestHelperPanel.kt payloads qhq / qhb / qhs / qhe / qhn on
 * the HUD side channel) - no quest text lives in the client. The buttons send `questpanel ...` commands.
 */
final class RspsQuestHelper {

    static final ImageIcon PANEL_ICON;
    private static final ImageIcon CLOSE_ICON;
    private static final ImageIcon START_ICON;

    /** RuneLite Quest Helper colours (QuestSelectPanel, QuestHelperConfig, QuestStepPanel). */
    static final Color IN_PROGRESS = new Color(240, 207, 123);
    static final Color PASS = Color.GREEN;
    static final Color FAIL = Color.RED;
    static final Color STEP_TEXT = Color.LIGHT_GRAY;
    static final Color SECTION_HEADER = ColorScheme.DARKER_GRAY_COLOR.darker();
    static final Color STEP_BORDER = ColorScheme.DARK_GRAY_COLOR.brighter();

    static {
        PANEL_ICON = new ImageIcon(ImageUtil.load("qh_quest_icon.png"));
        CLOSE_ICON = new ImageIcon(ImageUtil.load("qh_close.png"));
        START_ICON = new ImageIcon(ImageUtil.load("qh_start.png"));
    }

    static final class Row {
        final int slot;
        final int state;
        final boolean tracked;
        final String name;

        Row(int slot, int state, boolean tracked, String name) {
            this.slot = slot;
            this.state = state;
            this.tracked = tracked;
            this.name = name;
        }
    }

    static final class Step {
        String text = "";
        String place;
        String danger;
        String tip;
    }

    static final class Overview {
        int slot;
        int state;
        int stage;
        boolean tracked;
        int steps;
        boolean travel;
        String name;
        final List<String> start = new ArrayList<String>();
        final List<String> req = new ArrayList<String>();
        final List<String> enemies = new ArrayList<String>();
        final List<String> rewards = new ArrayList<String>();
        final Map<Integer, Step> stepInfo = new LinkedHashMap<Integer, Step>();

        Step step(int n) {
            Step s = stepInfo.get(n);
            if (s == null) {
                s = new Step();
                stepInfo.put(n, s);
            }
            return s;
        }
    }

    private static List<Row> rows = new ArrayList<Row>();
    private static final List<Row> pendingRows = new ArrayList<Row>();
    private static Overview overview;
    private static Overview building;
    /** The list is on screen although an overview exists (searching, as in QuestHelperPanel). */
    private static boolean listWanted;
    private static String search = "";
    /** Step sections the player opened or closed by hand, per quest (RuneLite keeps the toggle until the step changes). */
    private static final Set<String> toggled = new HashSet<String>();
    private static int toggledStage = -1;

    private RspsQuestHelper() {
    }

    static synchronized void reset() {
        rows = new ArrayList<Row>();
        pendingRows.clear();
        overview = null;
        building = null;
        listWanted = false;
        search = "";
        toggled.clear();
    }

    /** A server payload (game thread). */
    static void handle(String kind, String rest) {
        // The in-game hints (world map, minimap, npc outline, dialogue options): RspsQuestHelperHints.
        if (kind.equals("qht") || kind.equals("qhd") || kind.equals("qhda")) {
            RspsQuestHelperHints.handle(kind, rest);
            return;
        }
        boolean redraw = false;
        synchronized (RspsQuestHelper.class) {
            String[] p = rest.split("\\|", -1);
            if (kind.equals("qhq")) {
                pendingRows.add(new Row(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2].equals("1"), p[3]));
            } else if (kind.equals("qhqe")) {
                rows = new ArrayList<Row>(pendingRows);
                pendingRows.clear();
                redraw = true;
            } else if (kind.equals("qhb")) {
                Overview o = new Overview();
                o.slot = Integer.parseInt(p[0]);
                o.state = Integer.parseInt(p[1]);
                o.stage = Integer.parseInt(p[2]);
                o.tracked = p[3].equals("1");
                o.steps = Integer.parseInt(p[4]);
                o.travel = p[5].equals("1");
                o.name = p[6];
                building = o;
            } else if (kind.equals("qhs") && building != null) {
                String what = p[0];
                int n = Integer.parseInt(p[1]);
                String text = p.length > 2 ? p[2] : "";
                if (what.equals("start")) {
                    building.start.add(text);
                } else if (what.equals("req")) {
                    building.req.add(text);
                } else if (what.equals("enemy")) {
                    building.enemies.add(text);
                } else if (what.equals("reward")) {
                    building.rewards.add(text);
                } else if (what.equals("step")) {
                    building.step(n).text += text;
                } else if (what.equals("place")) {
                    building.step(n).place = text;
                } else if (what.equals("danger")) {
                    building.step(n).danger = text;
                } else if (what.equals("tip")) {
                    building.step(n).tip = text;
                }
            } else if (kind.equals("qhe") && building != null) {
                Overview old = overview;
                overview = building;
                building = null;
                if (old == null || old.slot != overview.slot) {
                    listWanted = false;
                }
                if (old == null || old.slot != overview.slot || toggledStage != overview.stage) {
                    toggled.clear();
                    toggledStage = overview.stage;
                }
                redraw = true;
            } else if (kind.equals("qhn")) {
                overview = null;
                redraw = true;
            }
        }
        if (redraw) {
            RspsSidebar.refreshQuestLater();
        }
    }

    // ---- view (Swing thread) ------------------------------------------------------------------------------------------

    static JPanel build(final Runnable rebuild) {
        final List<Row> list;
        final Overview o;
        synchronized (RspsQuestHelper.class) {
            list = new ArrayList<Row>(rows);
            o = overview;
        }
        JPanel layout = new JPanel();
        layout.setLayout(new BoxLayout(layout, BoxLayout.Y_AXIS));
        layout.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title (QuestHelperPanel: "Quest Helper", white, padding 10).
        JPanel title = new JPanel(new BorderLayout());
        title.setBackground(ColorScheme.DARK_GRAY_COLOR);
        title.setBorder(new EmptyBorder(10, 10, 4, 10));
        JLabel name = new JLabel("Quest Helper");
        name.setForeground(Color.WHITE);
        title.add(name, BorderLayout.WEST);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        layout.add(title);

        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        searchWrap.setBorder(new EmptyBorder(6, 10, 6, 10));
        searchWrap.add(searchField(rebuild), BorderLayout.CENTER);
        searchWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        layout.add(searchWrap);

        if (o != null && !listWanted && search.length() == 0) {
            layout.add(overviewPanel(o, rebuild));
        } else {
            layout.add(questList(list, rebuild));
        }
        return layout;
    }

    private static JComponent searchField(final Runnable rebuild) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bar.setPreferredSize(new Dimension(RspsSidebar.PANEL_WIDTH - 20, 30));
        JLabel icon = new JLabel(new ImageIcon(ImageUtil.load("search.png")));
        icon.setPreferredSize(new Dimension(30, 0));
        bar.add(icon, BorderLayout.WEST);
        final JTextField field = new JTextField(search);
        field.setName("questSearch");
        field.setBorder(null);
        field.setOpaque(false);
        field.setForeground(ColorScheme.TEXT_COLOR);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                synchronized (RspsQuestHelper.class) {
                    search = field.getText().trim();
                }
                // QuestHelperPanel: typing shows the list, clearing returns to the overview.
                javax.swing.SwingUtilities.invokeLater(() -> {
                    rebuild.run();
                    RspsSidebar.focusQuestSearch();
                });
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
        bar.add(field, BorderLayout.CENTER);
        return bar;
    }

    // ---- the quest list (QuestSelectPanel) ----------------------------------------------------------------------------

    private static JPanel questList(List<Row> list, final Runnable rebuild) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(8, 10, 10, 10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        String[] groups = {"In progress", "Not started", "Completed"};
        int[] states = {1, 0, 2};
        String[] words = search.toLowerCase().split(" ");
        boolean any = false;
        for (int g = 0; g < groups.length; g++) {
            List<Row> inGroup = new ArrayList<Row>();
            for (Row r : list) {
                if (r.state != states[g]) {
                    continue;
                }
                boolean match = true;
                for (String w : words) {
                    if (w.length() > 0 && !r.name.toLowerCase().contains(w)) {
                        match = false;
                    }
                }
                if (match) {
                    inGroup.add(r);
                }
            }
            if (inGroup.isEmpty()) {
                continue;
            }
            any = true;
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            header.setBorder(new EmptyBorder(0, 8, 0, 8));
            header.setPreferredSize(new Dimension(RspsSidebar.PANEL_WIDTH - 20, 30));
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel label = new JLabel(groups[g]);
            label.setForeground(Color.WHITE);
            header.add(label, BorderLayout.CENTER);
            panel.add(header);
            panel.add(Box.createVerticalStrut(5));
            for (final Row r : inGroup) {
                panel.add(questRow(r));
                panel.add(Box.createVerticalStrut(5));
            }
        }
        if (!any) {
            JLabel none = new JLabel("<html><body style='width:180px'>There are no quests that match your search.</body></html>");
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            panel.add(none);
        }
        return panel;
    }

    private static JPanel questRow(final Row r) {
        final JPanel row = new JPanel(new BorderLayout(3, 0));
        row.setName("questRow." + r.slot);
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setPreferredSize(new Dimension(RspsSidebar.PANEL_WIDTH - 20, 22));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(r.name + (r.tracked ? "  •" : ""), SwingConstants.CENTER);
        name.setForeground(r.state == 2 ? PASS : r.state == 1 ? IN_PROGRESS : Color.WHITE);
        name.setToolTipText(r.tracked ? "Tracked by the Quest Helper" : "Open " + r.name);
        row.add(name, BorderLayout.CENTER);
        if (r.state != 2) {
            JButton start = plain(START_ICON, "Start the Quest Helper for " + r.name);
            start.setName("questStart." + r.slot);
            start.addActionListener(e -> RspsHud.command("questpanel track " + r.slot));
            row.add(start, BorderLayout.LINE_END);
        }
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter open = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                synchronized (RspsQuestHelper.class) {
                    listWanted = false;
                    search = "";
                }
                RspsHud.command("questpanel view " + r.slot);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        };
        row.addMouseListener(open);
        name.addMouseListener(open);
        return row;
    }

    // ---- the overview (QuestOverviewPanel + QuestStepPanel) ------------------------------------------------------------

    private static JPanel overviewPanel(final Overview o, final Runnable rebuild) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Title row: DARKER_GRAY, 30 px, name in white, "Close helper".
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setName("questTitle");
        titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titleRow.setBorder(new EmptyBorder(5, 10, 5, 5));
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel questName = new JLabel(html(o.name, 150));
        questName.setForeground(Color.WHITE);
        titleRow.add(questName, BorderLayout.CENTER);
        JButton close = plain(CLOSE_ICON, o.tracked ? "Close helper" : "Back to the quest list");
        close.setName("questClose");
        close.addActionListener(e -> RspsHud.command(o.tracked ? "questpanel stop" : "questpanel list"));
        titleRow.add(close, BorderLayout.LINE_END);
        panel.add(titleRow);

        // Start / Stop helper and Travel.
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.setBorder(new EmptyBorder(6, 0, 0, 0));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (o.state != 2) {
            JButton helper = new JButton(o.tracked ? "Stop helper" : "Start helper");
            helper.setName("questHelperToggle");
            helper.addActionListener(e -> RspsHud.command(o.tracked ? "questpanel stop" : "questpanel track " + o.slot));
            buttons.add(helper);
            JButton travel = new JButton("Travel");
            travel.setName("questTravel");
            travel.setEnabled(o.travel);
            travel.setToolTipText(o.travel ? "The Quest Tutor sends you to the next step" : "Travel from the Grand Exchange, where the Quest Tutor stands");
            travel.addActionListener(e -> RspsHud.command("questpanel travel " + o.slot));
            buttons.add(travel);
            panel.add(buttons);
        } else {
            JLabel done = new JLabel("Quest complete!");
            done.setForeground(PASS);
            done.setBorder(new EmptyBorder(6, 10, 0, 0));
            done.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(done);
        }

        // Intro panel: requirements, enemies, rewards (QuestOverviewPanel / QuestRequirementsPanel / QuestRewardsPanel).
        JPanel intro = new JPanel();
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        intro.setBorder(BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR));
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        section(intro, "Start:", o.start, Color.WHITE);
        section(intro, "Requirements:", o.req, PASS);
        section(intro, "Enemies to defeat:", o.enemies.isEmpty() ? single("None") : o.enemies, ColorScheme.LIGHT_GRAY_COLOR);
        section(intro, "Rewards:", o.rewards.isEmpty() ? single("None") : o.rewards, Color.WHITE);
        panel.add(intro);

        for (int n = 1; n <= o.steps; n++) {
            panel.add(Box.createVerticalStrut(5));
            panel.add(stepSection(o, n, rebuild));
        }
        return panel;
    }

    private static List<String> single(String s) {
        List<String> l = new ArrayList<String>();
        l.add(s);
        return l;
    }

    private static void section(JPanel parent, String header, List<String> lines, Color colour) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(new EmptyBorder(5, 5, 10, 5));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel h = new JLabel(header);
        h.setForeground(Color.WHITE);
        h.setBorder(new EmptyBorder(0, 0, 3, 0));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(h);
        for (String line : lines) {
            JLabel l = new JLabel(html(line, 160));
            l.setForeground(colour);
            l.setBorder(new EmptyBorder(1, 0, 1, 0));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(l);
        }
        parent.add(box);
    }

    private static JPanel stepSection(final Overview o, final int n, final Runnable rebuild) {
        Step s = o.stepInfo.get(n);
        if (s == null) {
            s = new Step();
        }
        final boolean done = o.state == 2 || n < o.stage;
        final boolean current = o.state != 2 && n == o.stage;
        final String key = o.slot + ":" + n;
        // RuneLite: the section holding the current step is expanded, the others start collapsed; a header click toggles.
        boolean expanded = current ^ toggled.contains(key);

        JPanel sec = new JPanel(new BorderLayout());
        sec.setName("questStep." + n);
        sec.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sec.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(current ? ColorScheme.BRAND_ORANGE : SECTION_HEADER);
        header.setBorder(new EmptyBorder(7, 7, 3, 7));
        String tick = done ? "<font color='#00ff00'>✔</font> " : "";
        String place = s.place != null && s.place.length() > 0 ? ": " + Character.toUpperCase(s.place.charAt(0)) + s.place.substring(1) : "";
        JLabel h = new JLabel(html(tick + "Step " + n + place, 160));
        h.setFont(FontManager.getRunescapeBoldFont());
        Color headerText = current ? Color.BLACK : Color.WHITE;
        h.setForeground(expanded ? headerText : headerText.darker());
        header.add(h, BorderLayout.CENTER);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                synchronized (RspsQuestHelper.class) {
                    if (!toggled.remove(key)) {
                        toggled.add(key);
                    }
                }
                rebuild.run();
            }
        });
        sec.add(header, BorderLayout.NORTH);

        if (expanded) {
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            body.setBorder(new EmptyBorder(10, 5, 10, 5));
            JLabel text = new JLabel(html(s.text, 160));
            text.setName("questStepText." + n);
            text.setForeground(current ? ColorScheme.BRAND_ORANGE : STEP_TEXT);
            text.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, STEP_BORDER),
                new EmptyBorder(5, 5, 10, 0)));
            text.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(text);
            if (s.danger != null) {
                JLabel d = new JLabel(html(s.danger, 160));
                d.setForeground(FAIL);
                d.setBorder(new EmptyBorder(4, 5, 0, 0));
                d.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(d);
            }
            if (s.tip != null) {
                JLabel t = new JLabel(html("Tip: " + s.tip, 160));
                t.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                t.setBorder(new EmptyBorder(4, 5, 0, 0));
                t.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(t);
            }
            sec.add(body, BorderLayout.CENTER);
        }
        return sec;
    }

    private static String html(String text, int width) {
        String safe = text.replace("&", "&amp;").replace("<font color='#00ff00'>", "\u0001").replace("</font>", "\u0002")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\u0001", "<font color='#00ff00'>").replace("\u0002", "</font>");
        return "<html><body style='width:" + width + "px'>" + safe + "</body></html>";
    }

    private static JButton plain(ImageIcon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        return button;
    }

    /** Tests and the HUD conformance: the overview the client holds. */
    static synchronized Overview overviewForTest() {
        return overview;
    }

    static synchronized List<Row> rowsForTest() {
        return new ArrayList<Row>(rows);
    }
}
