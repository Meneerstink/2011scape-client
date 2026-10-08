import com.jagex.core.util.TimeUtils;
import com.jagex.game.runetek6.config.npctype.NPCType;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.graphics.ClippingMask;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The Quest Helper's in-game hints, ported from the RuneLite Quest Helper (Zoinkwiz/quest-helper, BSD-2; owner
 * 2026-10-08: "make the questhelper fully function like osrs questhelper runelite"):
 * <ul>
 *   <li>world map: the quest icon at the step's world point (QuestHelperWorldMapPoint: the icon on the clue-arrow
 *       background), snapped to the map edge as a direction arrow when the point is outside the view;</li>
 *   <li>minimap: the quest icon on the target, or a flashing direction arrow on the minimap edge when it is further away
 *       (DetailedQuestStep.renderMinimapArrow / DirectionArrow.createMinimapDirectionArrow);</li>
 *   <li>game view: the step npc's tile outlined in the target colour (NpcStep, highlight style "Tile");</li>
 *   <li>chatbox: the right dialogue option in the text highlight colour, numbered "[n] " (WidgetChoiceStep.highlightText).</li>
 * </ul>
 * The server sends {@code qht|x|z|plane|npc} (bare {@code qht} clears) and {@code qhd|title~answer|...} (bare {@code qhd}
 * clears; {@code qhda|...} continues a long list) from QuestHelper.kt; matching of titles and answers is QuestDialogueHints' ("Start *", "#1", "x*", "*x").
 * The quest icon is the game's own: sprite 1692, the icon of the cache's "Start of &lt;quest&gt;" map elements.
 */
public final class RspsQuestHelperHints {

    /** RuneLite Quest Helper defaults: targetOverlayColor CYAN, textHighlightColor BLUE. */
    private static final int TARGET = 0xFF00FFFF;
    private static final int TEXT_HIGHLIGHT = 0x0000FF;
    private static final int QUEST_ICON = 1692;

    private static volatile int targetX = -1;
    private static volatile int targetZ = -1;
    private static volatile int targetPlane = -1;
    private static volatile int targetNpc = -1;
    private static volatile List<String[]> hints = new ArrayList<>();

    private static Toolkit builtFor;
    private static Sprite minimapIcon;
    private static Sprite worldIcon;
    private static final Sprite[] edgeArrows = new Sprite[8];

    private RspsQuestHelperHints() {
    }

    static void handle(String kind, String rest) {
        if (kind.equals("qht")) {
            String[] p = rest.split("\\|", -1);
            if (rest.isEmpty() || p.length < 4) {
                targetX = -1;
                targetNpc = -1;
                return;
            }
            targetZ = Integer.parseInt(p[1]);
            targetPlane = Integer.parseInt(p[2]);
            targetNpc = Integer.parseInt(p[3]);
            targetX = Integer.parseInt(p[0]);
        } else if (kind.equals("qhd") || kind.equals("qhda")) {
            // qhd starts the list, qhda continues it (one message holds at most 200 characters).
            List<String[]> next = kind.equals("qhda") ? new ArrayList<>(hints) : new ArrayList<>();
            if (!rest.isEmpty()) {
                for (String pair : rest.split("\\|", -1)) {
                    int at = pair.indexOf('~');
                    if (at > 0) {
                        next.add(new String[]{pair.substring(0, at), pair.substring(at + 1)});
                    }
                }
            }
            hints = next;
        }
    }

    static void reset() {
        targetX = -1;
        targetNpc = -1;
        hints = new ArrayList<>();
    }

    private static boolean active() {
        return targetX >= 0 && RspsHudConfig.enabled("questhelper");
    }

    // ---- sprites -----------------------------------------------------------------------------------------------------

    private static void build(Toolkit toolkit) {
        if (builtFor == toolkit) {
            return;
        }
        builtFor = toolkit;
        BufferedImage icon = crop(RspsSprites.image(QUEST_ICON));
        minimapIcon = icon == null ? null : sprite(toolkit, scale(icon, 15, 15));
        BufferedImage background = png("qh_clue_arrow");
        if (icon != null && background != null) {
            // QuestHelperWorldMapPoint: the quest image centred in the top of the clue-arrow background.
            BufferedImage marker = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = marker.createGraphics();
            g.drawImage(background, 0, 0, null);
            int size = background.getWidth() - 8;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(icon, 4, 4, size, size, null);
            g.dispose();
            worldIcon = sprite(toolkit, marker);
        }
        String[] names = {"", "_45", "_90", "_135", "_180", "_225", "_270", "_315"};
        for (int i = 0; i < 8; i++) {
            BufferedImage arrow = png("qh_quest_step_arrow" + names[i]);
            edgeArrows[i] = arrow == null ? null : sprite(toolkit, arrow);
        }
    }

