import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The information overlays' settings: RuneLite's own config items of Boosts Information, Opponent Information, Ground
 * Items and Timers & Buffs (owner 2026-09-29: "zelfde instellingen als runelite"). The server owns the values (saved
 * with the account, server ClientHudConfig) and pushes them as {@code cfg|key=value;...}; the 78 Plugins sidebar
 * (RspsSidebar) shows and edits them and sends every change back as {@code hud set <key> <value>}.
 *
 * {@link #SETTINGS} is the sidebar's description of each item (name, description and choices as RuneLite shows them); the
 * defaults here only apply until the login push arrives and match the server's.
 */
final class RspsHudConfig {

    static final int BOOL = 0;
    static final int INT = 1;
    static final int ENUM = 2;
    static final int COLOR = 3;
    static final int LIST = 4;

    /** One config item as the sidebar shows it. */
    static final class Setting {
        final String plugin;
        final String key;
        final int type;
        final String name;
        final String description;
        final String defaultValue;
        final String[] options;
        final String[] optionNames;
        final String section;
        final int max;
        /** Lowest value of an INT item (RuneLite @Range min; 0 when it has none). */
        int min;

        Setting(String plugin, String key, int type, String name, String description, String defaultValue, String section, int max, String[] options, String[] optionNames) {
            this.plugin = plugin;
            this.key = key;
            this.type = type;
            this.name = name;
            this.description = description;
            this.defaultValue = defaultValue;
            this.section = section;
            this.max = max;
            this.options = options;
            this.optionNames = optionNames;
        }
    }

    /** Plugin id, name and description (RuneLite's plugin list). */
    static final String[][] PLUGINS = {
        {"boosts", "Boosts Information", "Show combat and/or skill boost information"},
        {"opponent", "Opponent Information", "Show name and hitpoints information about the NPC you are fighting"},
        {"grounditems", "Ground Items", "Highlight ground items and/or show price information"},
        {"timers", "Timers & Buffs", "Show various timers in an infobox"},
        // Owner 2026-09-30. RuneLite's Camera plugin keeps its old config group "zoom"; only its zoom items are imported.
        {"zoom", "Camera", "Expands zoom limit"},
        {"minimap", "Minimap", "Zoom the minimap"},
        {"loottracker", "Loot Tracker", "Tracks loot from monsters and minigames"},
        // Owner 2026-10-01: the resizable/fullscreen chatbox background (RspsChatbox).
        {"chatbox", "Chatbox", "Transparent or opaque chatbox in resizable and fullscreen"},
        // Owner 2026-10-07: the Quest Helper as a plugin (RuneLite Quest Helper: "An in-game interactive guide for quests").
        {"questhelper", "Quest Helper", "An in-game interactive guide for quests"},
    };

    static final List<Setting> SETTINGS = new ArrayList<Setting>();

    /** RuneLite ConfigSection descriptions, the section header tooltips. */
    static final Map<String, String> SECTION_DESCRIPTIONS = new java.util.HashMap<String, String>();

    static {
        SECTION_DESCRIPTIONS.put("Item lists", "The highlighted and hidden item lists.");
        SECTION_DESCRIPTIONS.put("Spells", "Timers and buffs related to spells you cast.");
        SECTION_DESCRIPTIONS.put("Miscellaneous", "Timers and buffs related to miscellaneous items or activities.");
    }

    private static final String[] TIERS = {"OFF", "LOW", "MEDIUM", "HIGH", "INSANE"};
    private static final String[] TIER_NAMES = {"Off", "Low", "Medium", "High", "Insane"};
    private static final String[] CHANGE = {"ALWAYS", "BOOSTED", "NEVER"};
    private static final String[] CHANGE_NAMES = {"Always", "Boosted", "Never"};

    private static void add(String plugin, String key, int type, String name, String description, String def, String section, int max, String[] options, String[] optionNames) {
        SETTINGS.add(new Setting(plugin, plugin + "." + key, type, name, description, def, section, max, options, optionNames));
    }

    private static void bool(String plugin, String key, String name, String description, boolean def, String section) {
        add(plugin, key, BOOL, name, description, Boolean.toString(def), section, 0, null, null);
    }

    private static void integer(String plugin, String key, String name, String description, int def, int max, String section) {
        add(plugin, key, INT, name, description, Integer.toString(def), section, max, null, null);
    }

    private static void integer(String plugin, String key, String name, String description, int def, int min, int max, String section) {
        add(plugin, key, INT, name, description, Integer.toString(def), section, max, null, null);
        SETTINGS.get(SETTINGS.size() - 1).min = min;
    }

    private static void choice(String plugin, String key, String name, String description, String def, String[] options, String[] names, String section) {
        add(plugin, key, ENUM, name, description, def, section, 0, options, names);
    }

    private static void colour(String plugin, String key, String name, String description, String def, String section) {
        add(plugin, key, COLOR, name, description, def, section, 0, null, null);
    }

    static {
        String b = "boosts";
        choice(b, "displayBoosts", "Display boosts", "Configures which skill boosts to display.", "COMBAT",
            new String[]{"NONE", "COMBAT", "NON_COMBAT", "BOTH"}, new String[]{"None", "Combat", "Non-combat", "Both"}, null);
        bool(b, "relativeBoost", "Show relative boosts", "Configures whether or not relative boost is used.", false, null);
        bool(b, "displayInfoboxes", "Display infoboxes", "Configures whether to display boost infoboxes.", false, null);
        bool(b, "displayPanel", "Display panel", "Configures whether to display the boost panel.", true, null);
        bool(b, "compactDisplay", "Compact display", "Displays skill boosts in a more compact panel.", false, null);
        choice(b, "displayNextBuffChange", "Next buff change", "Configures whether or not to display when the next buffed stat change will be.", "BOOSTED", CHANGE, CHANGE_NAMES, null);
        choice(b, "displayNextDebuffChange", "Next debuff change", "Configures whether or not to display when the next debuffed stat change will be.", "BOOSTED", CHANGE, CHANGE_NAMES, null);
        integer(b, "boostThreshold", "Boost threshold", "Number of levels above your base level at which boosted levels will be displayed in a different color.", 0, 99, null);
        bool(b, "notifyOnBoost", "Notify on boost threshold", "Configures whether or not a notification will be sent when boosted stats drain to the boost threshold.", false, null);

        String o = "opponent";
        choice(o, "hitpointsDisplayStyle", "Display style", "Show opponent's hitpoints as a value (if known), percentage, or both.", "HITPOINTS",
            new String[]{"HITPOINTS", "PERCENTAGE", "BOTH"}, new String[]{"Hitpoints", "Percentage", "Both"}, null);
        bool(o, "showOpponentsInMenu", "Show opponents in menu", "Marks opponents names in the menu which you are attacking or are attacking you (NPC only).", false, null);
        bool(o, "showOpponentHealthOverlay", "Show opponent health overlay", "Shows a health bar overlay with the opponent's name and hitpoints.", true, null);

        String g = "grounditems";
        String lists = "Item lists";
        add(g, "highlightedItems", LIST, "Highlighted items", "Configures specifically highlighted ground items. Format: item1, item2, item3", "", lists, 0, null, null);
        add(g, "hiddenItems", LIST, "Hidden items", "Configures hidden ground items. Format: item1, item2, item3", "", lists, 0, null, null);
        bool(g, "showHighlightedOnly", "Show highlighted items only", "Configures whether or not to draw items only on your highlighted list.", false, null);
        bool(g, "dontHideUntradeables", "Do not hide untradeables", "Configures whether or not untradeable items ignore hiding under settings.", true, null);
        bool(g, "showMenuItemQuantities", "Show menu item quantities", "Configures whether or not to show the item quantities in the menu.", true, null);
        // Both at RuneLite position 5; its config panel then sorts by name.
        bool(g, "deprioritizeHiddenItems", "Deprioritize menu hidden items", "Depriotizies the menu options for items which are hidden, requiring a right-click to pick up.", false, null);
        bool(g, "recolorMenuHiddenItems", "Recolor menu hidden items", "Configures whether or not hidden items in right-click menu will be recolored.", false, null);
        bool(g, "highlightTiles", "Highlight tiles", "Configures whether or not to highlight tiles containing ground items.", false, null);
        bool(g, "notifyHighlightedDrops", "Notify for highlighted drops", "Configures whether or not to notify for drops on your highlighted list.", false, null);
        choice(g, "notifyTier", "Notify tier", "Configures which price tiers will trigger a notification on drop.", "OFF", TIERS, TIER_NAMES, null);
        choice(g, "priceDisplayMode", "Price display mode", "Configures which price types are shown alongside ground item name.", "GE",
            new String[]{"HA", "GE", "BOTH", "OFF"}, new String[]{"High Alchemy", "Grand Exchange", "Both", "Off"}, null);
        choice(g, "itemHighlightMode", "Item highlight mode", "Configures how ground items will be highlighted.", "BOTH",
            new String[]{"NONE", "OVERLAY", "MENU", "BOTH"}, new String[]{"None", "Overlay", "Right-click menu", "Both"}, null);
        choice(g, "menuHighlightMode", "Menu highlight mode", "Configures what to highlight in right-click menu.", "NAME",
            new String[]{"OPTION", "NAME", "BOTH"}, new String[]{"Option", "Name", "Both"}, null);
        choice(g, "highlightValueCalculation", "Highlight value calculation", "Configures which coin value is used to determine highlight color.", "HIGHEST",
            new String[]{"HA", "GE", "HIGHEST"}, new String[]{"High Alchemy", "Grand Exchange", "Highest"}, null);
        integer(g, "hideUnderValue", "Hide under value", "Configures hidden ground items under both GE and HA value.", 0, Integer.MAX_VALUE, null);
        colour(g, "defaultColor", "Default items", "Configures the color for default, non-highlighted items.", "ffffff", null);
        colour(g, "highlightedColor", "Highlighted items", "Configures the color for highlighted items.", "aa00ff", null);
        colour(g, "hiddenColor", "Hidden items", "Configures the color for hidden items in right-click menu and when holding the hotkey.", "808080", null);
        colour(g, "lowValueColor", "Low value items", "Configures the color for low value items.", "66b2ff", null);
        integer(g, "lowValuePrice", "Low value price", "Configures the start price for low value items.", 20000, Integer.MAX_VALUE, null);
        colour(g, "mediumValueColor", "Medium value items", "Configures the color for medium value items.", "99ff99", null);
        integer(g, "mediumValuePrice", "Medium value price", "Configures the start price for medium value items.", 100000, Integer.MAX_VALUE, null);
        colour(g, "highValueColor", "High value items", "Configures the color for high value items.", "ff9600", null);
        integer(g, "highValuePrice", "High value price", "Configures the start price for high value items.", 1000000, Integer.MAX_VALUE, null);
        colour(g, "insaneValueColor", "Insane value items", "Configures the color for insane value items.", "ff66b2", null);
        integer(g, "insaneValuePrice", "Insane value price", "Configures the start price for insane value items.", 10000000, Integer.MAX_VALUE, null);
        choice(g, "ownershipFilterMode", "Ownership filter", "Show all items, takeable items, or only your drops.", "ALL",
            new String[]{"ALL", "TAKEABLE", "DROPS"}, new String[]{"All", "Takeable", "Drops"}, null);
        integer(g, "doubleTapDelay", "Double-tap delay", "Delay for the double-tap hotkey to hide ground items (ms). 0 to disable.", 250, 1000, null);
        bool(g, "collapseEntries", "Collapse ground item menu", "Collapses ground item menu entries together and appends count.", false, null);
        choice(g, "groundItemTimers", "Despawn timer", "Shows despawn timers for items you've dropped and received as loot.", "SECONDS",
            new String[]{"OFF", "PIE", "TICKS", "SECONDS"}, new String[]{"Off", "Pie", "Ticks", "Seconds"}, null);
        bool(g, "textOutline", "Text outline", "Use an outline around text instead of a text shadow.", false, null);
        bool(g, "showLootbeamForHighlighted", "Highlighted item lootbeams", "Configures lootbeams to show for all highlighted items.", false, null);
        choice(g, "showLootbeamTier", "Lootbeam tier", "Configures which price tiers will trigger a lootbeam.", "HIGH", TIERS, TIER_NAMES, null);
        choice(g, "lootbeamStyle", "Lootbeam style", "Style of lootbeam to use.", "MODERN", new String[]{"MODERN", "LEGACY"}, new String[]{"Modern", "Legacy"}, null);
        choice(g, "hotkey", "Hotkey", "Configures the hotkey used by the Ground Items plugin.", "ALT", new String[]{"ALT", "SHIFT", "CTRL"}, new String[]{"Alt", "Shift", "Ctrl"}, null);

        // RuneLite CameraConfig (group "zoom"), the zoom items, positions 1..6.
        String z = "zoom";
        bool(z, "inner", "Expand inner zoom limit", "Configures whether or not the inner zoom limit is reduced.", false, null);
        integer(z, "outerLimit", "Expand outer zoom limit", "Configures how much the outer zoom limit is adjusted.", 0, -400, 400, null);
        choice(z, "controlFunction", "Control function", "Configures the zoom function when control is pressed.", "NONE",
            new String[]{"NONE", "CONTROL_TO_ZOOM", "CONTROL_TO_RESET"}, new String[]{"None", "Hold to zoom", "Reset zoom"}, null);
        integer(z, "ctrlZoomValue", "Reset zoom position", "Position of zoom when it is reset.", 512, -400, 1400, null);
        integer(z, "zoomIncrement", "Zoom speed", "Speed of zoom.", 25, 0, Integer.MAX_VALUE, null);

        // RuneLite MinimapConfig: "Zoom".
        bool("minimap", "zoom", "Zoom", "Enables zooming on the minimap.", true, null);

        // RuneLite LootTrackerConfig (the ignore lists are the panel's own right-click actions here).
        bool("chatbox", "transparent", "Transparent chatbox",
            "Resizable and fullscreen: see the game through the chatbox. Off = the opaque parchment chatbox of fixed mode.", true, null);

        // RuneLite QuestHelperConfig items this helper has (the server ClientHudConfig keeps the same keys).
        String q = "questhelper";
        bool(q, "autoStartQuests", "Auto start helpers", "Automatically start the quest helper when you start a quest.", true, null);
        bool(q, "showOverlayPanel", "Display overlay on screen", "Show the panel with the current step in the top-left corner of the game.", true, null);
        bool(q, "showMiniMapArrow", "Display arrows", "Show the hint arrow over the next npc or place, and on the minimap.", true, null);
        bool(q, "showStepMessages", "Step messages in chat", "Write each new quest step in the chatbox.", true, null);

        String l = "loottracker";
        choice(l, "priceType", "Price type", "What type of price to use for calculating value.", "GRAND_EXCHANGE",
            new String[]{"GRAND_EXCHANGE", "HIGH_ALCHEMY"}, new String[]{"Grand Exchange", "High Alchemy"}, null);
        bool(l, "showPriceType", "Show price type", "Whether to show a GE: or HA: next to the total values in the tracker.", false, null);
        bool(l, "syncPanel", "Remember loot", "Saves loot between client sessions.", true, null);
        bool(l, "npcKillChatMessage", "Show chat message for NPC kills", "Adds a chat message with monster name and kill value when receiving loot from an NPC kill.", false, null);
        bool(l, "pvpKillChatMessage", "Show chat message for PvP kills", "Adds a chat message with player name and kill value when receiving loot from a player kill.", false, null);

        // RuneLite TimersAndBuffsConfig sections; items without a position sort by name inside their section.
        String t = "timers";
        String spells = "Spells";
        String misc = "Miscellaneous";
        bool(t, "showVengeanceActive", "Vengeance active", "Configures whether an indicator for vengeance being active is displayed.", true, spells);
        bool(t, "showVengeance", "Vengeance timer", "Configures whether vengeance and vengeance other timer is displayed.", true, spells);
        bool(t, "showFreezes", "Freeze timer", "Configures whether freeze timer is displayed.", true, misc);
        bool(t, "showTeleblock", "Teleblock timer", "Configures whether teleblock timer is displayed.", true, misc);
        bool(t, "showAntiPoison", "Antipoison/Venom timers", "Configures whether antipoison/antivenom timers are displayed.", true, misc);
        // RuneLite's separate Poison plugin infobox (next damage + time to it), kept with the other timers here.
        bool(t, "showPoison", "Poison/Venom", "Shows poison and venom with the next hit's damage and the time until it lands.", true, misc);
        bool(t, "showBurn", "Burn", "Shows active burn stacks (the next burn hit) and the time until it lands.", true, misc);
    }

    /** Read by the sidebar (Swing thread) while the game thread writes: a concurrent map. */
    private static final Map<String, String> values = new java.util.concurrent.ConcurrentHashMap<String, String>();

    private static final List<Pattern> highlighted = new ArrayList<Pattern>();
    private static final List<Pattern> hidden = new ArrayList<Pattern>();
    /** The same lists as typed (lower case), for the sidebar. */
    static final List<String> highlightedNames = new ArrayList<String>();
    static final List<String> hiddenNames = new ArrayList<String>();

    /** Bumped on every change, so the sidebar and the label cache know when to refresh. */
    static volatile int revision;

    private RspsHudConfig() {
    }

    static {
        resetToDefaults();
    }

    static synchronized void resetToDefaults() {
        values.clear();
        for (Setting setting : SETTINGS) {
            if (setting.type != LIST) {
                values.put(setting.key, setting.defaultValue);
            }
        }
        for (String[] plugin : PLUGINS) {
            values.put(plugin[0] + ".enabled", "true");
        }
        // RuneLite CameraPlugin: enabledByDefault = false.
        values.put("zoom.enabled", "false");
        highlighted.clear();
        hidden.clear();
        highlightedNames.clear();
        hiddenNames.clear();
        revision++;
    }

    static Setting setting(String key) {
        for (Setting setting : SETTINGS) {
            if (setting.key.equals(key)) {
                return setting;
            }
        }
        return null;
    }

    static String get(String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    static boolean bool(String key) {
        return "true".equals(values.get(key));
    }

    static int integer(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException notSet) {
            return 0;
        }
    }

    static boolean is(String key, String option) {
        return option.equals(values.get(key));
    }

    static int colour(String key) {
        try {
            return 0xFF000000 | Integer.parseInt(get(key), 16);
        } catch (NumberFormatException notSet) {
            return 0xFFFFFFFF;
        }
    }

    static boolean enabled(String plugin) {
        return bool(plugin + ".enabled");
    }

    /** Local change (the sidebar sets it at once; the server's push confirms it). */
    static void put(String key, String value) {
        values.put(key, value);
        revision++;
    }

    static synchronized void handle(String kind, String rest) {
        if (kind.equals("cfg")) {
            for (String pair : rest.split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            revision++;
            return;
        }
        List<Pattern> list = kind.equals("hl") ? highlighted : hidden;
        List<String> names = kind.equals("hl") ? highlightedNames : hiddenNames;
        if (rest.equals("clear")) {
            list.clear();
            names.clear();
        } else if (rest.startsWith("add|")) {
            for (String name : rest.substring(4).split(",")) {
                if (name.length() > 0) {
                    list.add(wildcard(name));
                    names.add(name);
                }
            }
        }
        revision++;
    }

    /** A copy (the sidebar reads it on the Swing thread). */
    static synchronized List<String> names(boolean hiddenList) {
        return new ArrayList<String>(hiddenList ? hiddenNames : highlightedNames);
    }

    /** RuneLite WildcardMatcher: '*' matches anything, the rest literally, case-insensitive, whole name. */
    static Pattern wildcard(String name) {
        StringBuilder regex = new StringBuilder();
        String[] parts = name.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    static synchronized boolean isHighlighted(String name) {
        return matches(highlighted, name);
    }

    static synchronized boolean isHidden(String name) {
        return matches(hidden, name);
    }

    private static boolean matches(List<Pattern> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    // ---- Ground Items value tiers (RuneLite HighlightTier) ------------------------------------------------------------

    static final int TIER_NONE = 0;
    static final int TIER_LOW = 1;
    static final int TIER_MEDIUM = 2;
    static final int TIER_HIGH = 3;
    static final int TIER_INSANE = 4;

    private static final String[] TIER_KEYS = {null, "low", "medium", "high", "insane"};

    /** The highest tier [value] reaches (0 = none): strictly above the tier's price, as GroundItemsPlugin's price checks. */
    static int tier(long value) {
        for (int tier = TIER_INSANE; tier >= TIER_LOW; tier--) {
            if (value > integer("grounditems." + TIER_KEYS[tier] + "ValuePrice")) {
                return tier;
            }
        }
        return TIER_NONE;
    }

    static int tierColour(int tier) {
        return tier == TIER_NONE ? colour("grounditems.defaultColor") : colour("grounditems." + TIER_KEYS[tier] + "ValueColor");
    }

    /** A tier setting (notifyTier, showLootbeamTier) as a tier number; OFF = no tier reaches it. */
    static int tierSetting(String key) {
        String value = get(key);
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(value)) {
                return i == 0 ? Integer.MAX_VALUE : i;
            }
        }
        return Integer.MAX_VALUE;
    }

    // ---- hotkey ---------------------------------------------------------------------------------------------------------

    /** Client key code of the Ground Items hotkey (SimpleKeyboardMonitor: shift 81, ctrl 82, alt 86). */
    static int hotkeyCode() {
        String key = get("grounditems.hotkey");
        if (key.equals("SHIFT")) {
            return 81;
        }
        if (key.equals("CTRL")) {
            return 82;
        }
        return 86;
    }
}
