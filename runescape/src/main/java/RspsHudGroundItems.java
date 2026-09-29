import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Ground Items: name, quantity, value and despawn time over every ground item stack the client holds.
 *
 * The items themselves are the client's own ground-item state ({@link Static497#objStacks}), which the OBJ_ADD / OBJ_DEL /
 * OBJ_COUNT / OBJ_REVEAL zone packets, zone resets, map rebuilds and logout already keep exact - so a pickup, partial
 * pickup, despawn, teleport, region change or relog can never leave a label behind. Every place that changes that
 * state calls {@link #markDirty()}; the label list is rebuilt from it only then, not every frame.
 *
 * Value and despawn time come from the server (`gi` entries, one per tile and item id, sent right after the zone packet
 * they belong to): the value is the server's own price (price checker / GE guide), the despawn time its own ground-item
 * clock. Neither is guessed here.
 */
final class RspsHudGroundItems {

    /** World units above the ground at which the lowest label of a tile sits (a tile is 512 units wide). */
    private static final int LABEL_HEIGHT = 96;
    private static final int LINE = 13;
    private static final int TIMER_COLOUR = 0xFFC8C8C8;

    /** Server data per tile + item id: despawn end (ms, -1 = never) and unit value. */
    private static final Map<Long, long[]> SERVER = new HashMap<Long, long[]>();

    /** Labels per tile (tile key -> its labels, bottom row first). */
    private static final Map<Long, List<Label>> labels = new LinkedHashMap<Long, List<Label>>();
    /** Everything must be rebuilt (reset, zone reset, a setting changed). */
    private static boolean dirty = true;
    /** Only these tiles changed (an obj add / del / count / server entry): 100 players dropping loot rebuild a tile each. */
    private static final Set<Long> dirtyTiles = new HashSet<Long>();

    private static final class Label {
        int level;
        int x;
        int z;
        long serverKey;
        String text;
        int colour;
        long total;
        int row;
    }

    private RspsHudGroundItems() {
    }

    /** Everything is rebuilt on the next frame. */
    static void markDirty() {
        dirty = true;
    }

    /** Only the tile with this objStacks key (level << 28 | z << 14 | x) is rebuilt. */
    static void markTile(long tileKey) {
        dirtyTiles.add(tileKey);
    }

    static void markTile(int level, int x, int z) {
        dirtyTiles.add(tileKey(level, x, z));
    }

    static void reset() {
        SERVER.clear();
        labels.clear();
        dirtyTiles.clear();
        dirty = true;
    }

    private static long tileKey(int level, int x, int z) {
        return ((long) level << 28) | ((long) z << 14) | x;
    }

    private static long serverKey(long tileKey, int id) {
        return ((long) id << 32) | tileKey;
    }

    /** {@code level,x,z,id,ticks,value;...} - ticks -1 = never despawns, -2 = none of these left on the tile. */
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
            } else {
                long end = ticks < 0 ? -1L : RspsHud.now() + ticks * RspsHud.TICK_MS;
                SERVER.put(key, new long[]{end, Long.parseLong(f[5])});
            }
            markTile(key & 0xFFFFFFFFL);
        }
    }

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
        if (dirty) {
            dirty = false;
            dirtyTiles.clear();
            labels.clear();
            // Server entries of stacks the client no longer holds (map rebuild, zone reset) go with them; hidden items keep
            // theirs, so un-hiding one does not lose its despawn time.
            HashSet<Long> live = new HashSet<Long>();
            for (ObjStack stack = (ObjStack) Static497.objStacks.first(); stack != null; stack = (ObjStack) Static497.objStacks.next()) {
                for (ObjStackEntry entry = (ObjStackEntry) stack.objs.first(); entry != null; entry = (ObjStackEntry) stack.objs.next()) {
                    live.add(serverKey(stack.key, entry.id));
                }
                buildTile(stack.key, stack, names);
            }
            SERVER.keySet().retainAll(live);
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
        // A tile the client dropped (pickup, zone reset, map shift) takes its server entries with it.
        if (emptied != null) {
            final Set<Long> gone = emptied;
            SERVER.keySet().removeIf(k -> gone.contains(k & 0xFFFFFFFFL));
        }
    }

    private static void buildTile(long key, ObjStack stack, IntFunction<String> names) {
        int level = (int) ((key >> 28) & 0x3L);
        int x = (int) (key & 0x3FFFL);
        int z = (int) ((key >> 14) & 0x3FFFL);
        // One label per item id on the tile: separate non-stackable drops of the same item are one "x3" line.
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
        List<Label> tile = new ArrayList<Label>();
        for (int i = 0; i < order.size(); i++) {
            int id = order.get(i);
            long count = counts.get(id)[0];
            String name = names.apply(id);
            if (RspsHudConfig.isHidden(name)) {
                continue;
            }
            long serverKey = serverKey(key, id);
            long[] server = SERVER.get(serverKey);
            Label label = new Label();
            label.level = level;
            label.x = x;
            label.z = z;
            label.serverKey = serverKey;
            label.total = server == null ? -1L : server[1] * count;
            boolean highlighted = RspsHudConfig.isHighlighted(name);
            if (!highlighted && RspsHudConfig.hideUnder > 0L && label.total >= 0L && label.total < RspsHudConfig.hideUnder) {
                continue;
            }
            StringBuilder text = new StringBuilder(name);
            if (count > 1L) {
                text.append(" x").append(RspsHud.formatValue(count));
            }
            if (RspsHudConfig.value && label.total >= 0L) {
                text.append(" (").append(RspsHud.formatValue(label.total)).append(')');
            }
            label.text = text.toString();
            label.colour = highlighted ? RspsHudConfig.highlightColour() : RspsHudConfig.valueColour(Math.max(0L, label.total));
            tile.add(label);
        }
        if (tile.isEmpty()) {
            return;
        }
        // Most valuable at the bottom, nearest the item, like RuneLite's stacking.
        Collections.sort(tile, new Comparator<Label>() {
            @Override
            public int compare(Label a, Label b) {
                return Long.compare(b.total, a.total);
            }
        });
        for (int i = 0; i < tile.size(); i++) {
            tile.get(i).row = i;
        }
        labels.put(key, tile);
    }

    /** Test view of the current labels: "level,x,z row text #colour". */
    static List<String> describe() {
        List<String> out = new ArrayList<String>();
        for (List<Label> tile : labels.values()) {
            for (Label l : tile) {
                out.add(l.level + "," + l.x + "," + l.z + " " + l.row + " " + l.text + " #" + Integer.toHexString(l.colour));
            }
        }
        return out;
    }

    static boolean hasServerEntry(int level, int x, int z, int id) {
        return SERVER.containsKey(serverKey(tileKey(level, x, z), id));
    }

    static boolean isDirty() {
        return dirty || !dirtyTiles.isEmpty();
    }

    /** Scene pass: the scene camera is set and the toolkit is clipped to the viewport. */
    static void render(int screenX, int screenY) {
        if (dirty || !dirtyTiles.isEmpty()) {
            rebuild();
        }
        if (labels.isEmpty()) {
            return;
        }
        Font font = Fonts.p11 != null ? Fonts.p11 : RspsHud.font();
        FontMetrics metrics = Fonts.p11Metrics != null ? Fonts.p11Metrics : RspsHud.metrics();
        int selfLevel = PlayerEntity.self.level;
        long now = RspsHud.now();
        for (List<Label> tile : labels.values()) {
          for (int i = 0; i < tile.size(); i++) {
            Label label = tile.get(i);
            if (label.level != selfLevel) {
                continue;
            }
            int localX = label.x - WorldMap.areaBaseX;
            int localZ = label.z - WorldMap.areaBaseZ;
            if (localX < 0 || localZ < 0 || localX >= Static720.mapWidth || localZ >= Static501.mapLength) {
                continue;
            }
            Static418.method7860(label.level, (localZ << 9) + 256, LABEL_HEIGHT, (localX << 9) + 256, 0);
            if (OverlayManager.hitmarkpos[0] < 0) {
                continue;
            }
            int cx = screenX + OverlayManager.hitmarkpos[0];
            int baseline = screenY + OverlayManager.hitmarkpos[1] - label.row * LINE;
            String timer = null;
            if (RspsHudConfig.despawn) {
                long[] server = SERVER.get(label.serverKey);
                if (server != null && server[0] > 0L && server[0] > now) {
                    timer = RspsHud.countdown(server[0]);
                }
            }
            int textWidth = RspsHud.width(metrics, label.text);
            int timerWidth = timer == null ? 0 : RspsHud.width(metrics, " " + timer);
            int left = cx - (textWidth + timerWidth) / 2;
            font.render(label.text, left, baseline, RspsHud.SHADOW, label.colour);
            if (timer != null) {
                font.render(" " + timer, left + textWidth, baseline, RspsHud.SHADOW, TIMER_COLOUR);
            }
          }
        }
    }
}