    /** The sprite's drawn pixels only (sprite 1692 sits in a 52x52 canvas). */
    private static BufferedImage crop(BufferedImage image) {
        if (image == null) {
            return null;
        }
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage scale(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static BufferedImage png(String name) {
        try (java.io.InputStream in = RspsQuestHelperHints.class.getResourceAsStream("/runelite/" + name + ".png")) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** Same conversion as RspsSprites.resource: half-transparent pixels drop out, the rest are opaque. */
    private static Sprite sprite(Toolkit toolkit, BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (pixels[i] >>> 24) < 128 ? 0 : pixels[i] | 0xFF000000;
        }
        return toolkit.createSprite(width, width, height, pixels);
    }

    // ---- minimap (Minimap.draw, after the hint arrows) -------------------------------------------------------------------

    static void drawMinimap(Component component, ClippingMask mask, int screenX, int screenY, int selfX, int selfZ) {
        Toolkit toolkit = Toolkit.active;
        if (!active() || toolkit == null) {
            return;
        }
        build(toolkit);
        int drawX = ((targetX - WorldMap.areaBaseX) * 4 + 2) - selfX / 128;
        int drawZ = ((targetZ - WorldMap.areaBaseZ) * 4 + 2) - selfZ / 128;
        int radius = Math.min(component.width / 2, component.height / 2);
        double zoom = RspsMinimapZoom.factor();
        long distance = (long) drawX * drawX + (long) drawZ * drawZ;
        if (distance * zoom * zoom <= (double) radius * radius) {
            Minimap.drawDot(screenY, mask, minimapIcon, drawZ, drawX, component, screenX);
        } else if (TimeUtils.clock % 20 < 10 && Sprites.hintMapedge != null && Sprites.hintMapedge.length > 0) {
            // RuneLite: the direction arrow on the minimap edge flashes (haveMinimapArrowFlash).
            Minimap.drawHintMapedge(drawX, screenX, Long.MAX_VALUE, mask, 0, screenY, drawZ, component);
        }
    }

    // ---- world map (WorldMap.draw, after the grave marker) ---------------------------------------------------------------

    static void drawWorldMap(Toolkit toolkit) {
        if (!active() || toolkit == null) {
            return;
        }
        int sourceWidth = WorldMap.anInt5647 - WorldMap.anInt5652;
        int sourceHeight = WorldMap.anInt5645 - WorldMap.anInt5654;
        if (sourceWidth <= 0 || sourceHeight <= 0 || WorldMap.areaWidth <= 0 || WorldMap.areaHeight <= 0) {
            return;
        }
        // Another map area (a dungeon map while the point is on the surface): no marker, as RuneLite hides it.
        if (targetX < WorldMap.areaX || targetX >= WorldMap.areaX + WorldMap.areaWidth || targetZ < WorldMap.areaZ || targetZ >= WorldMap.areaZ + WorldMap.areaHeight) {
            return;
        }
        build(toolkit);
        int left = WorldMap.anInt5649;
        int top = WorldMap.anInt5653;
        int right = WorldMap.anInt5651;
        int bottom = WorldMap.anInt5646;
        double scaleX = (right - left) / (double) sourceWidth;
        double scaleZ = (bottom - top) / (double) sourceHeight;
        double originX = left - (WorldMap.areaX + WorldMap.anInt5652) * scaleX;
        double originZ = bottom + (WorldMap.areaZ + WorldMap.anInt5654) * scaleZ;
        int sx = (int) Math.round(originX + (targetX + 0.5) * scaleX);
        int sy = (int) Math.round(originZ - (targetZ + 0.5) * scaleZ);
        if (sx >= left && sx <= right && sy >= top && sy <= bottom) {
            if (worldIcon != null) {
                // The marker's point is its bottom centre (questWorldImagePoint).
                worldIcon.render(sx - worldIcon.getWidth() / 2, sy - worldIcon.getHeight());
            }
            return;
        }
        // Snapped to the edge: the arrow of the octant the point lies in (DirectionArrow.renderWorldMapArrow).
        int rotation;
        if (sx < left) {
            rotation = sy < top ? 225 : sy > bottom ? 135 : 180;
        } else if (sx > right) {
            rotation = sy < top ? 315 : sy > bottom ? 45 : 0;
        } else {
            rotation = sy < top ? 270 : 90;
        }
        Sprite arrow = edgeArrows[rotation / 45];
        if (arrow == null) {
            return;
        }
        int ax = Math.max(left, Math.min(right - arrow.getWidth(), sx - arrow.getWidth() / 2));
        int ay = Math.max(top, Math.min(bottom - arrow.getHeight(), sy - arrow.getHeight() / 2));
        arrow.render(ax, ay);
    }

    // ---- game view (RspsHud.render) -----------------------------------------------------------------------------------

    /** NpcStep: every loaded npc of the step's id on this floor gets its tiles outlined in the target colour. */
    static void drawNpcHighlight(int screenX, int screenY) {
        if (!active() || targetNpc < 0 || PlayerEntity.self == null || Toolkit.active == null) {
            return;
        }
        int selfLevel = PlayerEntity.self.level;
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null) {
                continue;
            }
            NPCEntity npc = node.npc;
            if (!npc.hasType() || npc.level != selfLevel) {
                continue;
            }
            NPCType type = npc.type;
            if (type != null && type.multinpcs != null) {
                type = type.getMultiNPC(TimedVarDomain.instance);
            }
            if (type == null || type.id != targetNpc) {
                continue;
            }
            int size = Math.max(1, type.size);
            // npc.x / npc.z are the fine centre of its footprint; the outline spans its size.
            int fineLeft = npc.x - size * 256;
            int fineTop = npc.z - size * 256;
            int fineRight = npc.x + size * 256;
            int fineBottom = npc.z + size * 256;
            int[][] corners = {
                project(npc.level, fineLeft, fineTop, screenX, screenY),
                project(npc.level, fineRight, fineTop, screenX, screenY),
                project(npc.level, fineRight, fineBottom, screenX, screenY),
                project(npc.level, fineLeft, fineBottom, screenX, screenY),
            };
            boolean visible = true;
            for (int[] corner : corners) {
                visible &= corner != null;
            }
            if (!visible) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                int[] a = corners[c];
                int[] b = corners[(c + 1) % 4];
                Toolkit.active.line(a[0], a[1], b[0], b[1], TARGET, 1);
                Toolkit.active.line(a[0], a[1] + 1, b[0], b[1] + 1, TARGET, 1);
            }
        }
    }

    /** Same projection as RspsHudGroundItems.project: a fine local point at ground height, or null off screen. */
    private static int[] project(int level, int fineX, int fineZ, int screenX, int screenY) {
        Static418.method7860(level, fineZ, 0, fineX, 0);
        if (OverlayManager.hitmarkpos[0] < 0) {
            return null;
        }
        return new int[]{screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1]};
    }

    // ---- chatbox options (InterfaceManager, text components) -----------------------------------------------------------

    /** Option dialogues are interfaces 228..240 (2..8 options): component 1 is the title, 2.. are the options. */
    private static int option(Component child) {
        int iface = child.slot >>> 16;
        int comp = child.slot & 0xFFFF;
        if (iface < 228 || iface > 240 || (iface & 1) != 0) {
            return -1;
        }
        int count = (iface - 224) / 2;
        return comp >= 2 && comp < 2 + count ? comp - 1 : -1;
    }

    private static boolean matches(String pattern, String text) {
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return text.endsWith(pattern.substring(1));
        }
        return text.equals(pattern);
    }

    private static boolean highlighted(Component child, String text) {
        List<String[]> current = hints;
        if (current.isEmpty() || !RspsHudConfig.enabled("questhelper")) {
            return false;
        }
        int n = option(child);
        if (n < 0 || text == null) {
            return false;
        }
        Component titleComponent = InterfaceList.list((child.slot & 0xFFFF0000) | 1);
        String title = titleComponent == null || titleComponent.text == null ? "" : titleComponent.text;
        for (String[] hint : current) {
            if (!matches(hint[0], title)) {
                continue;
            }
            String answer = hint[1];
            if (answer.startsWith("#") ? answer.equals("#" + n) : matches(answer, text)) {
                return true;
            }
        }
        return false;
    }

    /** WidgetChoiceStep.highlightText: "[n] " before the right option. */
    static String dialogueText(Component child, String text) {
        return highlighted(child, text) ? "[" + option(child) + "] " + text : text;
    }

    /** WidgetChoiceStep.highlightText: the right option in the text highlight colour. */
    static int dialogueColour(Component child, String text, int colour) {
        return highlighted(child, text) ? TEXT_HIGHLIGHT : colour;
    }
}
