import java.util.ArrayList;
import java.util.HashMap;
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
    /** RuneLite's double config items (CameraConfig.cameraSpeed). */
    static final int DECIMAL = 14; // 2026-10-16 merge: Bruine Bonen took 4 for LIST (types are client-only)
    static final int LIST = 4;
    /** v3: a key binding (RspsKeyBind text, e.g. "CTRL+SHIFT+F6" or "NONE"), recorded with a key press in the sidebar. */
    static final int KEY = 5;
    /** v3: a read-only line the sidebar fills live (e.g. the PK last target); never stored, never sent to the server. */
    static final int INFO = 6;
    /** v3: the Gear Switcher's set editor (RspsGearSetEditor); the sets live in the client, never sent to the server. */
    static final int GEARSETS = 7;
    /** v3.1: a PK preset's own item ids (RspsPresetItemsRow); client only, like the gear sets. */
    static final int PRESETITEMS = 8;
    /** v3.2: one compact PK preset card (RspsPresetCard): on/off, key, gear or items, prayers, last-target step. Not stored. */
    static final int PRESETCARD = 9;
    /** v3.2: one compact spell row (RspsSpellRow): spell, key, cast/select. Not stored. */
    static final int SPELLROW = 10;
    /** v4: the Presets block (RspsProfilesPanel): whole-setup profiles, client only. */
    static final int PROFILES = 11;
    /** v4: Template + Wipe for the "Add to" script (RspsScriptTools). */
    static final int SCRIPTTOOLS = 12;
    /** v4: one script box (RspsScriptBox), default value = the script number. */
    static final int SCRIPTBOX = 13;

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
        /** v3.2: stored and synced like any item, but drawn by a card (PRESETCARD / SPELLROW) instead of its own row. */
        boolean hidden;

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
        // PK Bots staff UI: visibility comes only from the server's botsstaff message.
        {"botpanel", "PK Bots (staff)", "Staff only: spawn and steer the server's PK bots"},
        {"boosts", "Boosts Information", "Show combat and/or skill boost information"},
        {"opponent", "Opponent Information", "Show name and hitpoints information about the NPC you are fighting"},
        {"grounditems", "Ground Items", "Highlight ground items and/or show price information"},
        {"timers", "Timers & Buffs", "Show various timers in an infobox"},
        // Owner 2026-09-30. RuneLite's Camera plugin keeps its old config group "zoom"; owner 2026-10-11: the whole plugin.
        {"zoom", "Camera", "Expands zoom limit, provides vertical camera, and remaps mouse input keys"},
        {"minimap", "Minimap", "Zoom the minimap"},
        {"loottracker", "Loot Tracker", "Tracks loot from monsters and minigames"},
        // Owner 2026-10-01: the resizable/fullscreen chatbox background (RspsChatbox).
        {"chatbox", "Chatbox", "Chatbox look, friend login/logout messages and private-message notifications"},
        // Owner 2026-10-07: the Quest Helper as a plugin (RuneLite Quest Helper: "An in-game interactive guide for quests").
        {"questhelper", "Quest Helper", "An in-game interactive guide for quests"},
        // Owner 2026-10-11 (bug 012046 "we need the runelite stretched plugin"): RuneLite's Stretched Mode (com.jagex.StretchedMode).
        {"stretchedmode", "Stretched Mode", "Stretches the game in fixed and resizable modes."},
        // 78Legacy Gear Switcher (RspsGearSwitcher, 2026-10-11).
        // v4 (user 2026-10-12): Gear Switcher + PK Hotkeys merged into one plugin, named by the user.
        {"pkhotkeys", "Bruine Bonen Technologia", "Gear switches, prayers, spells and attacks as scripts on any key; presets, target overlay"},
        // N610: RuneLite ToA Extended (@PluginDescriptor name "Toa Extended", enabledByDefault = false).
        {"toaextended", "Toa Extended", "Extended plugin for Tombs of Amascut."},
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

    private static void decimal(String plugin, String key, String name, String description, double def, String section) {
        add(plugin, key, DECIMAL, name, description, Double.toString(def), section, 0, null, null);
    }

    private static void keybind(String plugin, String key, String name, String description, String def, String section) {
        add(plugin, key, KEY, name, description, def, section, 0, null, null);
    }

    /** True for the types the server stores (KEY included); INFO and GEARSETS live only in the client. */
    static boolean stored(int type) {
        return type != LIST && type != INFO && type != GEARSETS && type != PRESETITEMS && type != PRESETCARD && type != SPELLROW
            && type != PROFILES && type != SCRIPTTOOLS && type != SCRIPTBOX;
    }

    /** v4: shows the item added last on [plugin]'s page while its key keeps its own prefix (gearswitcher.* on pkhotkeys). */
    private static void regroup(String plugin) {
        Setting s = SETTINGS.get(SETTINGS.size() - 1);
        Setting moved = new Setting(plugin, s.key, s.type, s.name, s.description, s.defaultValue, s.section, s.max, s.options, s.optionNames);
        moved.min = s.min;
        moved.hidden = s.hidden;
        SETTINGS.set(SETTINGS.size() - 1, moved);
    }

    /** v3.2: marks the item added last as drawn by a card. */
    private static void hide() {
        SETTINGS.get(SETTINGS.size() - 1).hidden = true;
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

        // RuneLite CameraConfig (group "zoom"), every item in RuneLite's order (owner 2026-10-11: the whole plugin).
        String z = "zoom";
        bool(z, "inner", "Expand inner zoom limit", "Configures whether or not the inner zoom limit is reduced.", false, null);
        integer(z, "outerLimit", "Expand outer zoom limit", "Configures how much the outer zoom limit is adjusted.", 0, -400, 400, null);
        bool(z, "relaxCameraPitch", "Expand pitch limit", "Relax the camera's upper and lower pitch limits. Allows vertical and horizontal camera movement.", false, null);
        choice(z, "controlFunction", "Control function", "Configures the zoom function when control is pressed.", "NONE",
            new String[]{"NONE", "CONTROL_TO_ZOOM", "CONTROL_TO_RESET"}, new String[]{"None", "Hold to zoom", "Reset zoom"}, null);
        integer(z, "ctrlZoomValue", "Reset zoom position", "Position of zoom when it is reset.", 512, -400, 1400, null);
        integer(z, "zoomIncrement", "Zoom speed", "Speed of zoom.", 25, 0, Integer.MAX_VALUE, null);
        decimal(z, "cameraSpeed", "Camera speed", "Speed which the camera moves from input.", 1.0, null);
        bool(z, "disableCameraShake", "Disable camera shake", "Disables camera shake.", false, null);
        String mouse = "Mouse settings";
        SECTION_DESCRIPTIONS.put(mouse, "Mouse settings.");
        bool(z, "rightClickMovesCamera", "Right-click moves camera", "Remaps right-click to middle mouse click.", false, mouse);
        bool(z, "rightClickObjects", "Right-click objects", "Right-clicking objects opens the menu when 'Right-click moves camera' is on.", true, mouse);
        bool(z, "rightClickExamine", "Right-click examine", "Right-clicking examinable objects opens the menu when 'Right-click moves camera' is on.", false, mouse);
        bool(z, "rightClickMenuBlocksCamera", "Right-click menu blocks camera", "Prevents camera movement when 'Right-click moves camera' is on and the right-click menu<br>"
            + "is opened due to either 'Right-click objects' or 'Right-click examine' being on.", true, mouse);
        bool(z, "middleClickMenu", "Middle-button opens menu", "Remaps middle mouse click to right-click.", false, mouse);
        bool(z, "invertYaw", "Invert yaw", "Makes moving the camera horizontally with the mouse backwards.", false, mouse);
        bool(z, "invertPitch", "Invert pitch", "Makes moving the camera vertically with the mouse backwards.", false, mouse);

        // RuneLite MinimapConfig: "Zoom".
        bool("minimap", "zoom", "Zoom", "Enables zooming on the minimap.", true, null);

        // RuneLite LootTrackerConfig (the ignore lists are the panel's own right-click actions here).
        bool("chatbox", "transparent", "Transparent chatbox",
            "Resizable and fullscreen: see the game through the chatbox. Off = the opaque parchment chatbox of fixed mode.", true, null);
        // A33 (owner 2026-10-11): the chat options in the one place the player looks (OSRS Settings > Chat / RuneLite names).
        bool("chatbox", "friendLogin", "Friend login/logout messages", "Show 'Name has logged in.' / 'has logged out.' for your friends.", true, null);
        bool("chatbox", "timestamps", "Chat timestamps", "Show the time each message arrived in front of it, [HH:mm] (local time).", false, null);
        bool("chatbox", "notifyOnPM", "Notify on private message", "Notifies you whenever you receive a private message while the game window is not active.", false, null);

        // RuneLite QuestHelperConfig items this helper has (the server ClientHudConfig keeps the same keys).
        String q = "questhelper";
        bool(q, "autoStartQuests", "Auto start helpers", "Automatically start the quest helper when you start a quest.", true, null);
        bool(q, "showOverlayPanel", "Display overlay on screen", "Show the panel with the current step in the top-left corner of the game.", true, null);
        bool(q, "showMiniMapArrow", "Display arrows", "Show the hint arrow over the next npc or place, and on the minimap.", true, null);
        bool(q, "showStepMessages", "Step messages in chat", "Write each new quest step in the chatbox.", true, null);

        // RuneLite StretchedModeConfig (group "stretchedmode"): the official keys, names, descriptions and defaults, word for word
        // (owner 2026-10-11 "net als de officiele versie"; the afternoon's own fixed-mode meanings were wrong).
        String st = "stretchedmode";
        bool(st, "keepAspectRatio", "Keep aspect ratio", "Keeps the aspect ratio when stretching.", false, null);
        bool(st, "increasedPerformance", "Increased performance mode", "Uses a fast algorithm when stretching, lowering quality but increasing performance.", false, null);
        bool(st, "integerScaling", "Integer scaling", "Forces use of a whole number scale factor when stretching.", false, null);
        // No @Range on RuneLite's scalingFactor: its spinner runs 0 .. Integer.MAX_VALUE (the 100 cap blocked 200%, night run 2026-10-12).
        // N460 (owner 2026-10-31, choice B): fixed stays as it is; the tooltip tells the player that resizable is the mode where the
        // slider makes only the chatbox, side panel and orbs bigger while the world keeps filling the window.
        integer(st, "scalingFactor", "Resizable scaling", "In resizable mode, the game is reduced in size this much before it's stretched:<br>the chatbox, side panel and orbs get bigger, the world still fills the window.<br>In fixed mode, the whole game is enlarged this much, up to the window size.<br>For a bigger chatbox, inventory and orbs, use resizable mode.", 50, Integer.MAX_VALUE, null);

        // v4 (user 2026-10-12, layout version C): Gear Switcher + PK Hotkeys are ONE plugin page ("Bruine Bonen Technologia",
        // plugin id pkhotkeys). The stored keys keep their names (gearswitcher.* / pkhotkeys.*), so nobody loses a binding.
        String pk = "pkhotkeys";
        // Presets (UG's blue Preset / green Save buttons): whole-setup profiles, client only.
        add(pk, "profiles", PROFILES, "Presets", "Load or save your whole setup: scripts and keys.", "", "Presets", 0, null, null);
        // v5 (user 2026-10-12, layout version D): Scripts first, each with its own key in its header (Hotkey 1-15 are no
        // longer drawn under Hotkeys), then the PK HUD, Hotkeys, Target Overlay and Settings.
        String sc = "Scripts";
        bool(pk, "rightClickSetup", "[Easy Setup] Right Click Setup", "Right-click an item or prayer: 'Add Wear / Prayer ON / Prayer OFF to Script N' (N = Add to).", true, sc);
        String[] nums = new String[RspsScripts.COUNT];
        String[] numNames = new String[RspsScripts.COUNT];
        for (int n = 1; n <= RspsScripts.COUNT; n++) {
            nums[n - 1] = Integer.toString(n);
            numNames[n - 1] = "Script " + n;
        }
        choice(pk, "addTo", "Add to", "The script that right-click setup, Template and Wipe work on.", "1", nums, numNames, sc);
        add(pk, "scriptTools", SCRIPTTOOLS, "Template", "Templates and wipe for the script chosen at 'Add to'.", "", sc, 0, null, null);
        for (int n = 1; n <= RspsScripts.COUNT; n++) {
            add(pk, "script" + n, SCRIPTBOX, "Script " + n, "Script " + n + ": its steps, in order.", Integer.toString(n), sc, 0, null, null);
        }
        // v5 PK HUD (RspsPkHud): information only - it never clicks. The server switch allowCombatHints turns C/D/E off.
        String hud = "PK HUD";
        bool(pk, "showStatusPanel", "Show PK HUD", "A panel in the game: your target (health, overhead prayer, weapon), your special energy, prayer and attack timer, the last script.", true, hud);
        bool(pk, "hudHitWith", "'Hit with' hint", "Shows the attack styles your target does NOT protect against (his overhead prayer).", true, hud);
        bool(pk, "hudWeapon", "Target weapon + style", "Shows the weapon your target holds and its style (staff = Magic, crossbow = Ranged, whip = Melee).", true, hud);
        bool(pk, "hudSpecWarning", "Special weapon warning", "Warns when your target holds a special attack weapon such as dragon claws.", true, hud);
        bool(pk, "hudPrayHighlight", "Highlight protect prayer", "Outlines the protect prayer against your target's weapon in your prayer tab. You click it yourself.", true, hud);
        integer(pk, "hudLowHp", "Low HP warning (%)", "The panel turns red below this share of your hitpoints (0 = off). Also warns at low prayer points.", 50, 0, 99, hud);
        bool(pk, "hudAttackTimer", "Attack timer", "Dots until your next attack: whip 4, staff 5, crossbow 6 ticks.", true, hud);
        bool(pk, "hudTargetFrozen", "Target frozen timer", "How long your target stays frozen.", true, hud);
        bool(pk, "hudFightSummary", "Summary after a fight", "After a fight against a player: damage, hits into his protection, how well you prayed, your biggest mistake.", true, hud);
        // Hotkeys (v4 Hotkey Config).
        String hk = "Hotkeys";
        keybind(pk, "bind.toggleKey", "Start/Stop plugin", "Switches the whole plugin on or off.", "NONE", hk);
        keybind(pk, "bind.cancelKey", "Cancel running script", "Stops a script that is still waiting for its gear clicks.", "NONE", hk);
        keybind(pk, "bind.clearTargetKey", "Clear target", "Forgets your last target.", "NONE", hk);
        keybind(pk, "bind.attackKey", "Attack target", "Attacks the last target (melee or ranged: whatever you wield).", "CTRL+F1", hk);
        keybind(pk, "bind.stepUnderKey", "Step under target", "Walks onto the tile your last target stands on (one 'Walk here' click on that tile).", "CTRL+F6", hk);
        for (int n = 1; n <= RspsScripts.COUNT; n++) {
            // Drawn in the script's header (RspsScriptBox), skipped in this section by RspsSidebar.
            keybind(pk, "bind.script" + n + "Key", "Hotkey " + n, "Runs script " + n + ".", RspsScriptRunner.DEFAULT_KEYS[n - 1], hk);
        }
        // Target Overlay.
        String to = "Target Overlay";
        add(pk, "targetInfo", INFO, "Target", "The last player you attacked or cast on: target steps act on this player only.", "", to, 0, null, null);
        bool(pk, "showTargetBox", "Show target clickbox", "Draws a box around your target in the game.", true, to);
        colour(pk, "targetBoxColour", "Clickbox color", "Colour of the box and name around your target.", "00ffff", to);
        bool(pk, "fillTargetBox", "Fill clickbox", "Fills the target box with a see-through colour.", false, to);
        bool(pk, "showTargetName", "Show target name", "Writes your target's name above the box.", true, to);
        // Settings (v4 Main Config).
        String mc = "Settings";
        bool(pk, "ignoreDuplicateStarts", "Ignore duplicate starts", "Pressing a script's key again while it still runs does nothing.", true, mc);
        bool(pk, "setTargetRightClick", "Set target (right click)", "Adds 'Set target' to the right-click menu of other players.", true, mc);
        bool(pk, "chatMessages", "Chat messages", "Write in the chatbox what a key did or did not do.", true, mc);
        choice(pk, "chatLevel", "Chat detail", "Errors only: just what went wrong (no target, no food, ...). All: also what was switched. The same line is never repeated within 3 seconds.", "ERRORS",
            new String[]{"ERRORS", "ALL"}, new String[]{"Errors only", "All"}, mc);
        choice(pk, "accentColour", "Accent colour", "Colour of this page's section titles and buttons.", "ORANGE",
            new String[]{"ORANGE", "BLUE", "GREEN", "PURPLE"}, new String[]{"Orange", "Blue", "Green", "Purple"}, mc);
        // Quick keys: one action per key (prayers, special, food, potions).
        String bk = "Quick keys";
        keybind(pk, "bind.protectMagicKey", "Protect from Magic", "Turns Protect from Magic (Deflect Magic with curses) on or off.", "SHIFT+F6", bk);
        keybind(pk, "bind.protectMissilesKey", "Protect from Missiles", "Turns Protect from Missiles (Deflect Missiles) on or off.", "SHIFT+F7", bk);
        keybind(pk, "bind.protectMeleeKey", "Protect from Melee", "Turns Protect from Melee (Deflect Melee) on or off.", "SHIFT+F8", bk);
        keybind(pk, "bind.pietyKey", "Piety / Turmoil", "Turns Piety (Turmoil with curses) on or off.", "CTRL+SHIFT+F6", bk);
        keybind(pk, "bind.rigourKey", "Rigour", "Turns Rigour on or off (normal prayer book).", "CTRL+SHIFT+F7", bk);
        keybind(pk, "bind.auguryKey", "Augury", "Turns Augury on or off (normal prayer book).", "CTRL+SHIFT+F8", bk);
        keybind(pk, "bind.quickPrayersKey", "Quick prayers", "Turns your quick prayers on or off (like a click on the orb).", "CTRL+SHIFT+F9", bk);
        keybind(pk, "bind.specialKey", "Special attack", "Turns the special attack bar on or off.", "SHIFT+F9", bk);
        keybind(pk, "bind.vengeanceKey", "Vengeance", "Casts Vengeance (Lunar spellbook).", "CTRL+SHIFT+F10", bk);
        keybind(pk, "bind.eatKey", "Eat", "Eats one food from your inventory.", "SHIFT+F10", bk);
        bool(pk, "comboEat", "Combo eat", "The eat key also eats a cooked karambwan in the same tick, like a manual combo eat.", false, bk);
        keybind(pk, "bind.brewKey", "Saradomin brew", "Drinks a Saradomin brew (lowest dose first).", "SHIFT+F11", bk);
        keybind(pk, "bind.restoreKey", "Restore", "Drinks a super restore or prayer potion (lowest dose first).", "SHIFT+F12", bk);

        // v3.2 settings kept hidden (never drawn, never dispatched): the client turns them into scripts once
        // (RspsScriptRunner.migrate) and the server moves their keys to the script keys (ClientHudConfig migrateV4).
        String old = "Legacy";
        String[] sp = RspsPkData.SPELL_IDS;
        String[] spn = RspsPkData.SPELL_NAMES;
        String[][] spellDefaults = {{"A", "ICE_BARRAGE", "CTRL+F2"}, {"B", "BLOOD_BARRAGE", "CTRL+F3"}, {"C", "ICE_BLITZ", "CTRL+F4"}, {"D", "TELEPORT_BLOCK", "CTRL+F5"}};
        for (String[] d : spellDefaults) {
            choice(pk, "spell" + d[0], "Spell " + d[0], "v3 spell " + d[0] + ".", d[1], sp, spn, old);
            hide();
            keybind(pk, "bind.spell" + d[0] + "Key", "Spell " + d[0] + " key", "v3 spell key " + d[0] + ".", d[2], old);
            hide();
            choice(pk, "spell" + d[0] + "Mode", "Spell " + d[0] + " mode", "v3 spell mode " + d[0] + ".", "TARGET", RspsPkHotkeys.SPELL_MODES, RspsPkHotkeys.SPELL_MODE_NAMES, old);
            hide();
        }
        String[] pn = RspsPkData.withNone(RspsPkData.PRAYER_IDS, "NONE");
        String[] pnn = RspsPkData.withNone(RspsPkData.PRAYER_NAMES, "None");
        for (int n = 1; n <= RspsPkHotkeys.PRESETS; n++) {
            String[] d = RspsPkHotkeys.PRESET_DEFAULTS[n - 1]; // {key, gear, prayer1, prayer2, toggle, target, targetDo, spell}
            bool(pk, "preset" + n + "On", "On", "v3.2 preset " + n + ".", true, old);
            hide();
            keybind(pk, "bind.preset" + n + "Key", "Key", "v3.2 preset key " + n + ".", d[0], old);
            hide();
            choice(pk, "preset" + n + "Gear", "Gear set", "v3.2", d[1], RspsPkHotkeys.PRESET_GEAR, RspsPkHotkeys.PRESET_GEAR_NAMES, old);
            hide();
            choice(pk, "preset" + n + "PrayerOne", "Prayer 1", "v3.2", d[2], pn, pnn, old);
            hide();
            choice(pk, "preset" + n + "PrayerTwo", "Prayer 2", "v3.2", d[3], pn, pnn, old);
            hide();
            bool(pk, "preset" + n + "PrayerToggle", "Prayer on/off", "v3.2", Boolean.parseBoolean(d[4]), old);
            hide();
            bool(pk, "preset" + n + "Target", "Last target", "v3.2", Boolean.parseBoolean(d[5]), old);
            hide();
            choice(pk, "preset" + n + "TargetDo", "Target step", "v3.2", d[6], RspsPkHotkeys.TARGET_DO, RspsPkHotkeys.TARGET_DO_NAMES, old);
            hide();
            choice(pk, "preset" + n + "Spell", "Spell", "v3.2", d[7], sp, spn, old);
            hide();
        }

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

    /** N610: RuneLite ToA Extended (ca.plugins.toaextended.ToaExtendedConfig) - the 12 sections, all closedByDefault. */
    static final String[] TOAEXT_SECTIONS = {"Zebak", "Kephri", "Baba", "Akkha", "Warden Phase 2", "Warden Phase 3", "Scabaras Challenge", "Apmeken Challenge", "Het Challenge", "Prayer", "Recolor Sarcophagus", "Miscellaneous"};

    static {
        // N610 (owner 13-11 'toa extended plugin van runelite volledig geen namaak maar exact'): the 114 ConfigItems of
        // ToaExtendedConfig, same keyName/name/description/default/section; generated by work/claude-scripts/toaext_settings_gen.py.
        SECTION_DESCRIPTIONS.put("toaextended/Zebak", "Zebak configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Kephri", "Kephri configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Baba", "Baba configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Akkha", "Akkha configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Warden Phase 2", "Warden phase 2 configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Warden Phase 3", "Warden phase 3 configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Scabaras Challenge", "Scabaras configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Apmeken Challenge", "Apmeken configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Het Challenge", "Het configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Prayer", "Prayer configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Recolor Sarcophagus", "Sarcophagus recolor configuration.");
        SECTION_DESCRIPTIONS.put("toaextended/Miscellaneous", "Miscellaneous configurations.");
        String x = "toaextended";
        bool(x, "zebakPrayerIndicator", "Prayer Overlay", "Enable prayer overlays for Zebak.<br>Requires prayer mode config to be set.", false, "Zebak");
        bool(x, "zebakHealthCounter", "Health Counter", "Overlay hp until next phase.<br>Requires boss hp bar in-game setting turned on.", false, "Zebak");
        choice(x, "zebakBloodCloudTile", "Blood Cloud Tile", "Highlight blood cloud tiles.", "OFF", new String[]{"TILE", "TRUE_TILE", "OFF"}, new String[]{"Tile", "True Tile", "Off"}, "Zebak");
        choice(x, "zebakWaveTile", "Wave Tile", "Highlight wave tiles.", "OFF", new String[]{"TILE", "TRUE_TILE", "OFF"}, new String[]{"Tile", "True Tile", "Off"}, "Zebak");
        bool(x, "zebakProjectileTiles", "Projectile Tile", "Highlight where rocks, jugs, and poison will land.", false, "Zebak");
        bool(x, "zebakBloodMagicOutline", "Blood Magic Outline", "Outline blood magic for visibility.", false, "Zebak");
        bool(x, "zebakJugOutline", "Jug Outline", "Outline jugs.", false, "Zebak");
        bool(x, "zebakBoulderOutline", "Boulder Outline", "Outline boulders.", false, "Zebak");
        bool(x, "zebakHideWaves", "Hide Wave", "Prevent rendering waves.<br>Use with wave tile highlighting.", false, "Zebak");
        choice(x, "kephriAttackCounter", "Attack Counter", "Overlay Kephri with an attack counter.", "OFF", new String[]{"MEDIC", "DEFAULT", "OFF"}, new String[]{"Medic", "Default", "Off"}, "Kephri");
        choice(x, "kephriFireballTiles", "Fireball Tile", "Highlight where fireball projectiles will land.<br>Includes bomber scarabs.", "OFF", new String[]{"AERIAL", "DEFAULT", "OFF"}, new String[]{"Aerial", "Default", "Off"}, "Kephri");
        bool(x, "kephriEggTiles", "Egg Tile", "Highlight egg explosion radius tiles.", false, "Kephri");
        bool(x, "kephriDungOutline", "Dung Outline", "Outline Kephri and player when targetted by dung attack.", false, "Kephri");
        bool(x, "kephriOverlordOutline", "Overlord Outline", "Outline overlord scarabs.", false, "Kephri");
        bool(x, "kephriScarabSwarmOutline", "Swarm Outline", "Outline scarab swarms.", false, "Kephri");
        bool(x, "kephriHideFireballProjectile", "Hide Fireball", "Prevent rendering fireballs.<br>Use with fireball tile highlighting.", false, "Kephri");
        bool(x, "kephriHideBomberScarabProjectile", "Hide Bomber Scarab", "Prevent rendering bomber scarabs.<br>Use with fireball tile highlighting.", false, "Kephri");
        bool(x, "kephriHideUnattackableScarabSwarm", "Hide Unattackable Swarm", "Prevent rendering unattackable swarms.", false, "Kephri");
        bool(x, "kephriHideAgileScarabNpc", "Hide Agile Scarab", "Prevent rendering agile scarabs.", false, "Kephri");
        bool(x, "kephriHideAgileScarabProjectile", "Hide Agile Scarab Projectile", "Prevent rendering agile scarab projectiles.", false, "Kephri");
        bool(x, "babaHealthCounter", "Health Counter", "Overlay hp until next phase.<br>Requires boss hp bar in-game setting turned on.", false, "Baba");
        choice(x, "babaTile", "Baba Tile", "Highlight Baba's tile.", "OFF", new String[]{"TILE", "TRUE_TILE", "OFF"}, new String[]{"Tile", "True Tile", "Off"}, "Baba");
        bool(x, "babaShockwaveTiles", "Shockwave Tile", "Highlight where shockwaves will hit.", false, "Baba");
        bool(x, "babaNonWeakenedRollingBoulderTiles", "Rolling Boulder Tile", "Highlight non-weakened rolling boulders.", false, "Baba");
        bool(x, "babaFallingBoulderTiles", "Falling Boulder Tile", "Highlight where falling boulders will land.", false, "Baba");
        bool(x, "babaSarcophagusTiles", "Sarcophagus Projectile Tile", "Highlight where projectiles will land.", false, "Baba");
        bool(x, "babaRubbleTiles", "Rubble Tile", "Highlight perimiter of rubble.", false, "Baba");
        bool(x, "babaBananaPeelTiles", "Banana Peel Tile", "Highlight banana peels.", false, "Baba");
        bool(x, "babaBabaSpecOutline", "Boulder Throw Outline", "Outline Baba when she throws a boulder.", false, "Baba");
        bool(x, "babaBaboonOutline", "Baboon Outline", "Outline baboons.", false, "Baba");
        bool(x, "babaHideNonWeakenedRollingBoulders", "Hide Rolling Boulder", "Prevent rendering non-weakened boulders.<br>Use with rolling boulder tile highlighting.", false, "Baba");
        bool(x, "babaHideRollingBoulderProjectiles", "Hide Rolling Boulder Projectile", "Prevent rendering rolling boulder projectiles.", false, "Baba");
        bool(x, "babaHideFallingBoulders", "Hide Falling Boulder", "Prevent rendering falling boulders.<br>Use with falling boulder tile highlighting.", false, "Baba");
        bool(x, "babaHideBaboonProjectiles", "Hide Baboon Projectile", "Prevent rendering baboon projectiles.", false, "Baba");
        bool(x, "akkhaPrayerIndicator", "Prayer Overlay", "Enable prayer overlays for Akkha.<br>Requires prayer mode config to be set.", false, "Akkha");
        bool(x, "akkhaHealthCounter", "Health Counter", "Overlay hp until next phase.<br>Requires boss hp bar in-game setting turned on.", false, "Akkha");
        choice(x, "akkhaTile", "Akkha Tile", "Highlight Akkha's tile with color indicating attack style.", "OFF", new String[]{"TILE", "TRUE_TILE", "OFF"}, new String[]{"Tile", "True Tile", "Off"}, "Akkha");
        bool(x, "akkhaSpecialAttackOutline", "Special Attack Outline", "Outline Akkha when a special attack is imminent.<br>Trailing orbs or memory blast.", false, "Akkha");
        bool(x, "akkhaAttackTickCounter", "Attack Tick Counter", "Overlay ticks until Akkha's next attack on player.<br>Step back invocation.", false, "Akkha");
        bool(x, "akkhaMemoryBlastTracker", "Memory Blast Tracker", "Highlight the memorized tiles.", false, "Akkha");
        bool(x, "akkhaUnstableOrbRadius", "Unstable Orb Radius", "Highlight radius of unstable orbs.", false, "Akkha");
        integer(x, "akkhaRadiusDistance", "Radius Distance", "How many tiles to highlight.", 3, 0, 7, "Akkha");
        choice(x, "akkhaUnstableOrbTiles", "Unstable Orb Tile", "Highlight unstable orbs.", "OFF", new String[]{"TILE", "TRUE_TILE", "OFF"}, new String[]{"Tile", "True Tile", "Off"}, "Akkha");
        bool(x, "akkhaHideUnstableOrbs", "Hide Unstable Orbs", "Prevent rendering unstable orbs.<br>Use with unstable orb highlighting.", false, "Akkha");
        bool(x, "wardenP2PrayerIndicator", "Prayer Overlay", "Enable prayer overlays for Wardens.<br>Requires prayer mode config to be set.", false, "Warden Phase 2");
        bool(x, "wardenP2HealthCounter", "Health Counter", "Overlay hp until next phase.<br>Requires boss hp bar in-game setting turned on.", false, "Warden Phase 2");
        bool(x, "wardenCoreTickTimer", "Warden Core Tick Timer", "Overlay warden core with a tick timer.", false, "Warden Phase 2");
        bool(x, "wardenTile", "Warden Tile", "Highlight warden's tile.", false, "Warden Phase 2");
        bool(x, "wardenCoreTile", "Warden Core Tile", "Highlight warden core tile.", false, "Warden Phase 2");
        bool(x, "wardenBlackSkullProjectileTile", "Black Skull Projectile Tile", "Highlight black skull projectile.", false, "Warden Phase 2");
        bool(x, "wardenLightningProjectileTile", "Lightning Projectile Tile", "Highlight obelisk's lightning projectile.", false, "Warden Phase 2");
        bool(x, "wardenHideLightningTiles", "Hide Lightning Tile", "Prevent rendering lightning gfx objects.<br>Use with lightning tile highlighting.", false, "Warden Phase 2");
        bool(x, "wardenHideLightningProjectiles", "Hide Lightning Projectile", "Prevent rendering lightning projectiles.<br>Use with lightning tile highlighting.", false, "Warden Phase 2");
        bool(x, "wardenP3PrayerIndicator", "Prayer Overlay", "Enable prayer overlays for Wardens.<br>Requires prayer mode config to be set.", false, "Warden Phase 3");
        bool(x, "wardenP3HealthCounter", "Health Counter", "Overlay hp until next phase.<br>Requires boss hp bar in-game setting turned on.", false, "Warden Phase 3");
        bool(x, "wardenSlamIndicator", "Warden Slam Indicator", "Indicate where to stand when slamming starts/resumes.<br>Insanity invocation.", false, "Warden Phase 3");
        bool(x, "wardenEnergySiphonTickTimer", "Siphon Tick Timer", "Overlay energy siphons with a tick timer.<br>Solo and Insanity invocation.", false, "Warden Phase 3");
        bool(x, "wardenEnergySiphonProjectileTile", "Siphon Projectile Tile", "Highlight energy siphon projectiles.", false, "Warden Phase 3");
        bool(x, "wardenBabaFallingBoulderTile", "Boulder Tile", "Highlight Phantom Baba's falling boulder.", false, "Warden Phase 3");
        choice(x, "wardenKephriFireballTile", "Fireball Tile", "Highlight Phantom Kephri's fireball.", "OFF", new String[]{"AERIAL", "DEFAULT", "OFF"}, new String[]{"Aerial", "Default", "Off"}, "Warden Phase 3");
        bool(x, "wardenRedLightningTiles", "Red Lightning Tile", "Highlight red lightning gfx objects.<br>Insanity phase.", false, "Warden Phase 3");
        bool(x, "wardenHideTileDebrisProjectile", "Hide Tile Debris Projectile", "Prevent rendering tile debris projectiles.<br>Insanity phase.", false, "Warden Phase 3");
        bool(x, "wardenHideEnergySiphonProjectile", "Hide Siphon Projectile", "Prevent rendering energy siphon projectiles.", false, "Warden Phase 3");
        bool(x, "wardenHideEnergySiphonChargeProjectiles", "Hide Charge Projectile", "Prevent rendering energy siphon charge projectiles.", false, "Warden Phase 3");
        bool(x, "wardenHideDeadEnergySiphon", "Hide Dead Siphon", "Prevent rendering dead energy siphons.", false, "Warden Phase 3");
        bool(x, "wardenHideBabaFallingBoulders", "Hide Baba Boulder", "Prevent rendering Phantom Baba's falling boulders.<br>Use with boulder tile highlighting.", false, "Warden Phase 3");
        bool(x, "wardenHideKephriFireballProjectile", "Hide Kephri Fireball", "Prevent rendering Phantom Kephri's fireballs.<br>Use with fireball tile highlighting.", false, "Warden Phase 3");
        bool(x, "wardenHideZebakProjectile", "Hide Zebak Projectile", "Prevent renndering Zebak's range and mage projectiles.<br>Use with prayer overlay.", false, "Warden Phase 3");
        bool(x, "wardenHideRedLightning", "Hide Red Lightning", "Prevent rendering red lightning gfx objects.<br>Insanity phase. Use with red lighting tile highlighting.", false, "Warden Phase 3");
        bool(x, "scabarasAdditionPuzzle", "Addition Puzzle Solver", "Toggle the addition puzzle solver.", false, "Scabaras Challenge");
        bool(x, "scabarasLightPuzzle", "Light Puzzle Solver", "Toggle the light puzzle solver.", false, "Scabaras Challenge");
        bool(x, "scabarasSequencePuzzle", "Sequence Puzzle Solver", "Toggle the sequence puzzle solver.", false, "Scabaras Challenge");
        bool(x, "scabarasObeliskPuzzle", "Obelisk Puzzle Solver", "Toggle the obelisk puzzle solver.", false, "Scabaras Challenge");
        bool(x, "scabarasObeliskFallingRocksHighlight", "Obelisk Falling Rocks Tile", "Outline tile of falling rocks.", false, "Scabaras Challenge");
        bool(x, "scabarasMatchingPuzzle", "Matching Puzzle Solver", "Toggle the matching puzzle solver.", false, "Scabaras Challenge");
        bool(x, "scabarasHighlightEntrance", "Highlight Entrance", "Highlight the entrance to skip obelisks.", false, "Scabaras Challenge");
        bool(x, "apmekenBaboonOutline", "Baboon Outline", "Highlight baboons.", false, "Apmeken Challenge");
        bool(x, "apmekenVolatileBaboonTiles", "Volatile Baboon Tile", "Highlight the tiles of the explode radius.", false, "Apmeken Challenge");
        bool(x, "apmekenRoofVentOutline", "Roof/Vent Outline", "Outline vents and roof support.", false, "Apmeken Challenge");
        bool(x, "apmekenRepairMenuEntry", "Repair Roof Menu Entry", "Deprioritize menu entry when inactive.", false, "Apmeken Challenge");
        bool(x, "hetDepositPickaxe", "Deposit-Pickaxe", "Automatically swap to Deposit-pickaxe when a pickaxe is in your inventory.", false, "Het Challenge");
        bool(x, "hetCasterStatueBeamTimer", "Caster Statue Beam Timer", "Overlay caster statue with a count down timer.", false, "Het Challenge");
        bool(x, "hetMirrorOutline", "Mirror Outline", "Highlight mirrors you can pick up.", false, "Het Challenge");
        bool(x, "hetOrbOfDarknessTiles", "Orb of Darkness Tile", "Highlight orb of darkness tiles.", false, "Het Challenge");
        bool(x, "hetHideOrbsOfDarkness", "Hide Orbs of Darkness", "Prevent rendering orbs of darkness.", false, "Het Challenge");
        choice(x, "prayerMode", "Prayer Mode", "Types of overlays to use to indicate prayers.", "OFF", new String[]{"WIDGET", "INFO_BOX", "ALL", "OFF"}, new String[]{"Widget", "Infobox", "All", "Off"}, "Prayer");
        bool(x, "prayerDescendingBoxes", "Descending Boxes (Guitar Hero)", "Overlay descending boxes on prayer widgets.", false, "Prayer");
        bool(x, "prayerNonPriority", "Non-Priority Boxes (Guitar Hero)", "Show boxes for all upcoming attacks.<br>Requires descending boxes to be enabled.", false, "Prayer");
        colour(x, "prayerBoxColor", "Box Color", "Color of boxes that have > 1 ticks remaining.", "ffc800", "Prayer");
        colour(x, "prayerBoxWarnColor", "Box Warning Color", "Color of boxes that have 1 tick remaining.", "ff0000", "Prayer");
        bool(x, "sarcophagusRecolorWhite", "Recolor White", "Recolor white sarcophagus.", false, "Recolor Sarcophagus");
        colour(x, "sarcophagusWhiteRecolor", "Color", "Color to replace white sarcophagus with.", "edb117", "Recolor Sarcophagus");
        bool(x, "sarcophagusRecolorMyPurple", "Recolor Purple (Mine)", "Recolor purple sarcophagus.<br>When the sarcophagus is my loot.", false, "Recolor Sarcophagus");
        colour(x, "sarcophagusMyPurpleRecolor", "Color", "Color to replace purple sarcophagus with.<br>When the sarcophagus is my loot.", "c0147c", "Recolor Sarcophagus");
        bool(x, "sarcophagusRecolorOtherPurple", "Recolor Purple (Other)", "Recolor purple sarcophagus.<br>When the sarcophagus is not my loot.", false, "Recolor Sarcophagus");
        colour(x, "sarcophagusOtherPurpleRecolor", "Color", "Color to replace purple sarcophagus with.<br>When the sarcophagus is not my loot.", "115898", "Recolor Sarcophagus");
        bool(x, "sarcophagusRecolorLoot", "Recolor Loot", "Recolor purple sarcophagus based on loot.", false, "Recolor Sarcophagus");
        colour(x, "sarcophagusLightbearerColor", "Lightbearer", "Color to use when loot is a lightbearer.", "ffff00", "Recolor Sarcophagus");
        colour(x, "sarcophagusElidinisWardColor", "Elidinis' Ward", "Color to use when loot is Elidinis' Ward.", "ffff00", "Recolor Sarcophagus");
        colour(x, "sarcophagusOsmumtensFangColor", "Osmumten's Fang", "Color to use when loot is Osmumten's Fang.", "ff8700", "Recolor Sarcophagus");
        colour(x, "sarcophagusMasoriMaskColor", "Masori Mask", "Color to use when loot is a Masori Mask.", "ff8700", "Recolor Sarcophagus");
        colour(x, "sarcophagusMasoriChapsColor", "Masori Chaps", "Color to use when loot is a Masori Chaps.", "ff0000", "Recolor Sarcophagus");
        colour(x, "sarcophagusMasoriBodyColor", "Masori Body", "Color to use when loot is a Masori Body.", "ff0000", "Recolor Sarcophagus");
        colour(x, "sarcophagusTumekensShadowColor", "Tumeken's Shadow", "Color to use when loot is Tumeken's Shadow.", "ff0000", "Recolor Sarcophagus");
        bool(x, "sarcophagusHideLoot", "Hide Loot", "Prevent rendering loot animation and chatbox text.<br>Only active when purple is mine.", false, "Recolor Sarcophagus");
        bool(x, "hideFadeTransition", "Hide Fade Transition", "Hide the room fade transitions.", false, "Miscellaneous");
        bool(x, "hideHpOrbs", "Hide HP Orbs", "Hide the HP orbs in the HUD.", false, "Miscellaneous");
        choice(x, "quickProceedSwaps", "Menu Swaps", "Menu entry swaps for entering/exiting etc.", "OFF", new String[]{"ON", "SPEEDRUN", "OFF"}, new String[]{"On", "Speedrun", "Off"}, "Miscellaneous");
        choice(x, "fontStyle", "Font Style", "Font style of most text overlays.", "BOLD", new String[]{"BOLD", "ITALIC", "PLAIN"}, new String[]{"Bold", "Italic", "Plain"}, "Miscellaneous");
        integer(x, "fontSize", "Font Size", "Font size of most text overlays.", 12, 12, Integer.MAX_VALUE, "Miscellaneous");
        colour(x, "dangerOutlineColor", "Danger Outline", "Color used to mark \"dangerous\" tiles.", "ff0000", "Miscellaneous");
        colour(x, "dangerFillColor", "Danger Fill", "Color used to mark \"dangerous\" tiles.", "14ff0000", "Miscellaneous");
        colour(x, "tileOutlineColor", "Tile Outline", "Color used to outline npc tiles.", "000000", "Miscellaneous");
        colour(x, "tileFillColor", "Tile Fill", "Color used to fill npc tiles.", "0a000000", "Miscellaneous");
    }

    static {
        resetToDefaults();
    }

    static synchronized void resetToDefaults() {
        // Server-only switches (pkhotkeys.allow*): the owner decides them, a local reset must not drop them.
        Map<String, String> serverOnly = new java.util.HashMap<String, String>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey().startsWith("pkhotkeys.allow")) {
                serverOnly.put(e.getKey(), e.getValue());
            }
        }
        values.clear();
        values.putAll(serverOnly);
        for (Setting setting : SETTINGS) {
            if (stored(setting.type)) {
                values.put(setting.key, setting.defaultValue);
            }
        }
        for (String[] plugin : PLUGINS) {
            values.put(plugin[0] + ".enabled", "true");
        }
        // RuneLite CameraPlugin: enabledByDefault = false.
        values.put("zoom.enabled", "false");
        // RuneLite StretchedModePlugin: disabled by default.
        values.put("stretchedmode.enabled", "false");
        // N610: ToaExtendedPlugin enabledByDefault = false.
        values.put("toaextended.enabled", "false");
        highlighted.clear();
        hidden.clear();
        highlightedNames.clear();
        hiddenNames.clear();
        // fresh memory (start / tests): no local change waits for a server confirmation
        if (pending != null) { // null while the static block runs (declared further down)
            pending.clear();
            pendingAt.clear();
        }
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

    /** A DECIMAL setting; [fallback] when it is not set (yet) or not a finite number. */
    static double decimal(String key, double fallback) {
        try {
            double value = Double.parseDouble(get(key));
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
        } catch (NumberFormatException notSet) {
            return fallback;
        }
    }

    static boolean is(String key, String option) {
        return option.equals(values.get(key));
    }

    static int colour(String key) {
        try {
            // N610: RuneLite @Alpha colours are stored as aarrggbb (ToA Extended dangerFillColor 14ff0000); rrggbb stays opaque.
            String v = get(key);
            if (v.length() == 8) {
                return (int) Long.parseLong(v, 16);
            }
            return 0xFF000000 | Integer.parseInt(v, 16);
        } catch (NumberFormatException notSet) {
            return 0xFFFFFFFF;
        }
    }

    static boolean enabled(String plugin) {
        // v4: the Gear Switcher is part of the pkhotkeys plugin; its own old switch no longer counts.
        if (plugin.equals("gearswitcher")) {
            plugin = "pkhotkeys";
        }
        // Owner 2026-10-16: Bruine Bonen Technologia (gear switcher, PK hotkeys, scripts) is for moderators only. The server
        // says who is staff at login ("botsstaff|1", BotPanel.onLogin: mod power); everyone else never runs or sees it.
        if (plugin.equals("pkhotkeys") && !RspsBotPanel.isStaff()) {
            return false;
        }
        return bool(plugin + ".enabled");
    }

    /** Local change (the sidebar sets it at once; the server's push confirms it). */
    static synchronized void put(String key, String value) {
        values.put(key, value);
        pending.put(key, value);
        pendingAt.put(key, System.currentTimeMillis());
        revision++;
        applyStretchedMode();
    }

    /**
     * Owner 2026-10-11 ("als ik em hoger zet zet de client de waardes automatisch hoger"): every step of a setting goes to the
     * server, which pushes all values back. A push answering an EARLIER step (51 while the player is already at 55) put the
     * old value back, the spinner jumped back and its arrows went on counting from there. A local change now wins over pushes
     * until the server confirms that very value (or [PENDING_MS] passed - a value the server refused is not kept forever).
     */
    private static final Map<String, String> pending = new HashMap<String, String>();
    private static final Map<String, Long> pendingAt = new HashMap<String, Long>();
    private static final long PENDING_MS = 3000;

    /** False while a newer local change of [key] waits for the server to confirm it. */
    private static boolean acceptPush(String key, String value) {
        String mine = pending.get(key);
        if (mine == null) {
            return true;
        }
        if (mine.equals(value) || System.currentTimeMillis() - pendingAt.get(key) > PENDING_MS) {
            pending.remove(key);
            pendingAt.remove(key);
            return true;
        }
        return false;
    }

    /** RuneLite StretchedModePlugin.updateConfig: hands the settings to the client's stretching (it lays the window out again). */
    static void applyStretchedMode() {
        boolean wasOn = com.jagex.StretchedMode.enabled();
        com.jagex.StretchedMode.configure(enabled("stretchedmode"), bool("stretchedmode.keepAspectRatio"), bool("stretchedmode.increasedPerformance"),
            bool("stretchedmode.integerScaling"), values.containsKey("stretchedmode.scalingFactor") ? integer("stretchedmode.scalingFactor") : 50);
        // Stretched Mode starts filling a fixed window this client had fitted to the game: the player's own window comes back
        // (RuneLite never shrank it). Settings changes never touch the window; Resizable scaling sizes the fixed stretch inside it
        // (N187/N188, owner 2026-10-25: "Resizable scaling moet OOK in fixed werken").
        if (!wasOn && com.jagex.StretchedMode.fillsWindow() && InterfaceManager.getWindowMode() == com.jagex.core.constants.WindowMode.FIXED) {
            InterfaceManager.restoreRememberedWindow();
        }
        saveClientSettings();
    }

    // ---- Stretched Mode in the client's own config (A2 round 4, Keurmeester A2 round 3 reason 2) -------------------------
    //
    // RuneLite keeps StretchedModeConfig in the client's config (its settings.properties), so its login screen is stretched
    // right after a start and a choice survives a restart before any login. Here the values lived only in memory and came back
    // with an account's push after login: a restarted client showed the login screen unstretched and forgot Keep aspect ratio /
    // scaling set before the restart (owner done-list 8 "alles wordt onthouden na uitloggen, client-herstart en dagen
    // offline"). The client now keeps the five Stretched keys in stretchedmode.properties in the same folder as its overlay
    // positions (RspsOverlayLayout.file); an account's own saved values still come with its login push and are remembered here
    // from then on, and a new account adopts these (cfgadopt).

    private static final String[] CLIENT_KEYS = {"stretchedmode.enabled", "stretchedmode.keepAspectRatio", "stretchedmode.increasedPerformance",
        "stretchedmode.integerScaling", "stretchedmode.scalingFactor"};
    /** Null until the client read its file at start: unit tests and tools never write a prefs folder. */
    private static java.io.File clientFile;
    private static String savedClient = "";
    private static volatile boolean clientApplyPending;

    /** RS2Loader, once at start (before the sidebar is made): reads the client's own Stretched Mode settings. */
    static synchronized void loadClientSettings() {
        java.io.File file = new java.io.File(RspsOverlayLayout.file().getParentFile(), "stretchedmode.properties");
        if (file.isFile()) {
            java.util.Properties properties = new java.util.Properties();
            try (java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8")) {
                properties.load(reader);
            } catch (Exception unreadable) {
                properties.clear();
            }
            for (String key : CLIENT_KEYS) {
                String value = properties.getProperty(key);
                if (value != null && validClientValue(key, value.trim())) {
                    values.put(key, value.trim());
                }
            }
        }
        clientFile = file;
        savedClient = clientSnapshot();
        revision++;
        clientApplyPending = true;
    }

    /** The game loop: once after the start-up read, the settings go to the stretching (the login screen is laid out with them). */
    static boolean takeClientApply() {
        boolean pending = clientApplyPending;
        clientApplyPending = false;
        return pending;
    }

    private static boolean validClientValue(String key, String value) {
        if (key.endsWith(".scalingFactor")) {
            try {
                return Integer.parseInt(value) >= 0;
            } catch (NumberFormatException bad) {
                return false;
            }
        }
        return value.equals("true") || value.equals("false");
    }

    private static String clientSnapshot() {
        StringBuilder text = new StringBuilder();
        for (String key : CLIENT_KEYS) {
            String value = values.get(key);
            if (value != null) {
                text.append(key).append('=').append(value).append('\n');
            }
        }
        return text.toString();
    }

    /** After every change: the client's file follows its Stretched values (only when they changed). */
    private static synchronized void saveClientSettings() {
        java.io.File file = clientFile;
        if (file == null) {
            return;
        }
        String now = clientSnapshot();
        if (now.equals(savedClient)) {
            return;
        }
        savedClient = now;
        java.io.File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        try (java.io.Writer writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8")) {
            writer.write("# 78 client: RuneLite Stretched Mode settings (kept by the client, like RuneLite's settings.properties)\n");
            writer.write(now);
        } catch (java.io.IOException unwritable) {
            // kept for this session; the account still has them on the server
        }
    }

    /** v4: the keys the server has sent this session (the one-time script migration waits for the old preset keys). */
    private static final java.util.Set<String> RECEIVED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** v4: true once every hidden v3.2 preset / Spell A-D setting has come from the server. */
    static boolean receivedLegacy() {
        for (Setting s : SETTINGS) {
            if ("Legacy".equals(s.section) && !RECEIVED.contains(s.key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A2 round 3 / N93 (server ClientHudConfig.login "cfgadopt"): the account has never set these Stretched Mode settings, so the
     * login push left them out and this client keeps its own values (RuneLite keeps Stretched Mode in the client's config - a new
     * player who switched it on at the login screen kept it on). They go to the server as ordinary changes, so the account
     * remembers them from now on. Only Stretched Mode keys are taken.
     */
    static synchronized void adopt(String keys) {
        for (String key : keys.split(",")) {
            String value = values.get(key);
            if (key.startsWith("stretchedmode.") && value != null) {
                RspsHud.command("hud set " + key + " " + value);
            }
        }
    }

    static synchronized void handle(String kind, String rest) {
        if (kind.equals("cfg")) {
            for (String pair : rest.split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && acceptPush(pair.substring(0, eq), pair.substring(eq + 1))) {
                    values.put(pair.substring(0, eq), pair.substring(eq + 1));
                    RECEIVED.add(pair.substring(0, eq));
                }
            }
            revision++;
            applyStretchedMode();
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
