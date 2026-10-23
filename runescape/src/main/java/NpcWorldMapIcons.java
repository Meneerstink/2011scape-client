import com.jagex.game.runetek6.config.npctype.NPCType;
import com.jagex.game.runetek6.config.npctype.NPCTypeList;
import java.util.ArrayList;
import java.util.List;

/**
 * World-map icons of npcs that carry a map element (owner 2026-10-09 202308/213724/214334: "zorg ervoor dat de iconen
 * meebewegen ... ik ga nog npcs verplaatsen via devmode, mogelijk etage naar andere etage").
 *
 * The minimap already draws an npc's {@code NPCType.mapElement} at the npc's live position and only on its own level
 * (Jagex, {@link Minimap#draw}). The world map knows nothing of npcs: it draws its prebuilt landscape elements and the
 * {@code _staticelements} file, so a store icon written there stayed on the old tile after every move. The server now
 * sends {@code __RSPS_HUD__:npcicons|id,x,z,level;...} - the post of every such npc - on login and after every world edit;
 * each post becomes an ordinary {@link MapElementListEntry} of the npc type's map element, so drawing, hover text
 * (the element category's MAP_ELEMENT_MOUSEREPEAT script) and the Key filters stay Jagex's own.
 */
public final class NpcWorldMapIcons {

    /** id, x, z, level per post, as last sent by the server. */
    private static volatile int[] posts = new int[0];

    /**
     * N231 (owner 2026-10-23, the GE summoning obelisk): map element, x, z, level per spawned loc whose LocType carries a map
     * element ({@code __RSPS_HUD__:locicons|...}) - the minimap draws scene locs itself, the world map's data never has them.
     */
    private static volatile int[] locPosts = new int[0];

    /** The entries this class added to {@link WorldMap#elements}, so an update can take them out again. */
    private static final List<MapElementListEntry> added = new ArrayList<MapElementListEntry>();

    private NpcWorldMapIcons() {
    }

    static void update(String rest) {
        posts = parse(rest);
        refresh();
    }

    static void updateLocs(String rest) {
        locPosts = parse(rest);
        refresh();
    }

    private static int[] parse(String rest) {
        String[] parts = rest.isEmpty() ? new String[0] : rest.split(";");
        int[] next = new int[parts.length * 4];
        for (int i = 0; i < parts.length; i++) {
            String[] p = parts[i].split(",");
            for (int j = 0; j < 4; j++) {
                next[i * 4 + j] = Integer.parseInt(p[j].trim());
            }
        }
        return next;
    }

    private static void refresh() {
        // The map is open (or loaded): swap the entries now, as loadStaticElements would on the next load.
        if (WorldMap.area != null && WorldMap.staticElements != null) {
            removeAdded();
            addTo();
        }
    }

    static void reset() {
        posts = new int[0];
        locPosts = new int[0];
        removeAdded();
    }

    private static void removeAdded() {
        for (MapElementListEntry entry : added) {
            entry.unlink();
        }
        added.clear();
    }

    /** Called at the end of {@link WorldMap#loadStaticElements}: one entry per npc post that projects into the shown map. */
    static void addTo() {
        added.clear();
        int[] current = posts;
        int[] coord = new int[3];
        for (int i = 0; i + 3 < current.length; i += 4) {
            NPCType type = NPCTypeList.instance.list(current[i]);
            if (type == null || type.mapElement == -1) {
                continue;
            }
            // Any storey at its x/z, as Jagex's map data does for upstairs locs (WorldMap.method5075 reads levels 1-3 too: the bank
            // up in Lumbridge castle shows on the castle) - the surface map's chunks only take level 0 as their source.
            if (!WorldMap.area.projectDisplay(coord, current[i + 1], current[i + 2])) {
                continue;
            }
            add(type.mapElement, coord);
        }
        int[] locs = locPosts;
        for (int i = 0; i + 3 < locs.length; i += 4) {
            if (locs[i] < 0 || !WorldMap.area.projectDisplay(coord, locs[i + 1], locs[i + 2])) {
                continue;
            }
            add(locs[i], coord);
        }
    }

    private static void add(int element, int[] coord) {
        MapElementListEntry entry = new MapElementListEntry(element);
        entry.x = coord[1] - WorldMap.areaX;
        entry.z = coord[2] - WorldMap.areaZ;
        WorldMap.elements.addLast(entry);
        added.add(entry);
    }
}
