import com.jagex.IndexedImage;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import com.jagex.js5.js5;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Icons of the information overlays, all from this revision-667 cache (sprite archive), found through the interfaces that
 * show them (2026-09-29, tools read-out of interfaces 320 / 192 / 193 / 430):
 *
 *  - skills: the stats tab (interface 320) - Attack 197, Strength 198, Defence 199, Ranged 200, Prayer 201, Magic 202,
 *    Constitution 203, Agility 204, Herblore 205, Thieving 206, Crafting 207, Fletching 208, Mining 209, Smithing 210,
 *    Fishing 211, Cooking 212, Firemaking 213, Woodcutting 214, Runecrafting 215, Slayer 216, Farming 217, Hunter 220,
 *    Construction 221, Summoning 222, Dungeoneering 3028;
 *  - spells, the coloured (castable) icon of each spellbook button (the button's own sprite is the greyed one):
 *    Vengeance 564 (430:37 shows 614), Vengeance Other 561 (611), Tele Block 1565 (192:86 shows 1571),
 *    Ice Barrage 328 (193:23 shows 378);
 *  - buff / debuff change: the orange up / down arrows 1517 / 1518.
 *
 * Game sprites are made per toolkit (a renderer switch makes new ones); the sidebar gets plain Java images.
 */
final class RspsSprites {

    static final int ATTACK = 197;
    static final int VENGEANCE = 564;
    static final int VENGEANCE_OTHER = 561;
    static final int TELE_BLOCK = 1565;
    static final int ICE_BARRAGE = 328;
    static final int ARROW_UP = 1517;
    static final int ARROW_DOWN = 1518;
    /**
     * Damage-over-time infoboxes carry the hitsplat the damage lands as (RuneLite PoisonPlugin uses the poison / venom hitsplat
     * sprites): poison = the revision-667 poison hitmark art (sprite 4475, the 667 hitmark 6 inner tile, kept in the sprite
     * archive when the OSRS hitsplats replaced the hitmark definitions on 2026-10-09), venom / burn = the OSRS hitsplat art in
     * /hitsplats. Negative ids name those resources.
     */
    static final int POISON_HITMARK_SPRITE = 4475;
    static final int RES_VENOM = -1001;
    static final int RES_BURN = -1002;
    /** RuneLite Boosts' own infobox images (plugins/boosts/debuffed.png, buffed.png), in /boosts. */
    static final int RES_DEBUFFED = -1003;
    static final int RES_BUFFED = -1004;
    /** N72: the OSRS spell-filter menu tick / cross (OSRS cache 2686 sprites 8384 / 8383, script 2606). */
    static final int RES_FILTER_ON = -1005;
    static final int RES_FILTER_OFF = -1006;
    /**
     * N482 / N72 / N491: OSRS interface art (OSRS cache 2686 sprite archive, exported read-only with the canvas offsets kept) in
     * /osrs: the Filters button tiles (1141-1149 grey "options_button_off", 1150-1158 red "options_button_on"), the filter menu's
     * stone frame (v2_borders_slim 987-997) and dark tile (tradebacking_dark 897), the radio tick / cross / neutral (8384 / 8383 /
     * 8380) and the Ruinous Powers prayer tab icon (sideicons_interface,56 = 4834, OSRS script 907). {@link #osrs} names one.
     */
    static final String[] OSRS_RESOURCES = {
        "btn-off-0", "btn-off-1", "btn-off-2", "btn-off-3", "btn-off-4", "btn-off-5", "btn-off-6", "btn-off-7", "btn-off-8",
        "btn-on-0", "btn-on-1", "btn-on-2", "btn-on-3", "btn-on-4", "btn-on-5", "btn-on-6", "btn-on-7", "btn-on-8",
        "border-0", "border-1", "border-2", "border-3", "border-4", "border-5", "border-6", "border-7", "border-8", "border-9", "border-10",
        "tradebacking-dark", "radio-tick", "radio-cross", "radio-neutral", "ruinous-tab",
    };
    static final int OSRS_BASE = -1100;

    /** The resource id of /osrs/[name].png (see {@link #OSRS_RESOURCES}). */
    static int osrs(String name) {
        for (int i = 0; i < OSRS_RESOURCES.length; i++) {
            if (OSRS_RESOURCES[i].equals(name)) {
                return OSRS_BASE - i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    /** True for a graphic id this class draws from a resource (Component.sprite asks). */
    static boolean isResource(int graphic) {
        return graphic == RES_FILTER_ON || graphic == RES_FILTER_OFF || (graphic <= OSRS_BASE && graphic > OSRS_BASE - OSRS_RESOURCES.length);
    }

    /**
     * The OSRS hitsplat sprites RuneLite's infoboxes use, as OsrsHitsplatImportTool put them in this cache (tx-20261009-035259):
     * OSRS 1360 HITSPLAT_GREEN_POISON -> 8405, 1632 HITSPLAT_DARK_GREEN_VENOM -> 8415, 4767 BURN_DAMAGE -> 8443.
     */
    static final int OSRS_POISON_SPLAT = 8405;
    static final int OSRS_VENOM_SPLAT = 8415;
    static final int OSRS_BURN_SPLAT = 8443;

    /** Item icons (RuneLite GameTimer ITEM images): {@link #item(int)} encodes an item id below this. */
    private static final int ITEM_BASE = -100_000;

    static int item(int itemId) {
        return ITEM_BASE - itemId;
    }

    /** Items whose icon is queued for rendering (N219), so a missing icon is asked for once, not every frame. */
    private static final java.util.Set<Integer> requested = new java.util.HashSet<Integer>();

    /**
     * N219 (owner 2026-10-23: the Antipoison / Anti-venom timer showed its countdown without the potion): the client keeps item icons
     * only once an interface has drawn them (ObjTypeList cache); an item that is in no open interface had none. Like the client's
     * own interface code (Static485), a miss queues the icon for the next frame's sprite pass (Static369.updateObjSprites), so it
     * appears one frame later and stays cached.
     */
    static Sprite itemIcon(Toolkit toolkit, int itemId) {
        com.jagex.game.runetek6.config.objtype.ObjTypeList list = com.jagex.game.runetek6.config.objtype.ObjTypeList.instance;
        if (list == null || toolkit == null) {
            return null;
        }
        Sprite icon;
        try {
            icon = list.getCachedSprite(null, toolkit, com.jagex.game.runetek6.config.objtype.ObjNumMode.SHOWCOUNT_NEVER, itemId, 1, 1, 0xFF000000);
        } catch (RuntimeException notLoaded) {
            return null;
        }
        if (icon != null) {
            requested.remove(itemId);
        } else if (requested.add(itemId)) {
            JavaToolkit.objSprites.addLast(new com.jagex.graphics.JavaObjSprite(itemId, 1, 1, 0xFF000000, com.jagex.game.runetek6.config.objtype.ObjNumMode.SHOWCOUNT_NEVER, false));
        }
        return icon;
    }

    /** Tests: whether [itemId]'s icon has been queued and not seen rendered yet. */
    static boolean iconRequested(int itemId) {
        return requested.contains(itemId);
    }

    static int poisonSprite() {
        return POISON_HITMARK_SPRITE;
    }

    private static Sprite resource(Toolkit toolkit, int id) {
        String name = id == RES_VENOM ? "hitsplats/venom" : id == RES_BURN ? "hitsplats/burn"
            : id == RES_DEBUFFED ? "boosts/debuffed" : id == RES_BUFFED ? "boosts/buffed"
            : id == RES_FILTER_ON ? "spellbook/filter-on" : id == RES_FILTER_OFF ? "spellbook/filter-off"
            : id <= OSRS_BASE && id > OSRS_BASE - OSRS_RESOURCES.length ? "osrs/" + OSRS_RESOURCES[OSRS_BASE - id] : null;
        if (name == null) {
            return null;
        }
        try (java.io.InputStream in = RspsSprites.class.getResourceAsStream("/" + name + ".png")) {
            if (in == null) {
                return null;
            }
            BufferedImage png = javax.imageio.ImageIO.read(in);
            if (png == null) {
                return null;
            }
            int width = png.getWidth();
            int height = png.getHeight();
            int[] pixels = png.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] >>> 24) < 128 ? 0 : pixels[i] | 0xFF000000;
            }
            return toolkit.createSprite(width, width, height, pixels);
        } catch (Exception e) {
            return null;
        }
    }

    /** Skill id (client order) -> stats-tab icon. */
    private static final int[] SKILL_ICONS = {
        197, 199, 198, 203, 200, 201, 202, 212, 214, 208, 211, 213, 207, 210, 209, 205, 204, 206, 216, 217, 215, 220, 221, 222, 3028,
    };

    private static final Map<Integer, Sprite> sprites = new HashMap<Integer, Sprite>();
    private static Toolkit builtFor;

    private RspsSprites() {
    }

    static int skillIcon(int skill) {
        return skill >= 0 && skill < SKILL_ICONS.length ? SKILL_ICONS[skill] : ATTACK;
    }

    /** The game sprite for [id], or null while the sprite archive cannot deliver it yet (asked again next frame). */
    static Sprite sprite(int id) {
        Toolkit toolkit = Toolkit.active;
        if (toolkit == null) {
            return null;
        }
        if (builtFor != toolkit) {
            sprites.clear();
            builtFor = toolkit;
        }
        Sprite sprite = sprites.get(id);
        if (sprite == null && id <= ITEM_BASE) {
            // The inventory icon without a count, as RuneLite's ItemManager.getImage (DeadmanSkullHud draws emblems the same way).
            sprite = itemIcon(toolkit, ITEM_BASE - id);
            if (sprite != null) {
                sprites.put(id, sprite);
            }
            return sprite;
        }
        if (sprite == null && id < 0) {
            sprite = resource(toolkit, id);
            if (sprite != null) {
                sprites.put(id, sprite);
            }
            return sprite;
        }
        if (sprite == null) {
            try {
                IndexedImage image = IndexedImage.loadFirst(js5.SPRITES, id);
                if (image != null) {
                    sprite = toolkit.createSprite(image, true);
                    sprites.put(id, sprite);
                }
            } catch (RuntimeException notLoaded) {
                return null;
            }
        }
        return sprite;
    }

    private static final Map<Integer, int[]> contents = new HashMap<Integer, int[]>();

    /**
     * Where the visible pixels of cache sprite [id] lie on its canvas: x, y, width, height - or null for resources and items (and
     * while the archive is not loaded). The OSRS splats are 25 x 25 canvases holding 24 x 23 of art at the top left, so centring the
     * canvas puts the art half a pixel off; the poison infobox centres the art itself.
     */
    static int[] content(int id) {
        if (id < 0) {
            return null;
        }
        int[] known = contents.get(id);
        if (known != null) {
            return known;
        }
        try {
            IndexedImage image = IndexedImage.loadFirst(js5.SPRITES, id);
            if (image == null) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.height; y++) {
                for (int x = 0; x < image.width; x++) {
                    int index = y * image.width + x;
                    boolean visible = image.alpha != null ? (image.alpha[index] & 0xFF) != 0 : image.raster[index] != 0;
                    if (visible) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0) {
                return null;
            }
            int[] bounds = {image.offX1 + minX, image.offY1 + minY, maxX - minX + 1, maxY - minY + 1};
            contents.put(id, bounds);
            return bounds;
        } catch (RuntimeException notLoaded) {
            return null;
        }
    }

    /** The same icon as a Java image (the sidebar), or null when the sprite archive is not loaded yet. */
    static BufferedImage image(int id) {
        try {
            IndexedImage image = IndexedImage.loadFirst(js5.SPRITES, id);
            if (image == null) {
                return null;
            }
            int width = image.offX1 + image.width + image.offX2;
            int height = image.offY1 + image.height + image.offY2;
            BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.height; y++) {
                for (int x = 0; x < image.width; x++) {
                    int index = y * image.width + x;
                    int colour = image.palette[image.raster[index] & 0xFF];
                    int alpha = image.alpha != null ? image.alpha[index] & 0xFF : (image.raster[index] == 0 ? 0 : 255);
                    if (alpha != 0) {
                        out.setRGB(image.offX1 + x, image.offY1 + y, (alpha << 24) | (colour & 0xFFFFFF));
                    }
                }
            }
            return out;
        } catch (RuntimeException notLoaded) {
            return null;
        }
    }
}
