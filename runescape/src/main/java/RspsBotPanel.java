import rsps.runelite.ColorScheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Staff panel v5.3: protocol model on the game thread; widgets and drafts on the EDT. */
final class RspsBotPanel extends JPanel {
    private static final Object LOCK = new Object();
    private static volatile boolean staff;
    private static long session, messageVersion, locationsRevision, routesRevision, definitionsRevision;
    private static boolean stateKnown, enabled, recording, routeKnown, subscribed;
    private static String pvpZone = "WILDERNESS";
    private static boolean pvpZoneKnown; // review R6: only servers that send bots|pvpzone support the setting
    private static String mode = "WILDERNESS_ONLY", route = "", arenaText = "No arena state yet",
        roamText = "No roam state yet", perfText = "No performance state yet", message = "";
    private static boolean arenaRunning;
    private static int alive, maximum, waypoints, roamGroups;
    private static String[] profiles = new String[0], locations = new String[0], routes = new String[0];
    private static final Map<String, List<String>> CATALOG_STAGING = new LinkedHashMap<String, List<String>>();
    private static List<Bot> bots = new ArrayList<Bot>(), staging;
    private static Map<String, Definition> definitions = new LinkedHashMap<String, Definition>(), definitionStaging;
    private static Settings settings;
    private static Options options;
    private static Options2 options2;
    private static boolean optsKnown, populationKnown, statsKnown;
    private static String statsText = "No stats state yet";
    private static Map<Integer, PopulationEntry> population = new LinkedHashMap<Integer, PopulationEntry>(), populationStaging;
    private static final ConcurrentLinkedQueue<Command> COMMANDS = new ConcurrentLinkedQueue<Command>();
    private static final int CATALOG_LIMIT = 1000;

