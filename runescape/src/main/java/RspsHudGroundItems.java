import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Toolkit;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Ground Items (RuneLite GroundItemsPlugin / GroundItemsOverlay, every config item - see RspsHudConfig):
 *
 *  - labels: name, quantity, price per "Price display mode" (GE / HA / both / off, never for coins), colour by value tier
 *    ("Highlight value calculation" HA / GE / highest), the highlighted colour, and while the hotkey is held hidden items in
 *    the hidden colour with RuneLite's [-] / [+] boxes (hotkey + click hides / highlights the item); a double tap of the
 *    hotkey hides every label ("Double-tap delay");
 *  - filters: highlighted / hidden lists, "Hide under value" (under both GE and HA value), "Do not hide untradeables",
 *    "Show highlighted items only", "Ownership filter" (all / takeable / your drops), "Item highlight mode" (overlay and/or
 *    right-click menu);
 *  - despawn timer off / pie / ticks / seconds, yellow for public items and green for private ones (RuneLite's colours);
 *    tile highlights; lootbeams for highlighted items and from a value tier (modern / legacy); text outline;
 *  - notifications for your own fresh drops that are highlighted or reach "Notify tier";
 *  - the right-click menu ({@link #menuOption}, {@link #menuTarget}, {@link #deprioritize}): quantities, value colours on
 *    the option and/or name, recoloured and deprioritised hidden items, collapsed entries (MiniMenu).
 *
 * The items themselves are the client's own ground-item state ({@link Static497#objStacks}), which the OBJ_ADD / OBJ_DEL /
 * OBJ_COUNT / OBJ_REVEAL zone packets, zone resets, map rebuilds and logout already keep exact - so a pickup, partial
 * pickup, despawn, teleport, region change or relog can never leave a label behind. Every change marks its tile; only
 * changed tiles are rebuilt. Values, despawn time, ownership and tradeability come from the server (`gi` entries).
 */
final class RspsHudGroundItems {

    /** World units above the ground at which the lowest label of a tile sits (a tile is 512 units wide). */
    private static final int LABEL_HEIGHT = 96;
    /** RuneLite GroundItemsOverlay STRING_GAP. */
    private static final int LINE = 15;
    /** RuneLite RECTANGLE_SIZE and TIMER_OVERLAY_DIAMETER. */
    private static final int BOX = 8;
    private static final int PIE = 10;
    private static final int PUBLIC_TIMER = 0xFFFFFF00;
    private static final int PRIVATE_TIMER = 0xFF00FF00;
    /** Lootbeam height in world units (about two tiles). */
    private static final int BEAM_HEIGHT = 1100;

    static final int FLAG_UNTRADEABLE = 1;
    static final int FLAG_MINE = 2;
    static final int FLAG_OTHERS = 4;
    static final int FLAG_PRIVATE = 8;
    static final int FLAG_FRESH = 16;

    /** Server data per tile + item id: despawn end (ms, -1 = never), GE unit, HA unit, flags. */
    private static final Map<Long, long[]> SERVER = new HashMap<Long, long[]>();
    /** Items dropped this cycle for this player, waiting for their name and count to decide on a notification. */
    private static final Set<Long> freshDrops = new HashSet<Long>();

    /** Labels per tile (tile key -> its labels, bottom row first). */
    private static final Map<Long, List<Label>> labels = new LinkedHashMap<Long, List<Label>>();
    private static boolean dirty = true;
    private static final Set<Long> dirtyTiles = new HashSet<Long>();
    private static int configRevision = -1;

    /** Despawn delay of a new drop in ticks (server gItemDespawnDelay, pushed as `gdelay|n`), for the pie timer. */
    private static int despawnTicks = 200;

    /** RuneLite hideAll: a double tap of the hotkey hides every label until the next double tap. */
    private static boolean hideAll;
    private static long lastTap;

    static final class Label {
        int level;
        int x;
        int z;
        int id;
        long serverKey;
        String name;
        String text;
        int colour;
        long value;
        long count;
        boolean hidden;
        /** On the hidden list (not merely under the value threshold): RuneLite gives such an item no loot beam. */
        boolean listedHidden;
        boolean highlighted;
        boolean beam;
        int beamColour;
        int row;
    }

    /** [-] / [+] boxes drawn this frame while the hotkey is held: x, y, and which (0 hide, 1 highlight) per label. */
    private static final List<Object[]> boxes = new ArrayList<Object[]>();
    private static long boxesDrawnAt;

    private RspsHudGroundItems() {
    }

    static void markDirty() {
        dirty = true;
    }

    static void markTile(long tileKey) {
        dirtyTiles.add(tileKey);
    }

    static void markTile(int level, int x, int z) {
        dirtyTiles.add(tileKey(level, x, z));
    }

    static void reset() {
        SERVER.clear();
        freshDrops.clear();
        labels.clear();
        dirtyTiles.clear();
        boxes.clear();
        dirty = true;
        hideAll = false;
    }

    static void setDespawnTicks(int ticks) {
        if (ticks > 0) {
            despawnTicks = ticks;
        }
    }

    private static long tileKey(int level, int x, int z) {
        return ((long) level << 28) | ((long) z << 14) | x;
    }

    private static long serverKey(long tileKey, int id) {
        return ((long) id << 32) | tileKey;
    }

    /**
     * {@code level,x,z,id,ticks,ge,ha,flags,amount;...} - ticks -1 = never despawns, -2 = none of these left on the tile; amount =
     * the server's real total (the OBJ_ADD packet's count is 16-bit, so stacks over 65 535 arrive wrapped).
     */
    static void update(String rest) {
        for (String entry : rest.split(";")) {
            String[] f = entry.split(",");
            if (f.length < 6) {
                continue;
            }
            long key = serverKey(tileKey(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])), Integer.parseInt(f[3]));
            int ticks = Integer.parseInt(f[4]);
            if (ticks == -2) {
                SERVER.remove(key);
                freshDrops.remove(key);
            } else {
                long end = ticks < 0 ? -1L : RspsHud.now() + ticks * RspsHud.TICK_MS;
                long ha = f.length > 6 ? Long.parseLong(f[6]) : 0L;
                long flags = f.length > 7 ? Long.parseLong(f[7]) : 0L;
                long amount = f.length > 8 ? Long.parseLong(f[8]) : -1L;
                SERVER.put(key, new long[]{end, Long.parseLong(f[5]), ha, flags, amount});
                if ((flags & FLAG_FRESH) != 0 && (flags & FLAG_MINE) != 0) {
                    freshDrops.add(key);
                }
            }
            markTile(key & 0xFFFFFFFFL);
        }
    }

    // ---- hotkey -------------------------------------------------------------------------------------------------------

    static boolean hotkeyHeld() {
        return rs2.client.event.keyboard.KeyboardMonitor.instance != null
            && rs2.client.event.keyboard.KeyboardMonitor.instance.isPressed(RspsHudConfig.hotkeyCode());
    }

    /** A key press from the client's input loop: a double tap of the hotkey toggles hide-all (RuneLite). */
    static void keyPressed(int keyCode, long time) {
        if (keyCode != RspsHudConfig.hotkeyCode()) {
            return;
        }
        int delay = RspsHudConfig.integer("grounditems.doubleTapDelay");
        if (delay > 0 && lastTap > 0L && time - lastTap <= delay) {
            hideAll = !hideAll;
            lastTap = 0L;
            dirty = true;
        } else {
            lastTap = time;
        }
    }

    static boolean hideAll() {
        return hideAll;
    }

    /**
     * A press while the hotkey is held: on an item's [-] box hide it, on its [+] box highlight it (again: take it off).
     * Returns true when a box took the press.
     */
    static boolean boxClick(int x, int y) {
        if (System.currentTimeMillis() - boxesDrawnAt > 250L) {
            return false;
        }
        for (Object[] box : boxes) {
            int bx = (Integer) box[0];
            int by = (Integer) box[1];
            if (x >= bx && y >= by && x < bx + BOX && y < by + BOX) {
                Label label = (Label) box[3];
                boolean hideBox = (Integer) box[2] == 0;
                String name = label.name.toLowerCase();
                if (hideBox) {
                    RspsHud.command("hud list hide " + (label.hidden && RspsHudConfig.isHidden(label.name) ? "remove " : "add ") + name);
                } else {
                    RspsHud.command("hud list highlight " + (label.highlighted ? "remove " : "add ") + name);
                }
                return true;
            }
        }
        return false;
    }

    // ---- labels -------------------------------------------------------------------------------------------------------

    private static void rebuild() {
        if (ObjTypeList.instance == null) {
            return;
        }
        rebuild(new IntFunction<String>() {
            @Override
            public String apply(int id) {
                ObjType type = ObjTypeList.instance.list(id);
                return type == null || type.name == null ? "null" : type.name;
            }
        });
    }

    /** Rebuilds the labels from the client's ground-item state; {@code names} resolves item names (the cache in game). */
    static void rebuild(IntFunction<String> names) {
        if (configRevision != RspsHudConfig.revision) {
            configRevision = RspsHudConfig.revision;
            dirty = true;
        }
        if (dirty) {
            dirty = false;
            dirtyTiles.clear();
            labels.clear();
            // Server entries of stacks the client no longer holds (map rebuild, zone reset) go with them.
            HashSet<Long> live = new HashSet<Long>();
            for (ObjStack stack = (ObjStack) Static497.objStacks.first(); stack != null; stack = (ObjStack) Static497.objStacks.next()) {
                for (ObjStackEntry entry = (ObjStackEntry) stack.objs.first(); entry != null; entry = (ObjStackEntry) stack.objs.next()) {
                    live.add(serverKey(stack.key, entry.id));
                }
                buildTile(stack.key, stack, names);
            }
            SERVER.keySet().retainAll(live);
            freshDrops.retainAll(live);
            return;
        }
        Set<Long> emptied = null;
        for (Long key : dirtyTiles) {
            labels.remove(key);
            ObjStack stack = (ObjStack) Static497.objStacks.get(key);
            if (stack != null) {
                buildTile(key, stack, names);
            } else {
                if (emptied == null) {
                    emptied = new HashSet<Long>();
                }
                emptied.add(key);
            }
        }
        dirtyTiles.clear();
        if (emptied != null) {
            final Set<Long> gone = emptied;
            SERVER.keySet().removeIf(k -> gone.contains(k & 0xFFFFFFFFL));
            freshDrops.removeIf(k -> gone.contains(k & 0xFFFFFFFFL));
        }
    }

    /** The price the tier and "Hide under value" use, per "Highlight value calculation". */
    private static long tierValue(long ge, long ha) {
        String mode = RspsHudConfig.get("grounditems.highlightValueCalculation");
        if (mode.equals("HA")) {
            return ha;
        }
        if (mode.equals("GE")) {
            return ge;
        }
        return Math.max(ge, ha);
    }

    private static void buildTile(long key, ObjStack stack, IntFunction<String> names) {
        int level = (int) ((key >> 28) & 0x3L);
        int x = (int) (key & 0x3FFFL);
        int z = (int) ((key >> 14) & 0x3FFFL);
        Map<Integer, long[]> counts = new HashMap<Integer, long[]>();
        List<Integer> order = new ArrayList<Integer>();
        for (ObjStackEntry entry = (ObjStackEntry) stack.objs.first(); entry != null; entry = (ObjStackEntry) stack.objs.next()) {
            long[] count = counts.get(entry.id);
            if (count == null) {
                counts.put(entry.id, new long[]{entry.count});
                order.add(entry.id);
            } else {
                count[0] += entry.count;
            }
        }
        boolean onlyHighlighted = RspsHudConfig.bool("grounditems.showHighlightedOnly");
        boolean onlyMine = RspsHudConfig.is("grounditems.ownershipFilterMode", "DROPS");
        long hideUnder = RspsHudConfig.integer("grounditems.hideUnderValue");
        boolean keepUntradeables = RspsHudConfig.bool("grounditems.dontHideUntradeables");
        String prices = RspsHudConfig.get("grounditems.priceDisplayMode");
        int beamTier = RspsHudConfig.tierSetting("grounditems.showLootbeamTier");
        boolean beamHighlighted = RspsHudConfig.bool("grounditems.showLootbeamForHighlighted");
        List<Label> tile = new ArrayList<Label>();
        for (int i = 0; i < order.size(); i++) {
            int id = order.get(i);
            long count = counts.get(id)[0];
            String name = names.apply(id);
            long serverKey = serverKey(key, id);
            long[] server = SERVER.get(serverKey);
            if (server != null && server.length > 4 && server[4] > 0L) {
                count = server[4];
            }
            long ge = server == null ? 0L : server[1] * count;
            long ha = server == null ? 0L : server[2] * count;
            long flags = server == null ? 0L : server[3];
            boolean coins = name.equalsIgnoreCase("Coins");
            Label label = new Label();
            label.level = level;
            label.x = x;
            label.z = z;
            label.id = id;
            label.name = name;
            label.count = count;
            label.serverKey = serverKey;
            label.value = tierValue(ge, ha);
            label.highlighted = RspsHudConfig.isHighlighted(name);
            boolean underValue = hideUnder > 0L && server != null && Math.max(ge, ha) < hideUnder
                && !((flags & FLAG_UNTRADEABLE) != 0 && keepUntradeables);
            label.listedHidden = !label.highlighted && RspsHudConfig.isHidden(name);
            label.hidden = label.listedHidden || (!label.highlighted && underValue);
            if (onlyHighlighted && !label.highlighted) {
                label.hidden = true;
            }
            if (onlyMine && (flags & FLAG_MINE) == 0) {
                continue;
            }
            int tier = RspsHudConfig.tier(label.value);
            if (label.hidden) {
                label.colour = RspsHudConfig.colour("grounditems.hiddenColor");
            } else if (label.highlighted) {
                label.colour = RspsHudConfig.colour("grounditems.highlightedColor");
            } else {
                label.colour = RspsHudConfig.tierColour(tier);
            }
            StringBuilder text = new StringBuilder(name);
            // GroundItemsOverlay: "Name (quantity)".
            if (count > 1L) {
                text.append(" (").append(RspsHud.formatValue(count)).append(")");
            }
            if (!coins && server != null) {
                if (prices.equals("BOTH")) {
                    if (ge > 0L) {
                        text.append(" (GE: ").append(RspsHud.formatValue(ge)).append(" gp)");
                    }
                    if (ha > 0L) {
                        text.append(" (HA: ").append(RspsHud.formatValue(ha)).append(" gp)");
                    }
                } else if (prices.equals("GE") && ge > 0L) {
                    text.append(" (").append(RspsHud.formatValue(ge)).append(" gp)");
                } else if (prices.equals("HA") && ha > 0L) {
                    text.append(" (").append(RspsHud.formatValue(ha)).append(" gp)");
                }
            }
            label.text = text.toString();
            tile.add(label);
            if (freshDrops.remove(serverKey)) {
                notifyDrop(label, tier);
            }
        }
        if (tile.isEmpty()) {
            return;
        }
        // GroundItemsPlugin.handleLootbeam: one beam per tile - a highlighted item (when "Show lootbeam for highlighted items" is on)
        // in the highlight colour, else the most valuable item not on the hidden list when it is above the beam tier, in its tier's
        // colour. Under-value hiding and the label overlay do not matter.
        Label beam = null;
        if (beamHighlighted) {
            for (Label label : tile) {
                if (label.highlighted) {
                    beam = label;
                    beam.beamColour = RspsHudConfig.colour("grounditems.highlightedColor");
                    break;
                }
            }
        }
        if (beam == null) {
            for (Label label : tile) {
                if (!label.listedHidden && (beam == null || label.value > beam.value)) {
                    beam = label;
                }
            }
            int tier = beam == null ? RspsHudConfig.TIER_NONE : RspsHudConfig.tier(beam.value);
            if (beam != null && tier != RspsHudConfig.TIER_NONE && tier >= beamTier) {
                beam.beamColour = RspsHudConfig.tierColour(tier);
            } else {
                beam = null;
            }
        }
        if (beam != null) {
            beam.beam = true;
        }
        // Most valuable at the bottom, nearest the item, like RuneLite's stacking.
        Collections.sort(tile, new Comparator<Label>() {
            @Override
            public int compare(Label a, Label b) {
                return Long.compare(b.value, a.value);
            }
        });
        for (int i = 0; i < tile.size(); i++) {
            tile.get(i).row = i;
        }
        labels.put(key, tile);
    }

    /** RuneLite notifyHighlightedDrops / notifyTier, for this player's own fresh drops. */
    private static void notifyDrop(Label label, int tier) {
        String kind = null;
        if (label.highlighted && RspsHudConfig.bool("grounditems.notifyHighlightedDrops")) {
            kind = "highlighted";
        } else if (tier >= RspsHudConfig.tierSetting("grounditems.notifyTier")) {
            kind = "valuable";
        }
        if (kind == null) {
            return;
        }
        // GroundItemsPlugin.notifyHighlightedItem: "You received a highlighted / valuable drop: Name (quantity)", no price.
        StringBuilder message = new StringBuilder("You received a ").append(kind).append(" drop: ").append(label.name);
        if (label.count > 1L) {
            message.append(" (").append(RspsHud.formatValue(label.count)).append(")");
        }
        RspsHud.notify(message.toString());
    }

    // ---- right-click menu (MiniMenu) ------------------------------------------------------------------------------------

    private static Label labelFor(int level, int x, int z, int id) {
        List<Label> tile = labels.get(tileKey(level, x, z));
        if (tile == null) {
            return null;
        }
        for (Label label : tile) {
            if (label.id == id) {
                return label;
            }
        }
        return null;
    }

    private static boolean menuColours() {
        String mode = RspsHudConfig.get("grounditems.itemHighlightMode");
        return mode.equals("MENU") || mode.equals("BOTH");
    }

    private static String hex(int argb) {
        String hex = Integer.toHexString(argb & 0xFFFFFF);
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return hex;
    }

    /** The ground item's menu option ("Take"), coloured when "Menu highlight mode" includes the option. */
    static String menuOption(int level, int x, int z, int id, String option) {
        if (!RspsHudConfig.enabled("grounditems")) {
            return option;
        }
        Label label = labelFor(level, x, z, id);
        if (label == null || !menuColours()) {
            return option;
        }
        String mode = RspsHudConfig.get("grounditems.menuHighlightMode");
        if ((mode.equals("OPTION") || mode.equals("BOTH")) && colouredInMenu(label)) {
            return "<col=" + hex(menuColour(label)) + ">" + option;
        }
        return option;
    }

    private static boolean colouredInMenu(Label label) {
        if (label.hidden) {
            return RspsHudConfig.bool("grounditems.recolorMenuHiddenItems");
        }
        return label.highlighted || RspsHudConfig.tier(label.value) > RspsHudConfig.TIER_NONE;
    }

    private static int menuColour(Label label) {
        return label.hidden ? RspsHudConfig.colour("grounditems.hiddenColor") : label.colour;
    }

    /**
     * The ground item's menu target: the name (coloured per the value tier / highlight, or grey when hidden and "Recolor
     * menu hidden items" is on), with " (quantity)" for a stack when "Show menu item quantities" is on and " x N" for
     * collapsed entries.
     */
    static String menuTarget(int level, int x, int z, int id, String name, long count, int collapsed) {
        String target = name;
        if (RspsHudConfig.enabled("grounditems")) {
            if (count > 1L && RspsHudConfig.bool("grounditems.showMenuItemQuantities")) {
                target = target + " (" + RspsHud.formatValue(count) + ")";
            }
            if (collapsed > 1) {
                target = target + " x " + collapsed;
            }
            Label label = labelFor(level, x, z, id);
            String mode = RspsHudConfig.get("grounditems.menuHighlightMode");
            if (label != null && menuColours() && (mode.equals("NAME") || mode.equals("BOTH")) && colouredInMenu(label)) {
                return "<col=" + hex(menuColour(label)) + ">" + target;
            }
        }
        return "<col=ff9040>" + target;
    }

    /** "Deprioritize menu hidden items": the option goes below Walk here (RS menu action + 2000). */
    static boolean deprioritize(int level, int x, int z, int id) {
        if (!RspsHudConfig.enabled("grounditems") || !RspsHudConfig.bool("grounditems.deprioritizeHiddenItems")) {
            return false;
        }
        Label label = labelFor(level, x, z, id);
        return label != null && label.hidden;
    }

    static boolean collapse() {
        return RspsHudConfig.enabled("grounditems") && RspsHudConfig.bool("grounditems.collapseEntries");
    }

    // ---- tests --------------------------------------------------------------------------------------------------------

    /** Test view of the current labels: "level,x,z row text #colour" (hidden ones with a trailing " hidden"). */
    static List<String> describe() {
        List<String> out = new ArrayList<String>();
        for (List<Label> tile : labels.values()) {
            for (Label l : tile) {
                out.add(l.level + "," + l.x + "," + l.z + " " + l.row + " " + l.text + " #" + Integer.toHexString(l.colour) + (l.hidden ? " hidden" : "") + (l.beam ? " beam" : ""));
            }
        }
        return out;
    }

    static boolean hasServerEntry(int level, int x, int z, int id) {
        return SERVER.containsKey(serverKey(tileKey(level, x, z), id));
    }

    static boolean isDirty() {
        return dirty || !dirtyTiles.isEmpty() || configRevision != RspsHudConfig.revision;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** Screen point of a world spot (fine local coordinates) at [height] units above the ground, or null off screen. */
    private static int[] project(int level, int fineX, int fineZ, int height, int screenX, int screenY) {
        Static418.method7860(level, fineZ, height, fineX, 0);
        if (OverlayManager.hitmarkpos[0] < 0) {
            return null;
        }
        return new int[]{screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1]};
    }

    /** Scene pass: the scene camera is set and the toolkit is clipped to the viewport. */
    static void render(int screenX, int screenY) {
        if (isDirty()) {
            rebuild();
        }
        boxes.clear();
        if (labels.isEmpty()) {
            return;
        }
        boolean hotkey = hotkeyHeld();
        String mode = RspsHudConfig.get("grounditems.itemHighlightMode");
        boolean overlay = (mode.equals("OVERLAY") || mode.equals("BOTH")) && !hideAll;
        boolean tiles = RspsHudConfig.bool("grounditems.highlightTiles") && !hideAll;
        // Loot beams first: RuneLite's are scene objects, there whatever the label overlay or the double-tap hide does.
        drawBeams(screenX, screenY);
        if (!overlay && !tiles && !hotkey) {
            return;
        }
        Font font = Fonts.p11 != null ? Fonts.p11 : RspsHud.font();
        FontMetrics metrics = Fonts.p11Metrics != null ? Fonts.p11Metrics : RspsHud.metrics();
        boolean outline = RspsHudConfig.bool("grounditems.textOutline");
        String timers = RspsHudConfig.get("grounditems.groundItemTimers");
        int selfLevel = PlayerEntity.self.level;
        long now = RspsHud.now();
        for (List<Label> tile : labels.values()) {
            int row = 0;
            for (int i = 0; i < tile.size(); i++) {
                Label label = tile.get(i);
                if (label.level != selfLevel || (label.hidden && !hotkey)) {
                    continue;
                }
                int localX = label.x - WorldMap.areaBaseX;
                int localZ = label.z - WorldMap.areaBaseZ;
                if (localX < 0 || localZ < 0 || localX >= Static720.mapWidth || localZ >= Static501.mapLength) {
                    continue;
                }
                int fineX = (localX << 9) + 256;
                int fineZ = (localZ << 9) + 256;
                if (row == 0 && tiles) {
                    tileOutline(label.level, localX, localZ, label.colour, screenX, screenY);
                }
                if (!overlay && !hotkey) {
                    row++;
                    continue;
                }
                int[] at = project(label.level, fineX, fineZ, LABEL_HEIGHT, screenX, screenY);
                if (at == null) {
                    row++;
                    continue;
                }
                int baseline = at[1] - row * LINE;
                long[] server = SERVER.get(label.serverKey);
                String timer = null;
                boolean pie = false;
                int timerColour = PUBLIC_TIMER;
                if (server != null && server[0] > now && !timers.equals("OFF")) {
                    timerColour = (server[3] & FLAG_PRIVATE) != 0 ? PRIVATE_TIMER : PUBLIC_TIMER;
                    long left = server[0] - now;
                    if (timers.equals("SECONDS")) {
                        timer = String.format(java.util.Locale.ENGLISH, " - %.1f", left / 1000f);
                    } else if (timers.equals("TICKS")) {
                        timer = " - " + (left / RspsHud.TICK_MS);
                    } else {
                        pie = true;
                    }
                }
                int textWidth = RspsHud.width(metrics, label.text);
                int timerWidth = timer == null ? (pie ? PIE + 3 : 0) : RspsHud.width(metrics, timer);
                int left = at[0] - (textWidth + timerWidth) / 2;
                RspsHud.text(font, label.text, left, baseline, label.colour, outline);
                if (timer != null) {
                    RspsHud.text(font, timer, left + textWidth, baseline, timerColour, outline);
                } else if (pie) {
                    long total = Math.max(1L, despawnTicks * RspsHud.TICK_MS);
                    pie(left + textWidth + 3 + PIE / 2, baseline - 4, Math.min(1f, (server[0] - now) / (float) total), timerColour);
                }
                if (hotkey) {
                    // RuneLite: [-] hide box, then [+] highlight box, right of the text; filled when that state is on.
                    int bx = left + textWidth + timerWidth + 4;
                    int by = baseline - (BOX + 8) / 2;
                    drawBox(bx, by, label.colour, label.hidden && RspsHudConfig.isHidden(label.name), true);
                    drawBox(bx + BOX + 2, by, label.colour, label.highlighted, false);
                    boxes.add(new Object[]{bx, by, 0, label});
                    boxes.add(new Object[]{bx + BOX + 2, by, 1, label});
                }
                row++;
            }
        }
        if (hotkey) {
            boxesDrawnAt = System.currentTimeMillis();
        }
    }

    private static void drawBox(int x, int y, int colour, boolean filled, boolean hideBox) {
        Toolkit toolkit = Toolkit.active;
        if (filled) {
            toolkit.fillRect(x, y, BOX, BOX, (colour & 0xFFFFFF) | 0x80000000);
        }
        toolkit.outlineRect(x, y, BOX, BOX, colour);
        toolkit.line(x + 2, y + BOX / 2, x + BOX - 2, y + BOX / 2, 0xFFFFFFFF, 1);
        if (!hideBox) {
            toolkit.line(x + BOX / 2, y + 2, x + BOX / 2, y + BOX - 2, 0xFFFFFFFF, 1);
        }
    }

    /** RuneLite ProgressPieComponent: the remaining share of a disc, starting at twelve o'clock. */
    private static void pie(int cx, int cy, float remaining, int colour) {
        Toolkit toolkit = Toolkit.active;
        int radius = PIE / 2;
        toolkit.fillCircle(cx, cy, radius, 0x80000000);
        int steps = Math.round(remaining * 72);
        for (int i = 0; i <= steps; i++) {
            double angle = -Math.PI / 2 + i * (Math.PI * 2 / 72);
            toolkit.line(cx, cy, cx + (int) Math.round(Math.cos(angle) * radius), cy + (int) Math.round(Math.sin(angle) * radius), colour, 1);
        }
    }

    /** RuneLite "Highlight tiles": the tile's outline at ground height. */
    private static void tileOutline(int level, int localX, int localZ, int colour, int screenX, int screenY) {
        int[][] corners = {
            project(level, localX << 9, localZ << 9, 0, screenX, screenY),
            project(level, (localX + 1) << 9, localZ << 9, 0, screenX, screenY),
            project(level, (localX + 1) << 9, (localZ + 1) << 9, 0, screenX, screenY),
            project(level, localX << 9, (localZ + 1) << 9, 0, screenX, screenY),
        };
        for (int[] corner : corners) {
            if (corner == null) {
                return;
            }
        }
        for (int i = 0; i < 4; i++) {
            int[] a = corners[i];
            int[] b = corners[(i + 1) % 4];
            Toolkit.active.line(a[0], a[1], b[0], b[1], colour, 1);
        }
    }

    /** Every tile's loot beam on the player's level (GroundItemsPlugin.handleLootbeam picked it in buildTile). */
    private static void drawBeams(int screenX, int screenY) {
        int selfLevel = PlayerEntity.self.level;
        for (List<Label> tile : labels.values()) {
            for (Label label : tile) {
                if (!label.beam || label.level != selfLevel) {
                    continue;
                }
                int localX = label.x - WorldMap.areaBaseX;
                int localZ = label.z - WorldMap.areaBaseZ;
                if (localX < 0 || localZ < 0 || localX >= Static720.mapWidth || localZ >= Static501.mapLength) {
                    continue;
                }
                lootbeam(label.level, (localX << 9) + 256, (localZ << 9) + 256, label.beamColour, screenX, screenY);
            }
        }
    }

    /** A lootbeam: a light column from the tile, modern (a bright core in a soft glow) or legacy (a wide pale column). */
    private static void lootbeam(int level, int fineX, int fineZ, int colour, int screenX, int screenY) {
        int[] base = project(level, fineX, fineZ, 0, screenX, screenY);
        int[] top = project(level, fineX, fineZ, BEAM_HEIGHT, screenX, screenY);
        if (base == null || top == null || base[1] <= top[1]) {
            return;
        }
        int rgb = colour & 0xFFFFFF;
        int height = base[1] - top[1];
        if (RspsHudConfig.is("grounditems.lootbeamStyle", "LEGACY")) {
            Toolkit.active.fillRect(base[0] - 5, top[1], 11, height, 0x50000000 | rgb);
            Toolkit.active.outlineRect(base[0] - 5, top[1], 11, height, 0x90000000 | rgb);
        } else {
            Toolkit.active.fillRect(base[0] - 4, top[1], 9, height, 0x30000000 | rgb);
            Toolkit.active.fillRect(base[0] - 1, top[1], 3, height, 0xC0000000 | rgb);
        }
    }
}
