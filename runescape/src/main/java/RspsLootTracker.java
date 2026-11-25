import com.jagex.IndexedImage;
import com.jagex.game.runetek6.config.objtype.ObjNumMode;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;
import com.jagex.graphics.ToolkitType;
import com.jagex.js5.js5;
import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import rsps.runelite.ImageUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * RuneLite Loot Tracker (owner 2026-09-30): LootTrackerPanel / LootTrackerBox / LootTrackerRecord / LootTrackerItem as a
 * tab of the 78 Plugins sidebar (its own navigation button, RuneLite's panel_icon). The server's shared kill-drop routine
 * sends one {@code loot|} event per kill with the items that player really received (server LootTracker, payloads documented
 * there); this class keeps RuneLite's two views of them - every single kill of this session, and the aggregate per source
 * (grouped, the default) - and draws them as RuneLite does: the overall panel (Total count / Total value, right-click Reset
 * All), the view controls (single / grouped, collapse all) and one box per record (title, "x kills", price, a 5-wide item
 * grid with the item's icon and count, tooltip with GE / HA, right-click Hide loot / Reset / Toggle item).
 *
 * Item icons are the game's own inventory icons (ObjType.sprite with a count, rendered on the game thread by
 * {@link #renderIcons}) turned into Java images.
 */
final class RspsLootTracker {

    static final int ITEMS_PER_ROW = 5;
    static final int TITLE_PADDING = 5;
    static final int MAX_LOOT_BOXES = 500;
    private static final String HTML_LABEL_TEMPLATE = "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

    static final ImageIcon PANEL_ICON;
    private static final ImageIcon SINGLE_LOOT_ICON;
    private static final ImageIcon SINGLE_LOOT_ICON_FADED;
    private static final ImageIcon SINGLE_LOOT_ICON_HOVER;
    private static final ImageIcon GROUPED_LOOT_ICON;
    private static final ImageIcon GROUPED_LOOT_ICON_FADED;
    private static final ImageIcon GROUPED_LOOT_ICON_HOVER;
    private static final ImageIcon VISIBLE_ICON;
    private static final ImageIcon VISIBLE_ICON_HOVER;
    private static final ImageIcon INVISIBLE_ICON;
    private static final ImageIcon INVISIBLE_ICON_HOVER;
    private static final ImageIcon COLLAPSE_ICON;
    private static final ImageIcon EXPAND_ICON;

    /** LootTrackerPanel.hideIgnoredItems (starts true): hidden sources and ignored items are left out until the eye shows them. */
    private static boolean hideIgnoredItems = true;

    static {
        PANEL_ICON = new ImageIcon(ImageUtil.load("loot_panel_icon.png"));
        BufferedImage single = ImageUtil.load("loot_single_loot_icon.png");
        BufferedImage grouped = ImageUtil.load("loot_grouped_loot_icon.png");
        // LootTrackerPanel: the unselected view buttons are the same icons at alphaOffset -180, hovered -220.
        SINGLE_LOOT_ICON = new ImageIcon(single);
        SINGLE_LOOT_ICON_FADED = new ImageIcon(ImageUtil.alphaOffset(single, -180));
        SINGLE_LOOT_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(single, -220));
        GROUPED_LOOT_ICON = new ImageIcon(grouped);
        GROUPED_LOOT_ICON_FADED = new ImageIcon(ImageUtil.alphaOffset(grouped, -180));
        GROUPED_LOOT_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(grouped, -220));
        BufferedImage visible = ImageUtil.load("loot_visible_icon.png");
        BufferedImage invisible = ImageUtil.load("loot_invisible_icon.png");
        VISIBLE_ICON = new ImageIcon(visible);
        VISIBLE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(visible, -220));
        INVISIBLE_ICON = new ImageIcon(invisible);
        INVISIBLE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(invisible, -220));
        COLLAPSE_ICON = new ImageIcon(ImageUtil.load("loot_collapsed.png"));
        EXPAND_ICON = new ImageIcon(ImageUtil.load("loot_expanded.png"));
    }

    /** LootTrackerItem: one item id of a record. */
    static final class Item {
        final int id;
        long quantity;
        final long gePrice;
        final long haPrice;

        Item(int id, long quantity, long gePrice, long haPrice) {
            this.id = id;
            this.quantity = quantity;
            this.gePrice = gePrice;
            this.haPrice = haPrice;
        }

        long totalGe() {
            return gePrice * quantity;
        }

        long totalHa() {
            return haPrice * quantity;
        }
    }

    /** LootTrackerRecord: a source (name + level) with its kills and items. */
    static final class Record {
        final String name;
        final int level;
        int kills;
        final LinkedHashMap<Integer, Item> items = new LinkedHashMap<Integer, Item>();

        Record(String name, int level) {
            this.name = name;
            this.level = level;
        }

        String key() {
            return level + "|" + name;
        }

        /** RuneLite LootTrackerPanel.add: subTitle "(lvl-N)" when the actor has a level. */
        String subTitle() {
            return level > -1 ? "(lvl-" + level + ")" : "";
        }

        void add(Item item) {
            Item have = items.get(item.id);
            if (have == null) {
                items.put(item.id, new Item(item.id, item.quantity, item.gePrice, item.haPrice));
            } else {
                have.quantity += item.quantity;
            }
        }
    }

    /** Aggregate per source ("grouped"), in the order sources were first seen; newest shown first. */
    private static final LinkedHashMap<String, Record> aggregate = new LinkedHashMap<String, Record>();
    /** Every kill of this session ("single"). */
    private static final List<Record> session = new ArrayList<Record>();
    /** Sources hidden with "Hide loot" (RuneLite ignoredEvents), kept on this client. Swing and game thread: concurrent. */
    private static final Set<String> hiddenEvents = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    /** Items toggled with "Toggle item" (RuneLite ignoredItems): not counted in the prices. */
    private static final Set<Integer> ignoredItems = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    static boolean groupLoot = true;
    static boolean allCollapsed;
    private static final Set<String> collapsed = new HashSet<String>();
    private static String lastAccount;

    /** Bumped on every change: the sidebar rebuilds the open loot view. */
    static volatile int revision;

    private RspsLootTracker() {
    }

    // ---- network (game thread) ----------------------------------------------------------------------------------------

    /** {@code loot|...}, {@code lootr|...}, {@code lootclear[|name|level]}. */
    static void handle(String kind, String rest) {
        synchronized (RspsLootTracker.class) {
            if (kind.equals("lootclear")) {
                if (rest.length() == 0) {
                    aggregate.clear();
                    session.clear();
                } else {
                    String[] parts = rest.split("\\|", -1);
                    int level = Integer.parseInt(parts[1]);
                    aggregate.remove(level + "|" + parts[0]);
                    for (int i = session.size() - 1; i >= 0; i--) {
                        Record r = session.get(i);
                        if (r.level == level && r.name.equals(parts[0])) {
                            session.remove(i);
                        }
                    }
                }
            } else {
                String[] parts = rest.split("\\|", -1);
                String name = parts[0];
                int level = Integer.parseInt(parts[1]);
                int kills = Integer.parseInt(parts[2]);
                List<Item> items = parseItems(parts.length > 3 ? parts[3] : "");
                Record agg = aggregate.get(level + "|" + name);
                if (agg == null) {
                    agg = new Record(name, level);
                    aggregate.put(agg.key(), agg);
                } else if (kills > 0 && kind.equals("loot")) {
                    // Newest source on top, as LootTrackerPanel moves an updated box to the top.
                    aggregate.remove(agg.key());
                    aggregate.put(agg.key(), agg);
                }
                agg.kills += kills;
                for (Item item : items) {
                    agg.add(item);
                }
                if (kind.equals("loot")) {
                    // One kill split over several payloads: the follow-ups (kills 0) add to the kill just made.
                    Record single;
                    if (kills > 0 || session.isEmpty()) {
                        single = new Record(name, level);
                        single.kills = 1;
                        session.add(single);
                        if (session.size() > MAX_LOOT_BOXES) {
                            session.remove(0);
                        }
                    } else {
                        single = session.get(session.size() - 1);
                    }
                    for (Item item : items) {
                        single.add(item);
                    }
                }
            }
            revision++;
        }
        RspsSidebar.refreshLootLater();
    }

    static List<Item> parseItems(String text) {
        List<Item> out = new ArrayList<Item>();
        if (text.length() == 0) {
            return out;
        }
        for (String entry : text.split(";")) {
            String[] f = entry.split(",");
            if (f.length < 4) {
                continue;
            }
            out.add(new Item(Integer.parseInt(f[0]), Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3])));
        }
        return out;
    }

    /**
     * Login / reconnect. The server re-sends the remembered aggregate ("Remember loot"), so that one is dropped here; with
     * "Remember loot" off it stays for the rest of this client session, as in RuneLite. Another account starts empty.
     */
    static synchronized void reset(String account) {
        boolean otherAccount = lastAccount != null && !lastAccount.equals(account);
        lastAccount = account;
        if (otherAccount) {
            session.clear();
            aggregate.clear();
            hiddenEvents.clear();
            ignoredItems.clear();
        } else if (RspsHudConfig.bool("loottracker.syncPanel")) {
            aggregate.clear();
        }
        revision++;
    }

    // ---- item icons ---------------------------------------------------------------------------------------------------

    private static final Map<Long, BufferedImage> icons = new ConcurrentHashMap<Long, BufferedImage>();
    private static final ConcurrentLinkedQueue<Long> wanted = new ConcurrentLinkedQueue<Long>();
    private static final Set<Long> asked = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    private static long iconKey(int id, long quantity) {
        return ((long) id << 32) | Math.min(quantity, Integer.MAX_VALUE);
    }

    /** Icons are made per id and count (the count is drawn in); a long session starts over past this many (~9 MB). */
    private static final int MAX_ICONS = 2000;

    // ---- N537 item audit: TestHook 'icons <idfile> <outdir>' writes every listed icon as <outdir>/<id>.png ---------------------

    private static final Map<Long, java.io.File> dumpTo = new ConcurrentHashMap<Long, java.io.File>();
    private static final Map<Long, Integer> dumpTries = new ConcurrentHashMap<Long, Integer>();
    private static volatile int dumpWritten;
    private static volatile int dumpFailed;

    /** Called by com.jagex.TestHook (reflection, default package): queue every id in idFile (one per line) for a PNG dump. */
    public static String dumpIcons(String idFile, String outDir) throws java.io.IOException {
        java.io.File dir = new java.io.File(outDir);
        dir.mkdirs();
        int queued = 0;
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(idFile))) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // '<id>' or '<id> <quantity>': a stack is drawn with the stack model (countobj) but without the count text.
            String[] f = line.split("\\s+");
            int id = Integer.parseInt(f[0]);
            int quantity = f.length > 1 ? Integer.parseInt(f[1]) : 1;
            long key = iconKey(id, quantity);
            dumpTo.put(key, new java.io.File(dir, id + ".png"));
            icons.remove(key);
            asked.remove(key);
            wantIcon(id, quantity);
            queued++;
        }
        dumpWritten = 0;
        dumpFailed = 0;
        return "ok icons queued " + queued;
    }

    static boolean dumping() {
        return !dumpTo.isEmpty();
    }

    public static String dumpStatus() {
        return "ok icons pending " + dumpTo.size() + " written " + dumpWritten + " failed " + dumpFailed;
    }

    private static void dumpDone(long key, BufferedImage image) {
        java.io.File file = dumpTo.remove(key);
        if (file == null) {
            return;
        }
        try {
            javax.imageio.ImageIO.write(image, "png", file);
            dumpWritten++;
        } catch (java.io.IOException e) {
            dumpFailed++;
        }
    }

    /** A dumped icon that threw: try again later, give up after 50 frames (written as <id>.err). */
    private static void dumpRetry(long key, RuntimeException e) {
        java.io.File file = dumpTo.get(key);
        if (file == null) {
            return;
        }
        int tries = dumpTries.merge(key, 1, Integer::sum);
        if (tries < 50) {
            wantIcon((int) (key >>> 32), key & 0xFFFFFFFFL);
            return;
        }
        dumpTo.remove(key);
        dumpFailed++;
        try {
            java.nio.file.Files.write(new java.io.File(file.getParentFile(), (key >>> 32) + ".err").toPath(), String.valueOf(e).getBytes("UTF-8"));
        } catch (java.io.IOException ignored) {
        }
    }

    static void wantIcon(int id, long quantity) {
        if (icons.size() > MAX_ICONS) {
            icons.clear();
            asked.clear();
        }
        long key = iconKey(id, quantity);
        if (!icons.containsKey(key) && asked.add(key)) {
            wanted.add(key);
        }
    }

    static BufferedImage icon(int id, long quantity) {
        BufferedImage image = icons.get(iconKey(id, quantity));
        if (image == null) {
            wantIcon(id, quantity);
        }
        return image;
    }

    /**
     * Game thread, once per frame (client.mainloop, right after Static369.updateObjSprites): renders a few wanted icons with the game's own inventory-icon code on the
     * software toolkit the client keeps for item icons (Static369.updateObjSprites), count drawn like the inventory.
     */
    static void renderIcons(Toolkit toolkit) {
        if (wanted.isEmpty() || ObjTypeList.instance == null) {
            return;
        }
        if (Static158.objSpriteToolkit == null) {
            java.awt.Canvas canvas = new java.awt.Canvas();
            canvas.setSize(36, 32);
            Static158.objSpriteToolkit = Static255.create(ToolkitType.JAVA, js5.SHADERS, canvas, Js5TextureSource.instance, 0);
            Fonts.objSpriteFont = Static158.objSpriteToolkit.createFont(FontMetrics.loadGroup(js5.FONTMETRICS, Fonts.p11FullGroup), IndexedImage.load(js5.SPRITES, Fonts.p11FullGroup, 0), true);
        }
        boolean made = false;
        int perFrame = dumpTo.isEmpty() ? 4 : 24;
        for (int i = 0; i < perFrame; i++) {
            Long key = wanted.poll();
            if (key == null) {
                break;
            }
            int id = (int) (key >>> 32);
            int quantity = (int) (key & 0xFFFFFFFFL);
            try {
                ObjType type = ObjTypeList.instance.list(id);
                if (quantity > 1 && type.countobj != null) {
                    int stackId = -1;
                    for (int c = 0; c < 10; c++) {
                        if (type.countco[c] <= quantity && type.countco[c] != 0) {
                            stackId = type.countobj[c];
                        }
                    }
                    if (stackId != -1) {
                        type = ObjTypeList.instance.list(stackId);
                    }
                }
                // Inventory look: black outline (1), the inventory shadow 0x302020, count when not 1 (ItemManager.getImage).
                int[] pixels = type.sprite(quantity > 1 && !dumpTo.containsKey(key) ? ObjNumMode.SHOWCOUNT_IFNOT1 : ObjNumMode.SHOWCOUNT_NEVER, toolkit, Math.max(1, quantity), 0x302020, false, null, Static158.objSpriteToolkit, Fonts.objSpriteFont, 1);
                if (pixels == null) {
                    // Model not loaded yet: ask again next frame.
                    asked.remove(key);
                    wantIcon(id, quantity);
                    continue;
                }
                BufferedImage image = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
                for (int p = 0; p < 36 * 32 && p < pixels.length; p++) {
                    int rgb = pixels[p];
                    if (rgb != 0) {
                        image.setRGB(p % 36, p / 36, 0xFF000000 | rgb);
                    }
                }
                icons.put(key, image);
                dumpDone(key, image);
                made = true;
            } catch (RuntimeException notReady) {
                asked.remove(key);
                dumpRetry(key, notReady);
            }
        }
        if (made) {
            RspsSidebar.refreshLootLater();
        }
    }

    // ---- panel (Swing thread) -----------------------------------------------------------------------------------------

    private static boolean ha() {
        return RspsHudConfig.is("loottracker.priceType", "HIGH_ALCHEMY");
    }

    /** RuneLite QuantityFormatter.quantityToStackSize. */
    static String stackSize(long quantity) {
        if (quantity < 0) {
            return "-" + stackSize(quantity == Long.MIN_VALUE ? Long.MAX_VALUE : -quantity);
        }
        NumberFormat formatter = NumberFormat.getInstance(Locale.ENGLISH);
        if (quantity < 10_000) {
            return formatter.format(quantity);
        }
        String[] suffixes = {"", "K", "M", "B"};
        String suffix = suffixes[0];
        long divideBy = 1;
        for (int i = suffixes.length - 1; i >= 0; i--) {
            divideBy = (long) Math.pow(10, i * 3);
            if ((double) quantity / divideBy >= 1) {
                suffix = suffixes[i];
                break;
            }
        }
        String text = formatter.format((double) quantity / divideBy);
        text = text.length() > 4 ? text.substring(0, 4) : text;
        return (text.endsWith(".") ? text.substring(0, 3) : text) + suffix;
    }

    static String formatNumber(long value) {
        return NumberFormat.getInstance(Locale.ENGLISH).format(value);
    }

    private static String htmlLabel(String key, long value) {
        Color c = ColorScheme.LIGHT_GRAY_COLOR;
        return String.format(HTML_LABEL_TEMPLATE, String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()), key, stackSize(value));
    }

    /** A copy of the records the current view shows, newest first. */
    static synchronized List<Record> records() {
        List<Record> out = new ArrayList<Record>();
        if (groupLoot) {
            out.addAll(aggregate.values());
        } else {
            out.addAll(session);
        }
        Collections.reverse(out);
        List<Record> copies = new ArrayList<Record>();
        for (Record r : out) {
            Record copy = new Record(r.name, r.level);
            copy.kills = r.kills;
            for (Item item : r.items.values()) {
                copy.add(item);
            }
            copies.add(copy);
        }
        return copies;
    }

    /**
     * The whole loot tracker view (RuneLite LootTrackerPanel), rebuilt on every change: 6 px border, a BorderLayout whose NORTH holds
     * the actions bar, the overall panel and the boxes' own container (logsContainer); the "no loot yet" panel in its centre. The
     * owner's 2026-10-09 "weird layout" came from one flat BoxLayout with left-aligned, 225-wide boxes beside centred panels.
     */
    static JPanel build(final Runnable rebuild) {
        final JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        final JPanel layout = new JPanel();
        layout.setLayout(new BoxLayout(layout, BoxLayout.Y_AXIS));
        layout.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.add(layout, BorderLayout.NORTH);

        // Actions: view controls (collapse all, grouped / single, show ignored) - LootTrackerPanel.buildActionsPanel order.
        JPanel actions = new JPanel(new BorderLayout());
        actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actions.setPreferredSize(new Dimension(0, 30));
        actions.setBorder(new EmptyBorder(5, 5, 5, 10));
        JPanel viewControls = new JPanel(new GridLayout(1, 3, 10, 0));
        viewControls.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        // RuneLite: icon EXPAND_ICON, selected icon COLLAPSE_ICON, selected while every box is collapsed.
        final JButton collapseBtn = plain(allCollapsed ? COLLAPSE_ICON : EXPAND_ICON, allCollapsed ? "Expand All" : "Collapse All");
        collapseBtn.addActionListener(e -> {
            allCollapsed = !allCollapsed;
            collapsed.clear();
            rebuild.run();
        });
        viewControls.add(collapseBtn);
        JToggleButton groupedLootBtn = new JToggleButton(groupLoot ? GROUPED_LOOT_ICON : GROUPED_LOOT_ICON_FADED);
        styleToggle(groupedLootBtn, "Group loot by source");
        groupedLootBtn.setRolloverIcon(groupLoot ? GROUPED_LOOT_ICON : GROUPED_LOOT_ICON_HOVER);
        groupedLootBtn.addActionListener(e -> {
            groupLoot = true;
            rebuild.run();
        });
        JToggleButton singleLootBtn = new JToggleButton(groupLoot ? SINGLE_LOOT_ICON_FADED : SINGLE_LOOT_ICON);
        styleToggle(singleLootBtn, "Show each kill separately");
        singleLootBtn.setRolloverIcon(groupLoot ? SINGLE_LOOT_ICON_HOVER : SINGLE_LOOT_ICON);
        singleLootBtn.addActionListener(e -> {
            groupLoot = false;
            rebuild.run();
        });
        viewControls.add(groupedLootBtn);
        viewControls.add(singleLootBtn);
        // The eye: selected (= hiding) shows INVISIBLE_ICON; tooltip "Show ignored items" / "Hide ignored items".
        JToggleButton viewHiddenBtn = new JToggleButton(hideIgnoredItems ? INVISIBLE_ICON : VISIBLE_ICON);
        styleToggle(viewHiddenBtn, hideIgnoredItems ? "Show ignored items" : "Hide ignored items");
        viewHiddenBtn.setRolloverIcon(hideIgnoredItems ? VISIBLE_ICON_HOVER : INVISIBLE_ICON_HOVER);
        viewHiddenBtn.addActionListener(e -> {
            hideIgnoredItems = !hideIgnoredItems;
            rebuild.run();
        });
        viewControls.add(viewHiddenBtn);
        actions.add(viewControls, BorderLayout.EAST);
        layout.add(actions);

        List<Record> records = records();
        long overallKills = 0;
        long overallGe = 0;
        long overallHa = 0;
        if (hideIgnoredItems) {
            records.removeIf(r -> hiddenEvents.contains(r.name));
        }
        for (Record r : records) {
            if (hiddenEvents.contains(r.name)) {
                continue;
            }
            for (Item item : r.items.values()) {
                if (!ignoredItems.contains(item.id)) {
                    overallGe += item.totalGe();
                    overallHa += item.totalHa();
                }
            }
            overallKills += r.kills;
        }

        // Overall panel: coin icon, Total count, Total value; right-click Reset All.
        JPanel overall = new JPanel(new BorderLayout());
        overall.setName("lootOverall");
        overall.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        overall.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overall.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        info.setBorder(new EmptyBorder(2, 10, 2, 0));
        String priceType = RspsHudConfig.bool("loottracker.showPriceType") ? (ha() ? "HA " : "GE ") : "";
        JLabel killsLabel = new JLabel(htmlLabel("Total count: ", overallKills));
        killsLabel.setName("lootTotalCount");
        killsLabel.setFont(FontManager.getRunescapeSmallFont());
        JLabel gpLabel = new JLabel(htmlLabel("Total " + priceType + "value: ", ha() ? overallHa : overallGe));
        gpLabel.setName("lootTotalValue");
        gpLabel.setFont(FontManager.getRunescapeSmallFont());
        gpLabel.setToolTipText("<html>Total GE price: " + formatNumber(overallGe) + "<br>Total HA price: " + formatNumber(overallHa) + "</html>");
        info.add(killsLabel);
        info.add(gpLabel);
        BufferedImage coins = icon(995, overallGe > 0 ? Math.max(1, Math.min(overallGe, Integer.MAX_VALUE)) : 1);
        JLabel coinsLabel = new JLabel(coins != null ? new ImageIcon(coins) : null);
        coinsLabel.setPreferredSize(new Dimension(36, 32));
        overall.add(coinsLabel, BorderLayout.WEST);
        overall.add(info, BorderLayout.CENTER);
        JPopupMenu overallMenu = new JPopupMenu();
        overallMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
        JMenuItem resetAll = new JMenuItem("Reset All");
        resetAll.addActionListener(e -> {
            int result = javax.swing.JOptionPane.showConfirmDialog(overall,
                "Are you sure you want to reset all loot tracker data?", "Are you sure?", javax.swing.JOptionPane.YES_NO_OPTION);
            if (result == javax.swing.JOptionPane.YES_OPTION) {
                RspsHud.command("hud loot reset");
            }
        });
        overallMenu.add(resetAll);
        overall.setComponentPopupMenu(overallMenu);
        // LootTrackerPanel: the actions bar and the overall panel only appear with the first loot.
        actions.setVisible(!records.isEmpty());
        overall.setVisible(!records.isEmpty());
        layout.add(overall);

        JPanel logsContainer = new JPanel();
        logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
        logsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layout.add(logsContainer);

        if (records.isEmpty()) {
            root.add(errorPanel("Loot tracker", "You have not received any loot yet."), BorderLayout.CENTER);
        }

        int shown = 0;
        for (Record r : records) {
            JPanel box = box(r, rebuild);
            if (box == null) {
                continue;
            }
            if (shown++ >= MAX_LOOT_BOXES) {
                break;
            }
            logsContainer.add(box);
        }
        return root;
    }

    /**
     * RuneLite PluginErrorPanel: border (50, 10, 0, 10); the title white in the regular font, the description centred below it in
     * the small font, grey.
     */
    private static JPanel errorPanel(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setName("lootEmpty");
        panel.setBorder(new EmptyBorder(50, 10, 0, 10));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontManager.getRunescapeFont());
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel descriptionLabel = new JLabel("<html><body style='text-align:center'>" + description + "</body></html>");
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        descriptionLabel.setForeground(Color.GRAY);
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(descriptionLabel, BorderLayout.CENTER);
        return panel;
    }

    private static JButton plain(ImageIcon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        return button;
    }

    private static void styleToggle(JToggleButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
    }

    /** LootTrackerBox. */
    private static JPanel box(final Record record, final Runnable rebuild) {
        final boolean eventIgnored = hiddenEvents.contains(record.name);
        JPanel box = new JPanel(new BorderLayout(0, 1));
        box.setName("lootBox:" + record.name);
        box.setBorder(new EmptyBorder(5, 0, 0, 0));
        box.setBackground(ColorScheme.DARK_GRAY_COLOR);

        List<Item> items = new ArrayList<Item>(record.items.values());
        if (hideIgnoredItems) {
            items.removeIf(item -> ignoredItems.contains(item.id));
        }
        // LootTrackerBox: a box without items left is not shown.
        if (items.isEmpty()) {
            return null;
        }
        final boolean highAlch = ha();
        // Sorted by price, highest first (LootTrackerBox.buildItems).
        items.sort((a, b) -> Long.compare(highAlch ? b.totalHa() : b.totalGe(), highAlch ? a.totalHa() : a.totalGe()));
        long totalPrice = 0;
        for (Item item : items) {
            if (!ignoredItems.contains(item.id)) {
                totalPrice += highAlch ? item.totalHa() : item.totalGe();
            }
        }

        JPanel title = new JPanel();
        title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
        title.setBorder(new EmptyBorder(7, 7, 7, 7));
        title.setBackground(eventIgnored ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR.darker());
        JLabel titleLabel = new JLabel(record.name);
        titleLabel.setFont(FontManager.getRunescapeSmallFont());
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setMinimumSize(new Dimension(1, titleLabel.getPreferredSize().height));
        title.add(titleLabel);
        JLabel subTitle = new JLabel(record.subTitle());
        subTitle.setFont(FontManager.getRunescapeSmallFont());
        subTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        if (record.kills > 1) {
            subTitle.setText("x " + record.kills);
            subTitle.setToolTipText(formatNumber(totalPrice / record.kills) + " gp (average)");
        }
        title.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));
        title.add(subTitle);
        title.add(Box.createHorizontalGlue());
        title.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));
        String priceTypeString = RspsHudConfig.bool("loottracker.showPriceType") ? (highAlch ? "HA: " : "GE: ") : " ";
        JLabel priceLabel = new JLabel(priceTypeString + stackSize(totalPrice) + " gp");
        priceLabel.setName("lootPrice:" + record.name);
        priceLabel.setFont(FontManager.getRunescapeSmallFont());
        priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        priceLabel.setToolTipText(formatNumber(totalPrice) + " gp");
        title.add(priceLabel);
        box.add(title, BorderLayout.NORTH);

        final String collapseKey = record.key();
        final boolean isCollapsed = allCollapsed ? !collapsed.contains(collapseKey) : collapsed.contains(collapseKey);
        title.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    if (!collapsed.remove(collapseKey)) {
                        collapsed.add(collapseKey);
                    }
                    rebuild.run();
                }
            }
        });

        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(new EmptyBorder(5, 5, 5, 5));
        JMenuItem toggle = new JMenuItem(eventIgnored ? "Include loot" : "Hide loot");
        toggle.addActionListener(e -> {
            if (!hiddenEvents.remove(record.name)) {
                hiddenEvents.add(record.name);
            }
            rebuild.run();
        });
        menu.add(toggle);
        if (groupLoot) {
            JMenuItem reset = new JMenuItem("Reset");
            reset.addActionListener(e -> RspsHud.command("hud loot reset " + record.level + " " + record.name));
            menu.add(reset);
        }
        box.setComponentPopupMenu(menu);
        title.setComponentPopupMenu(menu);

        if (!isCollapsed && !items.isEmpty()) {
            int rows = (items.size() % ITEMS_PER_ROW == 0 ? 0 : 1) + items.size() / ITEMS_PER_ROW;
            JPanel grid = new JPanel(new GridLayout(rows, ITEMS_PER_ROW, 1, 1));
            grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
            for (int i = 0; i < rows * ITEMS_PER_ROW; i++) {
                JPanel slot = new JPanel();
                slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                if (i < items.size()) {
                    final Item item = items.get(i);
                    BufferedImage image = icon(item.id, item.quantity);
                    boolean ignored = ignoredItems.contains(item.id);
                    JLabel label = new JLabel();
                    label.setName("lootItem:" + item.id);
                    if (image != null) {
                        label.setIcon(new ImageIcon(ignored ? ImageUtil.alphaOffset(image, .3f) : image));
                    } else {
                        label.setText(stackSize(item.quantity));
                        label.setForeground(Color.YELLOW);
                        label.setFont(FontManager.getRunescapeSmallFont());
                    }
                    label.setPreferredSize(new Dimension(36, 32));
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    label.setToolTipText(toolTip(item, ignored));
                    slot.add(label);
                    JPopupMenu itemMenu = new JPopupMenu();
                    itemMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
                    JMenuItem toggleItem = new JMenuItem("Toggle item");
                    toggleItem.addActionListener(e -> {
                        if (!ignoredItems.remove(item.id)) {
                            ignoredItems.add(item.id);
                        }
                        rebuild.run();
                    });
                    itemMenu.add(toggleItem);
                    slot.setComponentPopupMenu(itemMenu);
                    label.setComponentPopupMenu(itemMenu);
                }
                grid.add(slot);
            }
            box.add(grid, BorderLayout.CENTER);
        }
        return box;
    }

    /** LootTrackerBox.buildToolTip. */
    private static String toolTip(Item item, boolean ignored) {
        String name = itemName(item.id);
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(name).append(" x ").append(formatNumber(item.quantity)).append(ignored ? " - Ignored" : "");
        if (item.id == 995) {
            sb.append("</html>");
            return sb.toString();
        }
        sb.append("<br>GE: ").append(stackSize(item.totalGe()));
        if (item.quantity > 1) {
            sb.append(" (").append(stackSize(item.gePrice)).append(" ea)");
        }
        sb.append("<br>HA: ").append(stackSize(item.totalHa()));
        if (item.quantity > 1) {
            sb.append(" (").append(stackSize(item.haPrice)).append(" ea)");
        }
        sb.append("</html>");
        return sb.toString();
    }

    static String itemName(int id) {
        try {
            return ObjTypeList.instance.list(id).name;
        } catch (RuntimeException notLoaded) {
            return "Item " + id;
        }
    }
}
