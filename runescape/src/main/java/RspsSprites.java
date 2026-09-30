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
     * sprites): poison = the cache's own poison hitmark (type 6) inner sprite, venom / burn = the OSRS hitsplat art the client
     * already ships in /hitsplats (VenomHitmarkType). Negative ids name those resources.
     */
    static final int POISON_HITMARK_TYPE = 6;
    static final int RES_VENOM = -1001;
    static final int RES_BURN = -1002;

    static int poisonSprite() {
        try {
            com.jagex.game.runetek6.config.hitmarktype.HitmarkTypeList list =
                com.jagex.game.runetek6.config.hitmarktype.HitmarkTypeList.instance;
            if (list == null) {
                return -1;
            }
            com.jagex.game.runetek6.config.hitmarktype.HitmarkType type = list.list(POISON_HITMARK_TYPE);
            return type == null ? -1 : type.inner;
        } catch (RuntimeException notLoaded) {
            return -1;
        }
    }

    private static Sprite resource(Toolkit toolkit, int id) {
        String name = id == RES_VENOM ? "venom" : id == RES_BURN ? "burn" : null;
        if (name == null) {
            return null;
        }
        try (java.io.InputStream in = RspsSprites.class.getResourceAsStream("/hitsplats/" + name + ".png")) {
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