    private static final class Bot {
        final String name, profile, task, position;
        final int level, hp;
        final boolean paused;
        Bot(String[] f) {
            name = f[1]; profile = f[2]; level = number(f[3], 0, 5);
            task = f[4]; hp = number(f[5], 0, 100);
            if (!safeText(name) || name.trim().isEmpty() || name.indexOf('|') >= 0
                || !safeText(profile) || !safeText(task)) throw new IllegalArgumentException("Invalid bot row");
            position = f.length >= 7 ? f[6] : "";
            if (!position.isEmpty()) {
                String[] xy = position.split(",", -1);
                if (xy.length != 2) throw new IllegalArgumentException("Invalid position");
                number(xy[0], 0, Integer.MAX_VALUE); number(xy[1], 0, Integer.MAX_VALUE);
            }
            paused = f.length == 8 && flag(f[7]);
        }
    }
    private static final class Definition {
        final String name, kit, steps;
        final boolean builtin;
        Definition(String value) {
            String[] f = value.split("~", -1);
            if (f.length != 4 || !name(f[0]) || !kit(f[1]) || !plain(f[2])
                || !("b".equals(f[3]) || "c".equals(f[3]))) throw new IllegalArgumentException("Invalid profile");
            name = f[0]; kit = f[1]; steps = f[2]; builtin = "b".equals(f[3]);
        }
    }
    private static final class Settings {
        final int max, gap, idle;
        final String loot, colour;
        final boolean fights, ignore, log;
        Settings(String[] f) {
            max = number(f[1], 1, 50); loot = f[2]; colour = f[3]; gap = number(f[4], 0, 126);
            fights = flag(f[5]); ignore = flag(f[6]); log = flag(f[7]); idle = number(f[8], 20, 6000);
            if (!("NONE".equals(loot) || "GEAR_DROP".equals(loot)) || !colour(colour))
                throw new IllegalArgumentException("Invalid settings");
        }
    }
    private static final class Options {
        final String combat, victimLoot;
        final int flee, scale;
        final boolean duels, chat, population, autoscale;
        Options(String[] f) {
            combat = f[1];
            if (!"max".equals(combat) && !"match".equals(combat)) number(combat, 3, 138);
            flee = number(f[2], 0, 90); duels = flag(f[3]); victimLoot = f[4];
            if (!"KEEP".equals(victimLoot) && !"DROP".equals(victimLoot)) throw new IllegalArgumentException("Invalid victim loot");
            chat = flag(f[5]); population = flag(f[6]); autoscale = flag(f[7]); scale = number(f[8], 20, 100);
        }
    }
    /** Second options line (2026-10-14): `bots|opts2|xp|npcdefense|fleetp`. */
    private static final class Options2 {
        final boolean xp, fleeTeleport;
        final String npcDefense;
        Options2(String[] f) {
            xp = flag(f[1]); npcDefense = f[2]; fleeTeleport = flag(f[3]);
            if (!"FIGHT".equals(npcDefense) && !"AVOID".equals(npcDefense) && !"OFF".equals(npcDefense)) throw new IllegalArgumentException("Invalid npc defence");
        }
    }
    private static final class PopulationEntry {
        final int id;
        final String description;
        PopulationEntry(String value) {
            String[] f = value.split("~", -1);
            if (f.length != 2 || !plain(f[1])) throw new IllegalArgumentException("Invalid population entry");
            id = number(f[0], 0, Integer.MAX_VALUE); description = f[1];
        }
        @Override public String toString() { return "#" + id + " " + description; }
    }
    private static final class Command {
        final long session;
        final String text;
        Command(long session, String text) { this.session = session; this.text = text; }
    }
    private static final class Snapshot {
        boolean staff, known, enabled, recording, routeKnown, arenaRunning, pvpZoneKnown;
        String mode, pvpZone, route, arena, roam, perf, message;
        int alive, maximum, waypoints, roamGroups;
        long messageVersion, locationsRevision, routesRevision, definitionsRevision;
        String[] profiles, locations, routes;
        List<Bot> bots;
        Map<String, Definition> definitions;
        Settings settings;
        Options options;
        Options2 options2;
        boolean optsKnown, populationKnown, statsKnown;
        String stats;
        List<PopulationEntry> population;
    }
    static boolean isStaff() { return staff; }
    static void reset() {
        synchronized (LOCK) { clear(); }
        RspsSidebar.refreshLater();
    }
    private static void clear() {
        staff = false; session++; COMMANDS.clear();
        stateKnown = enabled = recording = routeKnown = subscribed = arenaRunning = false;
        mode = "WILDERNESS_ONLY"; pvpZone = "WILDERNESS"; pvpZoneKnown = false; route = message = "";
        arenaText = "No arena state yet"; roamText = "No roam state yet"; perfText = "No performance state yet";
        alive = maximum = waypoints = roamGroups = 0; messageVersion = locationsRevision = routesRevision = definitionsRevision = 0; settings = null;
        profiles = new String[0]; locations = new String[0]; routes = new String[0];
        bots = new ArrayList<Bot>(); staging = null; CATALOG_STAGING.clear();
        definitions = new LinkedHashMap<String, Definition>(); definitionStaging = null;
        options = null; options2 = null; optsKnown = populationKnown = statsKnown = false; statsText = "No stats state yet";
        population = new LinkedHashMap<Integer, PopulationEntry>(); populationStaging = null;
    }
    /** Each catalog commits at end. Malformed rows cannot damage another pending catalog. */
    static void handle(String kind, String rest) {
        try {
            synchronized (LOCK) {
                if ("botsstaff".equals(kind)) {
                    if (!"0".equals(rest) && !"1".equals(rest)) return;
                    if ("0".equals(rest)) clear(); else staff = true;
                } else if ("bots".equals(kind) && staff) {
                    String[] f = rest.split("\\|", -1);
                    String type = f[0];
                    if ("state".equals(type) && f.length == 5) {
                        boolean on = flag(f[1]);
                        if (!"WILDERNESS_ONLY".equals(f[2]) && !"EVERYWHERE".equals(f[2])) return;
                        int count = number(f[3], 0, Integer.MAX_VALUE), max = number(f[4], 0, Integer.MAX_VALUE);
                        enabled = on; mode = f[2]; alive = count; maximum = max; stateKnown = true;
                    } else if ("pvpzone".equals(type) && f.length == 2) {
                        if (!"WILDERNESS".equals(f[1]) && !"DEADMAN".equals(f[1])) return;
                        pvpZone = f[1]; pvpZoneKnown = true;
                    } else if ("settings".equals(type) && f.length == 9) {
                        settings = new Settings(f);
                    } else if ("opts".equals(type) && f.length == 9) {
                        // Eight values plus opts; the bots prefix was removed before handle().
                        Options next = new Options(f); options = next; optsKnown = true;
                    } else if ("opts2".equals(type) && f.length == 4) {
                        options2 = new Options2(f);
                    } else if ("stats".equals(type) && f.length == 2 && plain(f[1]) && f[1].length() <= 200) {
                        statsText = f[1]; statsKnown = true;
                    } else if ("pop".equals(type)) {
                        if (!populationCatalog(f)) return;
                    } else if ("clear".equals(type) && f.length == 1) {
                        staging = new ArrayList<Bot>();
                    } else if ("bot".equals(type) && staging != null) {
                        if (f.length < 6 || f.length > 8) return;
                        if (staging.size() < CATALOG_LIMIT) staging.add(new Bot(f));
                    } else if ("end".equals(type) && f.length == 1 && staging != null) {
                        bots = staging; staging = null;
                    } else if ("profiles".equals(type) || "locations".equals(type) || "routes".equals(type)) {
                        if (!catalog(type, f)) return;
                    } else if ("ptext".equals(type)) {
                        if (f.length == 2 && "clear".equals(f[1])) {
                            definitionStaging = new LinkedHashMap<String, Definition>();
                        } else if (f.length == 3 && "add".equals(f[1]) && definitionStaging != null) {
                            try {
                                Definition d = new Definition(f[2]);
                                if (!definitionStaging.containsKey(d.name) && definitionStaging.size() >= CATALOG_LIMIT)
                                    throw new IllegalArgumentException("Profile catalog too large");
                                definitionStaging.put(d.name, d);
                            } catch (IllegalArgumentException invalid) { definitionStaging = null; return; }
                        } else if (f.length == 2 && "end".equals(f[1]) && definitionStaging != null) {
                            definitions = definitionStaging; definitionStaging = null; definitionsRevision++;
                        } else { definitionStaging = null; return; }
                    } else if ("route".equals(type) && f.length == 4) {
                        boolean rec = flag(f[1]); int count = number(f[3], 0, Integer.MAX_VALUE);
                        if (!plain(f[2])) return;
                        recording = rec; route = f[2]; waypoints = count; routeKnown = true;
                    } else if ("arena".equals(type) && f.length == 3 && plain(f[2])) {
                        boolean running = flag(f[1]); arenaRunning = running; arenaText = f[2];
                    } else if ("roam".equals(type) && f.length == 3 && plain(f[2])) {
                        int groups = number(f[1], 0, Integer.MAX_VALUE); roamGroups = groups; roamText = f[2];
                    } else if ("perf".equals(type) && f.length == 2 && plain(f[1])) {
                        perfText = f[1];
                    } else if ("msg".equals(type) && f.length == 2 && plain(f[1])) {
                        message = f[1]; messageVersion++;
                    } else return;
                } else return;
            }
        } catch (IllegalArgumentException malformed) { return; }
        RspsSidebar.refreshLater();
    }
    private static boolean catalog(String type, String[] f) {
        List<String> pending = CATALOG_STAGING.get(type);
        if (f.length == 2 && "clear".equals(f[1])) {
            CATALOG_STAGING.put(type, new ArrayList<String>());
        } else if (f.length == 3 && "add".equals(f[1]) && pending != null) {
            String[] added = f[2].isEmpty() ? new String[0] : f[2].split(",", -1);
            for (String value : added) if (!name(value)) { CATALOG_STAGING.remove(type); return false; }
            for (String value : added) {
                if (!pending.contains(value)) pending.add(value);
                if (pending.size() > CATALOG_LIMIT) { CATALOG_STAGING.remove(type); return false; }
            }
        } else if (f.length == 2 && "end".equals(f[1]) && pending != null) {
            String[] committed = pending.toArray(new String[pending.size()]);
            if ("profiles".equals(type)) profiles = committed;
            else if ("locations".equals(type)) { locations = committed; locationsRevision++; }
            else { routes = committed; routesRevision++; }
            CATALOG_STAGING.remove(type);
        } else { CATALOG_STAGING.remove(type); return false; }
        return true;
    }
    private static boolean populationCatalog(String[] f) {
        if (f.length == 2 && "clear".equals(f[1])) {
            populationStaging = new LinkedHashMap<Integer, PopulationEntry>();
        } else if (f.length == 3 && "add".equals(f[1]) && populationStaging != null) {
            try {
                PopulationEntry entry = new PopulationEntry(f[2]);
                if (populationStaging.containsKey(entry.id) || populationStaging.size() >= CATALOG_LIMIT)
                    throw new IllegalArgumentException("Invalid population catalog");
                populationStaging.put(entry.id, entry);
            } catch (IllegalArgumentException invalid) { populationStaging = null; return false; }
        } else if (f.length == 2 && "end".equals(f[1]) && populationStaging != null) {
            population = populationStaging; populationStaging = null; populationKnown = true;
        } else { populationStaging = null; return false; }
        return true;
    }
    private static int number(String s, int min, int max) {
        int n = Integer.parseInt(s);
        if (n < min || n > max) throw new IllegalArgumentException("Enter " + min + "-" + max + ".");
        return n;
    }
    private static boolean flag(String s) {
        if ("1".equals(s)) return true;
        if ("0".equals(s)) return false;
        throw new IllegalArgumentException("Invalid flag");
    }
    private static boolean safeText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }
    private static boolean plain(String value) { return safeText(value) && value.indexOf('|') < 0; }
    private static boolean name(String value) { return value != null && value.matches("[a-z0-9_-]{1,24}"); }
    private static boolean kit(String value) {
        return "nh".equals(value) || "woodcut".equals(value) || "fish".equals(value) || "none".equals(value) || "auto".equals(value);
    }
    private static boolean colour(String value) { return "off".equals(value) || value.matches("[a-fA-F0-9]{6}"); }
    private static Snapshot snapshot() {
        synchronized (LOCK) {
            Snapshot s = new Snapshot();
            s.staff = staff; s.known = stateKnown; s.enabled = enabled; s.mode = mode; s.pvpZone = pvpZone; s.pvpZoneKnown = pvpZoneKnown;
            s.alive = alive; s.maximum = maximum; s.recording = recording;
            s.routeKnown = routeKnown; s.route = route; s.waypoints = waypoints;
            s.profiles = profiles.clone(); s.locations = locations.clone(); s.routes = routes.clone();
            s.bots = new ArrayList<Bot>(bots); s.definitions = new LinkedHashMap<String, Definition>(definitions);
            s.settings = settings; s.arenaRunning = arenaRunning; s.arena = arenaText;
            s.options = options; s.options2 = options2; s.optsKnown = optsKnown; s.populationKnown = populationKnown; s.statsKnown = statsKnown;
            s.stats = statsText; s.population = new ArrayList<PopulationEntry>(population.values());
            s.roamGroups = roamGroups; s.roam = roamText; s.perf = perfText;
            s.message = message; s.messageVersion = messageVersion;
            s.locationsRevision = locationsRevision; s.routesRevision = routesRevision; s.definitionsRevision = definitionsRevision;
            return s;
        }
    }
    private static boolean enqueue(String text) {
        synchronized (LOCK) {
            if (!staff || text.isEmpty() || text.length() > 240 || !safeText(text)
                || text.indexOf('|') >= 0 || text.indexOf(';') >= 0 || text.indexOf('>') >= 0) return false;
            COMMANDS.add(new Command(session, text)); return true;
        }
    }
    /** Called only from RspsHud.tickInput's in-game branch. */
    static void tickInput() {
        for (Command command = COMMANDS.poll(); command != null; command = COMMANDS.poll()) {
            synchronized (LOCK) {
                if (!staff || command.session != session) continue;
                try { RspsDev.send(command.text); }
                catch (RuntimeException disconnected) { /* Never retry a staff action automatically. */ }
            }
        }
    }
    static void request() {
        synchronized (LOCK) { if (!subscribed && enqueue("bot panel")) subscribed = true; }
    }
    static void release() {
        synchronized (LOCK) {
            if (!subscribed) return;
            subscribed = false; enqueue("bot panel off");
        }
    }

    private final long panelSession;
    boolean currentSession() { synchronized (LOCK) { return panelSession == session; } }
    private final JLabel status = RspsScriptTools.label("Waiting for server state");
    private final JLabel feedback = RspsScriptTools.label("");
    private final JLabel routeStatus = RspsScriptTools.label("No route state yet");
    private final JLabel arenaStatus = RspsScriptTools.label("No arena state yet");
    private final JLabel roamStatus = RspsScriptTools.label("No roam state yet");
    private final JLabel perfStatus = RspsScriptTools.label("No performance state yet");
    private final JLabel settingsStatus = RspsScriptTools.label("Waiting for settings");
    private final JLabel profileStatus = RspsScriptTools.label("Choose a profile or enter a new name");
    private final JComboBox<String> world = combo("Wilderness only", "Everywhere");
    private JButton applyPvp;
    private final JComboBox<String> pvp = combo("Wilderness only", "Deadman (outside guarded cities)");
    private final JComboBox<String> profile = combo(), location = combo(), locationList = combo(), routeList = combo(), editorList = combo();
    private final JComboBox<String> spawnLevel = combo("1", "2", "3", "4", "5", "Random 1-5");
    private final JComboBox<String> loot = combo("None", "Gear drop"), editorKit = combo("nh", "woodcut", "fish", "none", "auto");
    private final JComboBox<String> botFights = combo("Off", "On"), ignoreStaff = combo("Off", "On"), decisionLog = combo("Off", "On");
    private final JComboBox<String> roamMode = combo("fair", "retaliate");
    private final JLabel optionsStatus = RspsScriptTools.label("Waiting for options"), scaleStatus = RspsScriptTools.label("Limit not known yet");
    private final JLabel statsStatus = RspsScriptTools.label("No stats state yet"), populationStatus = RspsScriptTools.label("Waiting for population catalog");
    private final JComboBox<String> combatMode = combo("Max (138)", "Match nearby players", "Fixed level");
    private final JSpinner fixedCombat = spinner(138, 3, 138), flee = spinner(0, 0, 90);
    private final JComboBox<String> duels = combo("Off", "On"), victimLoot = combo("Keeps items", "Drops loot on the ground");
    private final JComboBox<String> chat = combo("Off", "On"), populationKeeper = combo("Off", "On"), autoscale = combo("Off", "On");
    private final JComboBox<String> botXp = combo("Off", "On"), npcDefense = combo("Fight back", "Walk away", "Ignore"), fleeTeleport = combo("Off", "On");
    private final List<JComponent> optionInputs = new ArrayList<JComponent>();
    private final List<JButton> optionControls = new ArrayList<JButton>();
    private final JComboBox<String> populationProfile = combo(), populationLevel = combo("1", "2", "3", "4", "5", "r");
    private final JComboBox<String> populationPlace = combo("(profile's first place)");
    private final JComboBox<String> populationPreset = combo("towns", "skillers", "crafters", "wildy", "deadman", "all");
    private final JSpinner populationCount = spinner(1, 1, 20), populationFrom = spinner(0, 0, 23), populationTo = spinner(0, 0, 23);
    private final JComboBox<String> populationCombat = combo("Server setting", "Max (138)", "Match nearby players", "Fixed level");
    private final JSpinner populationCombatLevel = spinner(100, 3, 138);
    private final JList<PopulationEntry> populationList = new JList<PopulationEntry>(new DefaultListModel<PopulationEntry>());
    private final List<JComponent> populationInputs = new ArrayList<JComponent>();
    private JButton addPopulation, addPreset, deletePopulation, clearPopulation, resetStats;
    private final JCheckBox here = new JCheckBox("At my position");
    private final JSpinner count = spinner(1, 1, 10), maxBots = spinner(10, 1, 50), gap = spinner(10, 0, 126), idle = spinner(100, 20, 6000);
    private final JSpinner radius = spinner(5, 1, 30), fights = spinner(10, 1, 1000), levelA = spinner(3, 1, 5), levelB = spinner(3, 1, 5), lanes = spinner(1, 1, 8);
    private final JSpinner roamCount = spinner(1, 1, 50), roamLevel = spinner(3, 1, 5);
    private final JTextField colourField = RspsScriptTools.field(), routeName = RspsScriptTools.field(), locationName = RspsScriptTools.field(), editorName = RspsScriptTools.field();
    private final JTextArea editorSteps = new JTextArea(4, 12);
    private final JPanel botRows = new JPanel();
    private final List<JComponent> inputs = new ArrayList<JComponent>();
    private final List<JButton> controls = new ArrayList<JButton>();
    private final Map<String, BotView> botViews = new LinkedHashMap<String, BotView>();
    private final Map<JComponent, Boolean> settingsDirty = new LinkedHashMap<JComponent, Boolean>();
    private JButton spawn, record, save, cancel, deleteProfile;
    private boolean refreshing, profileDirty;
    private String loadedProfile;
    private Definition loadedDefinition;
    private Settings shownSettings;
    private long seenMessage = -1, editorRevision;
    private Selection pendingLocation, pendingRoute, pendingProfile;
    private long savedEditorRevision;
    private static final class Selection {
        final String name; final long revision;
        Selection(String name, long revision) { this.name = name; this.revision = revision; }
    }

    RspsBotPanel(Runnable back) {
        synchronized (LOCK) { panelSession = session; }
        RspsScriptTools.column(this); setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel heading = RspsScriptTools.row();
        JButton previous = RspsScriptTools.button("<"); previous.addActionListener(e -> back.run());
        heading.add(previous, BorderLayout.WEST); heading.add(RspsScriptTools.label("PK Bots (staff)"), BorderLayout.CENTER); add(heading);
        feedback.setForeground(ColorScheme.LIGHT_GRAY_COLOR); add(feedback);
        JPanel p = section("Status", true); p.add(status);
        p.add(commandButton("Bots on", "bots on"));
        JButton off = commandButton("Bots off (kill switch)", "bots off"); off.setBackground(new Color(0x903030)); off.setOpaque(true); p.add(off);
        labeled(p, "World mode", world); p.add(actionButton("Apply world mode", () -> send("bots world " + (world.getSelectedIndex() == 1 ? "everywhere" : "wilderness"))));
        dirty(world);
        labeled(p, "Bots fight in", pvp); p.add(applyPvp = actionButton("Apply fight area", () -> send("bots pvpzone " + (pvp.getSelectedIndex() == 1 ? "deadman" : "wilderness"))));
        dirty(pvp);
        p = section("Settings", false); p.add(settingsStatus);
        setting(p, "Maximum (1-50)", maxBots, () -> send("bots max " + integer(maxBots, 1, 50)));
        setting(p, "Loot", loot, () -> send("bots loot " + (loot.getSelectedIndex() == 1 ? "drop" : "none")));
        colourField.setText("off");
        setting(p, "Name colour (hex / off)", colourField, () -> {
            String value = colourField.getText().trim();
            if (!colour(value)) throw new IllegalArgumentException("Use rrggbb or off.");
            send("bots colour " + value);
        });
        setting(p, "Combat level gap (0-126)", gap, () -> send("bots gap " + integer(gap, 0, 126)));
        setting(p, "Bot fights", botFights, () -> send("bots botfights " + onOff(botFights)));
        setting(p, "Ignore staff", ignoreStaff, () -> send("bots ignorestaff " + onOff(ignoreStaff)));
        setting(p, "Decision log", decisionLog, () -> send("bot log " + onOff(decisionLog)));
        setting(p, "Idle deadline (20-6000 ticks)", idle, () -> send("bots idle " + integer(idle, 20, 6000)));
        p = section("Deadman & options", false); p.add(optionsStatus);
        optionSetting(p, "Bot combat level", combatMode, () -> send("bots combat " + combatValue()));
        labeled(p, "Fixed level (3-138)", fixedCombat); dirty(fixedCombat); optionInputs.add(fixedCombat);
        combatMode.addActionListener(e -> { if (!refreshing) refresh(); });
        optionSetting(p, "Flee below HP % (0 = never)", flee, () -> send("bots flee " + integer(flee, 0, 90)));
        optionSetting(p, "Player duels (::botduel)", duels, () -> send("bots duels " + onOff(duels)));
        optionSetting(p, "Player killed by a bot", victimLoot, () -> send("bots victimloot " + (victimLoot.getSelectedIndex() == 1 ? "drop" : "keep")));
        optionSetting(p, "Bot chat lines", chat, () -> send("bots chat " + onOff(chat)));
        optionSetting(p, "Population keeper", populationKeeper, () -> send("bots population " + onOff(populationKeeper)));
        optionSetting(p, "Auto-scale bot limit", autoscale, () -> send("bots autoscale " + onOff(autoscale))); p.add(scaleStatus);
        optionSetting(p, "Combat XP from bots", botXp, () -> send("bots xp " + onOff(botXp)));
        optionSetting(p, "When an NPC attacks a bot", npcDefense, () -> send("bots npcdefense " + new String[] { "fight", "avoid", "off" }[Math.max(0, npcDefense.getSelectedIndex())]));
        optionSetting(p, "Glory teleport when fleeing", fleeTeleport, () -> send("bots fleetp " + onOff(fleeTeleport)));
        p = section("Spawn", false); labeled(p, "Profile", profile); spawnLevel.setSelectedIndex(2);
        labeled(p, "Level", spawnLevel); labeled(p, "Count (1-10)", count); labeled(p, "Location", location);
        here.setOpaque(false); here.setForeground(Color.WHITE); here.setFont(status.getFont()); here.setAlignmentX(LEFT_ALIGNMENT); inputs.add(here); p.add(here);
        spawn = actionButton("Spawn", () -> {
            String selected = selected(profile), level = spawnLevel.getSelectedIndex() == 5 ? "r" : selected(spawnLevel);
            int n = integer(count, 1, 10);
            send(here.isSelected() ? "bot spawnhere " + selected + " " + level + " " + n
                : "bot spawnp " + selected + " " + level + " " + n + " " + selected(location));
        }); p.add(spawn);
        here.addActionListener(e -> { if (!refreshing) refresh(); });
        profile.addActionListener(e -> { if (!refreshing) refresh(); });
        location.addActionListener(e -> { if (!refreshing) refresh(); });
        locationList.addActionListener(e -> { if (!refreshing) pendingLocation = null; });
        routeList.addActionListener(e -> { if (!refreshing) pendingRoute = null; });
        p = section("Population", false); p.add(populationStatus);
        populationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); populationList.setFont(status.getFont());
        populationList.setBackground(ColorScheme.DARKER_GRAY_COLOR); populationList.setForeground(Color.WHITE);
        DefaultListCellRenderer populationRenderer = new DefaultListCellRenderer(); populationRenderer.putClientProperty("html.disable", Boolean.TRUE);
        populationList.setCellRenderer(populationRenderer);
        JScrollPane populationScroll = new JScrollPane(populationList); populationScroll.setAlignmentX(LEFT_ALIGNMENT);
        populationScroll.setPreferredSize(new Dimension(180, 90)); populationScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90)); p.add(populationScroll);
        populationList.addListSelectionListener(e -> { if (!refreshing && !e.getValueIsAdjusting()) refresh(); });
        populationField(p, "Profile", populationProfile); populationField(p, "Level", populationLevel); populationLevel.setSelectedIndex(2);
        populationField(p, "Count (1-20)", populationCount); populationField(p, "Place", populationPlace);
        populationField(p, "From hour (0-23)", populationFrom); populationField(p, "To hour (0-23)", populationTo);
        populationField(p, "Combat level", populationCombat); populationField(p, "Fixed level (3-138)", populationCombatLevel);
        populationCombat.addActionListener(e -> { if (!refreshing) refresh(); });
        p.add(help("Hours 0 and 0 mean always. The default place uses the profile's first place. Combat level: this group's own level (server 2026-10-14 or newer)."));
        addPopulation = actionButton("Add population", () -> {
            String profileName = selected(populationProfile), level = selected(populationLevel);
            String place = populationPlace.getSelectedIndex() == 0 ? "-" : selected(populationPlace);
            if (!name(profileName) || !("r".equals(level) || level.matches("[1-5]")) || !("-".equals(place) || name(place)))
                throw new IllegalArgumentException("Choose a valid population profile, level and place.");
            send("bot pop add " + profileName + " " + level + " " + integer(populationCount, 1, 20) + " " + place
                + " " + integer(populationFrom, 0, 23) + "-" + integer(populationTo, 0, 23) + populationCombatValue());
        }); p.add(addPopulation);
        deletePopulation = actionButton("Delete selected", () -> {
            PopulationEntry entry = populationList.getSelectedValue();
            if (entry == null || !snapshot().population.stream().anyMatch(value -> value.id == entry.id))
                throw new IllegalArgumentException("Choose an existing population entry.");
            send("bot pop del " + entry.id);
        }); p.add(deletePopulation);
        clearPopulation = commandButton("Clear all", "bot pop clear"); p.add(clearPopulation);
        populationField(p, "Preset", populationPreset);
        addPreset = actionButton("Add preset", () -> send("bot pop preset " + selected(populationPreset))); p.add(addPreset);
        p = section("Bots", true); RspsScriptTools.column(botRows); p.add(botRows); p.add(commandButton("Despawn all", "bot despawn all"));
        p = section("Locations", false); labeled(p, "Location", locationList); labeled(p, "Name (a-z, 0-9, _ or -)", locationName);
        labeled(p, "Radius (1-30)", radius);
        p.add(actionButton("Add here", () -> {
            String n = enteredName(locationName); int r = integer(radius, 1, 30);
            long revision = snapshot().locationsRevision;
            if (send("bot loc add " + n + " " + r)) pendingLocation = new Selection(n, revision);
        }));
        p.add(actionButton("Delete location", () -> send("bot loc del " + selected(locationList))));
        p.add(actionButton("Teleport me there", () -> send("bot loc tp " + selected(locationList))));
        p = section("Routes", false); labeled(p, "Record name", routeName); p.add(routeStatus);
        record = actionButton("Record", () -> send("bot route record " + enteredName(routeName)));
        save = actionButton("Save", () -> {
            Snapshot s = snapshot();
            if (send("bot route save") && name(s.route)) pendingRoute = new Selection(s.route, s.routesRevision);
        }); cancel = commandButton("Cancel", "bot route cancel");
        buttons(p, record, save, cancel); labeled(p, "Saved route", routeList);
        p.add(actionButton("Delete route", () -> send("bot route del " + selected(routeList))));
        p = section("Profiles", false); labeled(p, "Profile", editorList); p.add(profileStatus);
        labeled(p, "Name (new or existing)", editorName); labeled(p, "Kit", editorKit);
        editorSteps.setFont(status.getFont()); editorSteps.setForeground(Color.WHITE); editorSteps.setCaretColor(Color.WHITE);
        editorSteps.setBackground(ColorScheme.DARKER_GRAY_COLOR); editorSteps.setLineWrap(true); editorSteps.setWrapStyleWord(true); inputs.add(editorSteps);
        JScrollPane stepsScroll = new JScrollPane(editorSteps); stepsScroll.setAlignmentX(LEFT_ALIGNMENT);
        stepsScroll.setPreferredSize(new Dimension(180, 76)); stepsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76)); p.add(RspsScriptTools.label("Steps (one line; separate with ;)")); p.add(stepsScroll);
        p.add(actionButton("Save profile", () -> {
            String n = enteredName(editorName), steps = editorSteps.getText();
            if (!plain(steps) || steps.trim().isEmpty()) throw new IllegalArgumentException("Steps: no | or control characters.");
            long revision = snapshot().definitionsRevision;
            if (send("bot profile set " + n + " " + selected(editorKit) + " " + steps.trim().replace(';', ','))) { // Keep Claude's packet-safe format.
                pendingProfile = new Selection(n, revision); savedEditorRevision = editorRevision;
            }
        }));
        deleteProfile = actionButton("Delete custom profile", () -> {
            String n = enteredName(editorName); Definition d = snapshot().definitions.get(n);
            if (d == null || d.builtin) throw new IllegalArgumentException("Only a known custom profile can be deleted.");
            send("bot profile del " + n);
        }); p.add(deleteProfile); p.add(commandButton("Reload profiles", "bot profiles reload"));
        p.add(actionButton("New profile", () -> {
            // Suppress the selection listener while clearing the editor deliberately.
            refreshing = true;
            try { pendingProfile = null; editorRevision++; loadedProfile = null; loadedDefinition = null; editorList.setSelectedIndex(-1); editorName.setText(""); editorKit.setSelectedItem("nh"); editorSteps.setText(""); profileDirty = true; profileStatus.setText("New custom profile"); }
            finally { refreshing = false; }
            refresh();
        }));
        p.add(help("goto <place>; route <name>; roam <place> peaceful|retaliate|fair <min>; bank <place>; skill woodcut|fish <place> <min>; idle <sec>; respawn <sec>; loop. Kits: nh, woodcut, fish, none."));
        editorList.addActionListener(e -> { if (!refreshing) { pendingProfile = null; loadDefinition(snapshot(), true); refresh(); } });
        watch(editorName, () -> { profileDirty = true; editorRevision++; updateDeleteProfile(); }); watch(editorSteps, () -> { profileDirty = true; editorRevision++; });
        editorKit.addActionListener(e -> { if (!refreshing) { profileDirty = true; editorRevision++; } });
        p = section("Arena", false); p.add(arenaStatus); labeled(p, "Fights (1-1000)", fights); labeled(p, "Level A (1-5)", levelA);
        labeled(p, "Level B (1-5)", levelB); labeled(p, "Lanes (1-8)", lanes);
        p.add(actionButton("Start arena", () -> send("bot arena " + integer(fights, 1, 1000) + " " + integer(levelA, 1, 5) + " " + integer(levelB, 1, 5) + " " + integer(lanes, 1, 8))));
        p.add(commandButton("Stop arena", "bot arena stop"));
        p = section("Roam group", false); p.add(roamStatus); labeled(p, "Count (1-50)", roamCount); labeled(p, "Level (1-5)", roamLevel); labeled(p, "Mode", roamMode);
        p.add(actionButton("Start roam group", () -> send("bot roam " + integer(roamCount, 1, 50) + " " + integer(roamLevel, 1, 5) + " " + selected(roamMode))));
        p.add(commandButton("Stop roam group", "bot roam stop"));
        p = section("Info", false); p.add(perfStatus); p.add(statsStatus);
        resetStats = commandButton("Reset stats", "bot stats reset"); p.add(resetStats); p.add(commandButton("Refresh", "bot panel"));
        refresh();
    }
    private static JComboBox<String> combo(String... values) { return RspsScriptTools.combo(values); }
    private static JSpinner spinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        s.setFont(RspsScriptTools.label("").getFont()); return s;
    }
    private JPanel section(String title, boolean expanded) {
        add(Box.createVerticalStrut(8));
        JPanel body = new JPanel(); RspsScriptTools.column(body); body.setVisible(expanded);
        JButton toggle = RspsScriptTools.button((expanded ? "- " : "+ ") + title); toggle.setForeground(RspsSidebar.accent());
        toggle.addActionListener(e -> { body.setVisible(!body.isVisible()); toggle.setText((body.isVisible() ? "- " : "+ ") + title); RspsScriptTools.fit(this); });
        add(toggle); add(body); return body;
    }
    private void labeled(JPanel p, String title, JComponent input) {
        p.add(RspsScriptTools.label(title)); input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        input.setAlignmentX(LEFT_ALIGNMENT); p.add(input); inputs.add(input);
    }
    private void setting(JPanel p, String title, JComponent input, Runnable action) {
        labeled(p, title, input); p.add(actionButton("Apply " + title.split(" ")[0], action)); dirty(input);
    }
    private void optionSetting(JPanel p, String title, JComponent input, Runnable action) {
        labeled(p, title, input); p.add(actionButton("Apply", action)); dirty(input); // plain "Apply": the row title says what
        optionInputs.add(input); optionControls.add(controls.get(controls.size() - 1));
    }
    private void populationField(JPanel p, String title, JComponent input) {
        labeled(p, title, input); populationInputs.add(input);
    }
    /** "" (the server's own ::bots combat), " max", " match" or " <3-138>" for the population entry. */
    private String populationCombatValue() {
        switch (populationCombat.getSelectedIndex()) {
            case 1: return " max";
            case 2: return " match";
            case 3: return " " + integer(populationCombatLevel, 3, 138);
            default: return "";
        }
    }
    private String combatValue() {
        switch (combatMode.getSelectedIndex()) {
            case 0: return "max";
            case 1: return "match";
            case 2: return String.valueOf(integer(fixedCombat, 3, 138));
            default: throw new IllegalArgumentException("Choose a combat level mode.");
        }
    }
    private void dirty(JComponent input) {
        settingsDirty.put(input, false);
        Runnable changed = () -> { if (!refreshing) settingsDirty.put(input, true); };
        if (input instanceof JComboBox) ((JComboBox<?>) input).addActionListener(e -> changed.run());
        else if (input instanceof JSpinner) {
            JSpinner s = (JSpinner) input; s.addChangeListener(e -> changed.run());
            if (s.getEditor() instanceof JSpinner.DefaultEditor) watch(((JSpinner.DefaultEditor) s.getEditor()).getTextField(), changed);
        } else if (input instanceof JTextField) watch((JTextField) input, changed);
    }
    private void watch(javax.swing.text.JTextComponent input, Runnable changed) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
            private void update() { if (!refreshing) changed.run(); }
        });
    }
    private static void buttons(JPanel p, JButton... values) {
        JPanel line = new JPanel(new GridLayout(1, values.length, 3, 0)); line.setOpaque(false);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22)); line.setAlignmentX(LEFT_ALIGNMENT);
        for (JButton b : values) line.add(b); p.add(line);
    }
    private static JTextArea help(String text) {
        JTextArea area = new JTextArea(text); area.setFont(RspsScriptTools.label("").getFont());
        area.setForeground(ColorScheme.LIGHT_GRAY_COLOR); area.setOpaque(false); area.setEditable(false);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setFocusable(false); area.setAlignmentX(LEFT_ALIGNMENT);
        area.setSize(180, Short.MAX_VALUE); area.setMaximumSize(new Dimension(Integer.MAX_VALUE, area.getPreferredSize().height));
        return area;
    }
    private JButton actionButton(String label, Runnable action) {
        JButton b = RspsScriptTools.button(label);
        b.addActionListener(e -> {
            if (refreshing || !b.isEnabled() || !currentSession() || !staff) return;
            try { action.run(); }
            catch (IllegalArgumentException invalid) { feedback("Invalid input: " + invalid.getMessage()); }
        }); controls.add(b); return b;
    }
    private JButton commandButton(String label, String command) { return actionButton(label, () -> send(command)); }
    private static String onOff(JComboBox<String> box) { return box.getSelectedIndex() == 1 ? "on" : "off"; }
    private static int integer(JSpinner spinner, int min, int max) {
        try { spinner.commitEdit(); }
        catch (java.text.ParseException invalid) { throw new IllegalArgumentException("Enter " + min + "-" + max + "."); }
        return number(spinner.getValue().toString(), min, max);
    }
    private static String enteredName(JTextField field) {
        String value = field.getText().trim();
        if (!name(value)) throw new IllegalArgumentException("Name: 1-24 lowercase letters, digits, _ or -.");
        return value;
    }
    private static String selected(JComboBox<String> box) {
        Object value = box.getSelectedItem();
        if (value == null) throw new IllegalArgumentException("Choose an option first.");
        return value.toString();
    }
    private void feedback(String value) {
        feedback.setText(value); feedback.setToolTipText(RspsScriptTools.tooltip(value));
    }
    private boolean send(String command) {
        synchronized (LOCK) {
            if (panelSession != session) { feedback("Session changed; reopen the panel."); return false; }
            if (command.length() > 240) { feedback("Command exceeds 240 characters; shorten the fields."); return false; }
            if (!enqueue(command)) { feedback("Request blocked (staff/session/packet characters)."); return false; }
        }
        feedback("Queued request; awaiting server reply."); feedback.setToolTipText(RspsScriptTools.tooltip(command));
        return true;
    }
    private static void choices(JComboBox<String> box, String[] values) {
        String[] current = new String[box.getItemCount()];
        for (int i = 0; i < current.length; i++) current[i] = box.getItemAt(i);
        if (Arrays.equals(current, values)) return;
        Object selected = box.getSelectedItem(); box.setModel(new DefaultComboBoxModel<String>(values));
        for (String value : values) if (value.equals(selected)) { box.setSelectedItem(value); break; }
    }
    private void populationChoices(List<PopulationEntry> values) {
        ListModel<PopulationEntry> current = populationList.getModel();
        boolean same = current.getSize() == values.size();
        for (int i = 0; same && i < values.size(); i++) {
            PopulationEntry a = current.getElementAt(i), b = values.get(i);
            same = a.id == b.id && a.description.equals(b.description);
        }
        if (same) return;
        PopulationEntry selected = populationList.getSelectedValue();
        DefaultListModel<PopulationEntry> next = new DefaultListModel<PopulationEntry>(); int selectedIndex = -1;
        for (PopulationEntry entry : values) {
            if (selected != null && selected.id == entry.id) selectedIndex = next.getSize();
            next.addElement(entry);
        }
        populationList.setModel(next);
        if (selectedIndex >= 0) populationList.setSelectedIndex(selectedIndex); else populationList.clearSelection();
    }
    private void showSetting(JComponent input, Object value) {
        if (Boolean.TRUE.equals(settingsDirty.get(input))) return;
        if (input instanceof JSpinner) ((JSpinner) input).setValue(value);
        else if (input instanceof JComboBox) ((JComboBox<?>) input).setSelectedItem(value);
        else ((JTextField) input).setText(String.valueOf(value));
    }
    private void updateDeleteProfile() {
        Definition d = snapshot().definitions.get(editorName.getText().trim());
        deleteProfile.setEnabled(staff && currentSession() && d != null && !d.builtin);
    }
    private void loadDefinition(Snapshot s, boolean deliberateSelection) {
        String n = (String) editorList.getSelectedItem(); Definition d = s.definitions.get(n);
        boolean same = n == null ? loadedProfile == null : n.equals(loadedProfile);
        if (!deliberateSelection && same && profileDirty) return;
        if (!deliberateSelection && same && equivalent(d, loadedDefinition)) return;
        boolean before = refreshing; refreshing = true;
        try {
            loadedProfile = n; loadedDefinition = d; profileDirty = false;
            editorName.setText(d == null ? "" : d.name); editorKit.setSelectedItem(d == null ? "nh" : d.kit);
            editorSteps.setText(d == null ? "" : d.steps);
            profileStatus.setText(d == null ? "New custom profile" : d.builtin ? "Built-in profile" : "Custom profile");
            profileStatus.setToolTipText(RspsScriptTools.tooltip(profileStatus.getText()));
        } finally { refreshing = before; }
    }
    private static boolean equivalent(Definition a, Definition b) {
        return a == b || a != null && b != null && a.name.equals(b.name) && a.kit.equals(b.kit) && a.steps.equals(b.steps) && a.builtin == b.builtin;
    }
    private final class BotView {
        final JPanel panel = new JPanel();
        final JLabel nameLabel = RspsScriptTools.label(""), details = RspsScriptTools.label(""), task = RspsScriptTools.label("");
        final JButton actions = RspsScriptTools.button("Actions");
        Bot bot;
        BotView(Bot value) {
            RspsScriptTools.column(panel); JPanel line = RspsScriptTools.row(); nameLabel.setForeground(new Color(0x00c8ff));
            line.add(nameLabel, BorderLayout.CENTER); line.add(actions, BorderLayout.EAST); panel.add(line); panel.add(details); panel.add(task);
            actions.addActionListener(e -> { if (staff && currentSession()) showActions(this); }); update(value, true);
        }
        void update(Bot value, boolean authorised) {
            bot = value; text(nameLabel, value.name); text(details, value.profile + "  " + (value.level == 0 ? "bench" : "L" + value.level) + "  HP " + value.hp + "%");
            text(task, value.task + (value.position.isEmpty() ? "" : "  @ " + value.position));
            actions.setEnabled(authorised); panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        }
    }
    private void showActions(BotView view) {
        JPopupMenu menu = actionsMenu(view);
        menu.show(view.actions, 0, view.actions.getHeight());
    }
    private JPopupMenu actionsMenu(BotView view) {
        Snapshot s = snapshot(); Bot bot = view.bot;
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Stop", "bot despawn " + bot.name);
        item(menu, bot.paused ? "Resume" : "Pause", "bot order " + (bot.paused ? "resume " : "pause ") + bot.name);
        item(menu, "Come here", "bot order here " + bot.name);
        JMenu go = catalogMenu("Go to location", s.locations, "bot order goto ", bot.name);
        menu.add(go);
        JMenu change = catalogMenu("Change profile", s.profiles, "bot order profile ", bot.name);
        menu.add(change);
        item(menu, "Teleport me to bot", "bot tpto " + bot.name);
        return menu;
    }
    /** Native menu items keep mouse navigation inside Swing's menu selection path. */
    private JMenu catalogMenu(String title, String[] names, String command, String botName) {
        JMenu root = new JMenu(title), page = root;
        root.setFont(status.getFont()); root.setEnabled(names.length > 0);
        for (int i = 0; i < names.length; i++) {
            if (i > 0 && i % 20 == 0) {
                JMenu more = new JMenu("More..."); more.setFont(status.getFont());
                page.add(more); page = more;
            }
            item(page, names[i], command + names[i] + " " + botName);
        }
        return root;
    }
    /** Consume an auto-selection only after a fresh, committed catalog contains the submitted name. */
    private static boolean selectSaved(JComboBox<String> box, Selection pending, long revision) {
        if (pending == null || revision <= pending.revision) return false;
        for (int i = 0; i < box.getItemCount(); i++) if (pending.name.equals(box.getItemAt(i))) {
            box.setSelectedItem(pending.name); return true;
        }
        return false;
    }
    private void item(JComponent parent, String label, String command) {
        menuAction(parent, label, () -> send(command));
    }
    private void menuAction(JComponent parent, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label); item.putClientProperty("html.disable", Boolean.TRUE); item.setFont(status.getFont());
        item.addActionListener(e -> {
            if (!refreshing && staff && currentSession()) {
                try { action.run(); }
                catch (IllegalArgumentException invalid) { feedback("Invalid input: " + invalid.getMessage()); }
            }
        }); parent.add(item);
    }
    private static void text(JLabel label, String value) { label.setText(value); label.setToolTipText(RspsScriptTools.tooltip(value)); }
    /** Refresh never sends commands; controls retain drafts and surviving catalog selections. */
    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        Snapshot s = snapshot(); boolean authorised = s.staff && currentSession(); refreshing = true;
        try {
            text(status, s.known ? "Bots " + (s.enabled ? "ON" : "OFF") + "  " + s.alive + "/" + s.maximum + "  " + ("EVERYWHERE".equals(s.mode) ? "Everywhere" : "Wilderness only") + ", fights: " + ("DEADMAN".equals(s.pvpZone) ? "Deadman" : "Wilderness") : "Waiting for server state");
            showSetting(pvp, "DEADMAN".equals(s.pvpZone) ? "Deadman (outside guarded cities)" : "Wilderness only");
            showSetting(world, "EVERYWHERE".equals(s.mode) ? "Everywhere" : "Wilderness only");
            choices(profile, s.profiles); choices(location, s.locations); choices(locationList, s.locations); choices(routeList, s.routes);
            choices(populationProfile, s.profiles);
            String[] populationPlaces = new String[s.locations.length + 1]; populationPlaces[0] = "(profile's first place)";
            System.arraycopy(s.locations, 0, populationPlaces, 1, s.locations.length); choices(populationPlace, populationPlaces);
            populationChoices(s.population);
            text(populationStatus, s.populationKnown ? s.population.size() + " population entries" : "Waiting for population catalog");
            text(statsStatus, s.stats);
            if (s.options != null) {
                Options x = s.options;
                if (!Boolean.TRUE.equals(settingsDirty.get(combatMode)) && !Boolean.TRUE.equals(settingsDirty.get(fixedCombat))) {
                    showSetting(combatMode, "max".equals(x.combat) ? "Max (138)" : "match".equals(x.combat) ? "Match nearby players" : "Fixed level");
                    if (!"max".equals(x.combat) && !"match".equals(x.combat)) showSetting(fixedCombat, number(x.combat, 3, 138));
                }
                showSetting(flee, x.flee); showSetting(duels, x.duels ? "On" : "Off");
                showSetting(victimLoot, "DROP".equals(x.victimLoot) ? "Drops loot on the ground" : "Keeps items");
                showSetting(chat, x.chat ? "On" : "Off"); showSetting(populationKeeper, x.population ? "On" : "Off");
                showSetting(autoscale, x.autoscale ? "On" : "Off"); text(scaleStatus, "Limit at " + x.scale + "%");
            }
            if (s.options2 != null) {
                Options2 y = s.options2;
                showSetting(botXp, y.xp ? "On" : "Off"); showSetting(fleeTeleport, y.fleeTeleport ? "On" : "Off");
                showSetting(npcDefense, "AVOID".equals(y.npcDefense) ? "Walk away" : "OFF".equals(y.npcDefense) ? "Ignore" : "Fight back");
            }
            text(optionsStatus, s.optsKnown ? "Server options received; edited controls stay as drafts" : "Waiting for options");
            boolean newDraft = loadedProfile == null && profileDirty && editorList.getSelectedIndex() < 0;
            choices(editorList, s.definitions.keySet().toArray(new String[s.definitions.size()]));
            if (newDraft) editorList.setSelectedIndex(-1);
            if (authorised) {
                if (selectSaved(locationList, pendingLocation, s.locationsRevision)) pendingLocation = null;
                if (selectSaved(routeList, pendingRoute, s.routesRevision)) pendingRoute = null;
                if (pendingProfile != null && editorRevision != savedEditorRevision) pendingProfile = null;
                if (selectSaved(editorList, pendingProfile, s.definitionsRevision)) {
                    pendingProfile = null; loadDefinition(s, true);
                }
            }
            loadDefinition(s, false);
            if (s.settings != null && s.settings != shownSettings) {
                Settings x = s.settings;
                showSetting(maxBots, x.max); showSetting(loot, "GEAR_DROP".equals(x.loot) ? "Gear drop" : "None");
                showSetting(colourField, x.colour); showSetting(gap, x.gap); showSetting(idle, x.idle);
                showSetting(botFights, x.fights ? "On" : "Off"); showSetting(ignoreStaff, x.ignore ? "On" : "Off"); showSetting(decisionLog, x.log ? "On" : "Off");
                shownSettings = x;
            }
            text(settingsStatus, s.settings == null ? "Waiting for settings" : "Server max " + s.settings.max + ", loot " + s.settings.loot);
            if (s.settings != null) settingsStatus.setToolTipText(RspsScriptTools.tooltip("Server: max " + s.settings.max + ", loot " + s.settings.loot + ", colour " + s.settings.colour + ", gap " + s.settings.gap + ", bot fights " + s.settings.fights + ", ignore staff " + s.settings.ignore + ", log " + s.settings.log + ", idle " + s.settings.idle + ". Edited controls stay as drafts."));
            for (JButton b : controls) b.setEnabled(authorised);
            for (JComponent input : inputs) input.setEnabled(authorised);
            world.setEnabled(authorised && s.known); pvp.setEnabled(authorised && s.known && s.pvpZoneKnown); if (applyPvp != null) applyPvp.setEnabled(authorised && s.known && s.pvpZoneKnown);
            for (JComponent input : optionInputs) input.setEnabled(authorised && s.optsKnown);
            for (JButton button : optionControls) button.setEnabled(authorised && s.optsKnown);
            fixedCombat.setEnabled(authorised && s.optsKnown && combatMode.getSelectedIndex() == 2);
            for (JComponent input : populationInputs) input.setEnabled(authorised && s.populationKnown);
            populationProfile.setEnabled(authorised && s.populationKnown && s.profiles.length > 0);
            populationCombatLevel.setEnabled(authorised && s.populationKnown && populationCombat.getSelectedIndex() == 3);
            populationList.setEnabled(authorised && s.populationKnown);
            addPopulation.setEnabled(authorised && s.populationKnown && populationProfile.getSelectedItem() != null);
            addPreset.setEnabled(authorised && s.populationKnown);
            deletePopulation.setEnabled(authorised && s.populationKnown && populationList.getSelectedValue() != null);
            clearPopulation.setEnabled(authorised && s.populationKnown && !s.population.isEmpty());
            resetStats.setEnabled(authorised && s.statsKnown);
            profile.setEnabled(authorised && s.profiles.length > 0); location.setEnabled(authorised && !here.isSelected() && s.locations.length > 0);
            spawn.setEnabled(authorised && s.known && s.enabled && profile.getSelectedItem() != null && (here.isSelected() || location.getSelectedItem() != null));
            record.setEnabled(authorised && !s.recording); save.setEnabled(authorised && s.recording); cancel.setEnabled(authorised && s.recording);
            routeName.setEnabled(authorised && !s.recording);
            Definition deleting = s.definitions.get(editorName.getText().trim()); deleteProfile.setEnabled(authorised && deleting != null && !deleting.builtin);
            text(routeStatus, s.routeKnown ? (s.recording ? "Recording " : "Route ") + s.route + "  " + s.waypoints + " points" : "No route state yet");
            text(arenaStatus, (s.arenaRunning ? "Running: " : "Stopped: ") + s.arena); text(roamStatus, s.roamGroups + " groups: " + s.roam); text(perfStatus, s.perf);
            if (seenMessage != s.messageVersion) { seenMessage = s.messageVersion; if (!s.message.isEmpty()) feedback(s.message); }
            List<String> order = new ArrayList<String>();
            for (Bot bot : s.bots) if (!order.contains(bot.name)) order.add(bot.name);
            boolean rebuild = !order.equals(new ArrayList<String>(botViews.keySet()));
            if (rebuild) {
                Map<String, BotView> previous = new LinkedHashMap<String, BotView>(botViews); botViews.clear(); botRows.removeAll();
                if (order.isEmpty()) botRows.add(RspsScriptTools.label("No bots in the server list."));
                for (Bot bot : s.bots) if (!botViews.containsKey(bot.name)) {
                    BotView view = previous.get(bot.name); if (view == null) view = new BotView(bot);
                    botViews.put(bot.name, view); botRows.add(view.panel); botRows.add(Box.createVerticalStrut(5));
                }
            } else if (botViews.isEmpty() && botRows.getComponentCount() == 0) botRows.add(RspsScriptTools.label("No bots in the server list."));
            for (Bot bot : s.bots) { BotView view = botViews.get(bot.name); if (view != null) view.update(bot, authorised); }
        } finally { refreshing = false; }
        RspsScriptTools.fit(this);
    }
    @Override public Dimension getPreferredSize() { return new Dimension(200, super.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
}
