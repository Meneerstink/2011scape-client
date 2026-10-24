import com.jagex.Client;
import com.jagex.ClientConfig;
import com.jagex.ClientProt;
import com.jagex.FullscreenMode;
import com.jagex.core.constants.ComponentClientCode;
import com.jagex.core.constants.MainLogicStep;
import com.jagex.core.constants.MaxScreenSize;
import com.jagex.core.constants.MiniMenuAction;
import com.jagex.core.constants.ModeGame;
import com.jagex.core.constants.ModeWhere;
import com.jagex.core.constants.WindowMode;
import com.jagex.core.datastruct.key.Deque;
import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.core.datastruct.key.IterableHashTable;
import com.jagex.core.datastruct.key.Node;
import com.jagex.core.stringtools.general.StringTools;
import com.jagex.core.util.TimeUtils;
import com.jagex.game.Animator;
import com.jagex.game.LocalisedText;
import com.jagex.game.PlayerModel;
import com.jagex.game.camera.CameraMode;
import com.jagex.game.runetek6.client.GameShell;
import com.jagex.game.runetek6.config.bastype.BASTypeList;
import com.jagex.game.runetek6.config.defaults.GraphicsDefaults;
import com.jagex.game.runetek6.config.defaults.WearposDefaults;
import com.jagex.game.runetek6.config.idktype.IDKTypeList;
import com.jagex.game.runetek6.config.iftype.DragRenderBehaviour;
import com.jagex.game.runetek6.config.iftype.ServerActiveProperties;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import com.jagex.game.runetek6.config.iftype.SubInterfaceType;
import com.jagex.game.runetek6.config.iftype.TargetMask;
import com.jagex.game.runetek6.config.npctype.NPCTypeCustomisation;
import com.jagex.game.runetek6.config.npctype.NPCTypeList;
import com.jagex.game.runetek6.config.objtype.ObjStackability;
import com.jagex.game.runetek6.config.objtype.ObjType;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import com.jagex.game.runetek6.config.paramtype.ParamType;
import com.jagex.game.runetek6.config.paramtype.ParamTypeList;
import com.jagex.game.runetek6.config.seqtype.SeqTypeList;
import com.jagex.game.runetek6.config.skyboxspheretype.SkyBoxSphereTypeList;
import com.jagex.game.runetek6.config.skyboxtype.SkyBoxTypeList;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.game.world.World;
import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Model;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import com.jagex.js5.js5;
import com.jagex.math.Trig1;
import com.jagex.sign.SignLink;
import com.jagex.sign.SignedResource;
import com.jagex.sign.SignedResourceStatus;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;
import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.awt.Container;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Rectangle;

public final class InterfaceManager {

    private static final int ROOT = 0xABCDABCD;

    public static final int IMMEDIATE_HOOK_TYPE_DIALOGABORT = 0;

    public static final int IMMEDIATE_HOOK_TYPE_SUBCHANGE = 1;

    @OriginalMember(owner = "client!va", name = "J", descriptor = "[Ljava/awt/Rectangle;")
    public static final Rectangle[] flippedDirtyRects = new Rectangle[100];

    @OriginalMember(owner = "client!qga", name = "i", descriptor = "Lclient!sia;")
    public static final Deque hookRequests = new Deque();

    @OriginalMember(owner = "client!lia", name = "b", descriptor = "[Z")
    public static final boolean[] dirtyRectangles = new boolean[100];

    @OriginalMember(owner = "client!oq", name = "i", descriptor = "[Z")
    public static final boolean[] flipDirtyRect = new boolean[100];

    @OriginalMember(owner = "client!lfa", name = "a", descriptor = "[Z")
    public static final boolean[] currentlyDirtyRect = new boolean[100];

    @OriginalMember(owner = "client!je", name = "h", descriptor = "Lclient!av;")
    public static final IterableHashTable serverActiveProperties = new IterableHashTable(512);

    @OriginalMember(owner = "client!ff", name = "k", descriptor = "I")
    public static int lastDrawCycle = -2;

    @OriginalMember(owner = "client!cea", name = "u", descriptor = "Lclient!hda;")
    public static Component optionsComponent = null;

    @OriginalMember(owner = "client!fu", name = "p", descriptor = "I")
    public static int optionsY = -1;

    @OriginalMember(owner = "client!ww", name = "b", descriptor = "I")
    public static int optionsX = -1;

    @OriginalMember(owner = "client!dh", name = "f", descriptor = "Lclient!hda;")
    public static Component dragSource = null;

    @OriginalMember(owner = "client!dq", name = "k", descriptor = "I")
    public static int rectangleCount = 0;

    @OriginalMember(owner = "client!tia", name = "S", descriptor = "[Ljava/awt/Rectangle;")
    public static Rectangle[] rectangles = new Rectangle[100];

    @OriginalMember(owner = "client!dda", name = "o", descriptor = "Z")
    public static boolean testOpacity = false;

    @OriginalMember(owner = "client!dca", name = "v", descriptor = "I")
    public static int dragOffsetY;

    @OriginalMember(owner = "client!nq", name = "e", descriptor = "[Lclient!hda;")
    public static Component[] dragChildren;

    @OriginalMember(owner = "client!gl", name = "c", descriptor = "I")
    public static int dragOffsetX;

    @OriginalMember(owner = "client!ffa", name = "b", descriptor = "Z")
    public static boolean dragging = false;

    @OriginalMember(owner = "client!kq", name = "k", descriptor = "Z")
    public static boolean aBoolean428 = false;

    @OriginalMember(owner = "client!lt", name = "D", descriptor = "I")
    public static int dragStartX = 0;

    @OriginalMember(owner = "client!en", name = "m", descriptor = "I")
    public static int dragStartY = 0;

    @OriginalMember(owner = "client!ld", name = "e", descriptor = "Lclient!hda;")
    public static Component dragLayer = null;

    @OriginalMember(owner = "client!fe", name = "e", descriptor = "I")
    public static int dragParentY = -1;

    @OriginalMember(owner = "client!dr", name = "b", descriptor = "I")
    public static int dragParentX = -1;

    @OriginalMember(owner = "client!re", name = "n", descriptor = "Lclient!av;")
    public static IterableHashTable subInterfaces = new IterableHashTable(8);

    @OriginalMember(owner = "client!aka", name = "l", descriptor = "I")
    public static int rectDebug = 0;

    @OriginalMember(owner = "client!mda", name = "B", descriptor = "Lclient!hda;")
    public static Component dialog = null;

    @OriginalMember(owner = "client!lr", name = "e", descriptor = "Z")
    public static boolean clipComponents = false;

    /**
     * RSPS: clip a model component's 3D render to the component's own box.
     *
     * Owner 2026-09-20, on the 78 Store preview panel: "the preview of the items is still not fitting inside the
     * panel". A model component is scaled by a fixed aspect ratio baked into the cache (width * 512 /
     * modelAspectRatioX), but item models are not a fixed size - a long weapon is drawn corner to corner across
     * its inventory box and is roughly half again as tall as a boot. Whatever single scale the interface picks,
     * some item overflows, and with no clip the overflow is drawn straight over the window frame and the rest of
     * the screen.
     *
     * The 3D clip already exists here; it was simply tied to {@link #clipComponents}, a debug toggle that defaults
     * to false, so nothing ever used it.
     *
     * It must NOT be unconditional. Owner 2026-09-21/22: every NPC's dialogue head was "small and incorrect not
     * fitting" (chatdialog.png: the Estate agent reduced to a moustache). Jagex's own chathead component (241-244
     * component 2, and the player head in 64-67) is a small box the head is meant to draw well past; clipping it
     * cut every portrait down to the square around the nose. Native 667 interfaces are therefore never clipped;
     * only the RSPS item previews in {@link #FITTED_PREVIEW_INTERFACES} are, and those are fitted to their box
     * first, so the clip is only a last safety net there.
     */
    public static boolean clipModelComponents = true;

    /**
     * Item preview panels whose model is fitted to the box per item instead of by one fixed scale (owner 2026-09-22,
     * "shopp ui.png": the Dark bow ran out of the store preview). The cache can hold only one scale, and item models
     * differ several times in size, so every fixed scale either overflowed long weapons or left small items tiny.
     * Interface 1151 is the 78 Store (StoreInterfaceImportTool.PREVIEW_MODEL); 1176 the Drop Viewer and 1182 the quest tab's
     * Drops & Collection Log panel (N108, 2026-10-24: every npc from a rat to the King Black Dragon in the same thumb / head box).
     */
    private static final int[] FITTED_PREVIEW_INTERFACES = { 1151, 1176, 1182 };

    /**
     * Share of the box the fitted model may use; the rest is margin. The fit uses the model's projected 3D bounding box, which is
     * larger than what is drawn (owner 2026-10-11, bank-size store: amulets came out tiny at 0.84).
     */
    private static final float FITTED_PREVIEW_FILL = 0.96F;

    /** Nearest depth a fitted model's bounding box may have: clear of the hardware renderers' near plane (GlToolkit 50) (N26). */
    private static final int FITTED_PREVIEW_MIN_DEPTH = 64;

    /** Pixels kept free above and below a fitted model (N26). */
    private static final int FITTED_PREVIEW_MARGIN_Y = 4;

    /**
     * N26: the 78 Store's model box (StoreInterfaceImportTool PREVIEW_Y / PREVIEW_HEIGHT: y 107, 78 high) sits 3 px under the top of
     * its stage and 7 px above its bottom (StoreArtTool SHOW_Y 104, SHOW_HEIGHT 88): 2 px lower centres the model on the stage.
     */
    private static final int FITTED_PREVIEW_SHIFT_Y = 2;

    private static final int[] fitCorner = new int[3];

    /**
     * The fit per preview component, computed once per shown item / npc: {key, scale, centreX - offsetX, centreY - offsetY,
     * width, height}. Owner 2026-10-10: pets are previewed as their npc in its idle animation, whose bounding box changes every
     * frame - fitting every frame made the model pump in and out, so the first frame's fit is kept while the same model shows.
     */
    private static final java.util.HashMap<Integer, int[]> fitCache = new java.util.HashMap<>();

    /**
     * A preview component that shows an item, an npc model (Component.OBJ_TYPE_NPCMODEL, set by the store's clientscript) or a plain
     * model (OBJ_TYPE_MODEL: the Drop Viewer's Deadman's Chest, N277).
     */
    private static boolean hasFittedModel(Component child) {
        return child.invObject != -1 || child.objType == Component.OBJ_TYPE_NPCMODEL || child.objType == Component.OBJ_TYPE_MODEL;
    }

    /** The Drop Viewer (1176) and the quest tab's Drops panel (1182): models in their standing pose, not moving (N283 / N319). */
    private static final int[] STILL_PREVIEW_INTERFACES = { 1176, 1182 };

    private static boolean isStillPreview(Component child) {
        int interfaceId = child.slot >>> 16;
        for (int id : STILL_PREVIEW_INTERFACES) {
            if (id == interfaceId) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFittedPreview(Component child) {
        int interfaceId = child.slot >>> 16; // slot = (interface << 16) | component (InterfaceList); id is unrelated
        for (int id : FITTED_PREVIEW_INTERFACES) {
            if (id == interfaceId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Projects the eight corners of the model's bounding box through the model's own transform, then sets the
     * projection scale and centre so that box fills {@link #FITTED_PREVIEW_FILL} of the component, centred. Uses one
     * scale for both axes, so the item is never stretched.
     */
    private static void fitModelToBox(Component child, Model model, int offsetX, int offsetY) {
        int key = child.invObject != -1 ? child.invObject : (child.objType == Component.OBJ_TYPE_MODEL ? 0x20000000 : 0x40000000) | child.obj;
        int[] cached = fitCache.get(child.slot);
        if (cached != null && cached[0] == key && cached[4] == child.width && cached[5] == child.height) {
            if (cached[6] != 0) {
                Static59.aMatrix_5.translate(0, 0, cached[6]);
            }
            Toolkit.active.DA(offsetX + cached[2], offsetY + cached[3], cached[1], cached[1]);
            return;
        }
        int[] xs = { model.V(), model.RA() };
        int[] ys = { model.fa(), model.EA() };
        int[] zs = { model.HA(), model.G() };
        // N26: the hardware renderers clip everything nearer than their near plane (GlToolkit 50). A small zoom2d (Ankou gloves:
        // 112) put the near end of the model inside it, which cut it flat. Push the model back along the view axis first; the
        // fit below scales it up again, so its size in the box stays the same.
        int minDepth = Integer.MAX_VALUE;
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    Static59.aMatrix_5.project(x, y, z, fitCorner);
                    minDepth = Math.min(minDepth, fitCorner[2]);
                }
            }
        }
        int push = minDepth < FITTED_PREVIEW_MIN_DEPTH ? FITTED_PREVIEW_MIN_DEPTH - minDepth : 0;
        if (push != 0) {
            Static59.aMatrix_5.translate(0, 0, push);
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean exact = false;
        if (model instanceof JavaModel && ((JavaModel) model).vertexCount > 0) {
            // N319 (owner 2026-10-24: the Drop Viewer's npc pictures "te klein en passen niet allemaal"): the corners of the model's
            // bounding box, turned to the 3/4 view, spread far wider and taller than the model itself (a Barrows brother or Bork
            // came out a few pixels high). The software renderer's model has its vertices: fit what is really drawn.
            JavaModel m = (JavaModel) model;
            for (int v = 0; v < m.vertexCount; v++) {
                Static59.aMatrix_5.project(m.vertexX[v], m.vertexY[v], m.vertexZ[v], fitCorner);
                if (fitCorner[2] <= 16) {
                    continue;
                }
                float sx = (float) fitCorner[0] / fitCorner[2];
                float sy = (float) fitCorner[1] / fitCorner[2];
                minX = Math.min(minX, sx);
                maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy);
                maxY = Math.max(maxY, sy);
                exact = true;
            }
        }
        if (!exact) {
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        Static59.aMatrix_5.project(x, y, z, fitCorner);
                        if (fitCorner[2] <= 16) {
                            return; // a corner at or behind the eye: keep the cache scale rather than divide by ~0
                        }
                        float sx = (float) fitCorner[0] / fitCorner[2];
                        float sy = (float) fitCorner[1] / fitCorner[2];
                        minX = Math.min(minX, sx);
                        maxX = Math.max(maxX, sx);
                        minY = Math.min(minY, sy);
                        maxY = Math.max(maxY, sy);
                    }
                }
            }
        }
        float spanX = maxX - minX;
        float spanY = maxY - minY;
        if (spanX <= 0.0001F && spanY <= 0.0001F) {
            return;
        }
        float scaleX = spanX <= 0.0001F ? Float.MAX_VALUE : child.width * FITTED_PREVIEW_FILL / spanX;
        // N26: vertically a fixed margin, not 4% of the 78 px box - a model whose bounding box is as tall as what it draws (the long
        // cuffs of the Ankou gloves, Elder chaos top) otherwise touched the box's top edge, which looks cut off under the panel frame.
        // N108: a small box (the Drop Viewer's 20 px npc thumbs) keeps a margin in proportion; 4 px each side left 7 of 15 px.
        // N319: an exact (vertex) fit in a small box keeps 1 px; the bounding-box fit keeps its proportional margin.
        int marginY = exact && child.height < 40 ? 1 : Math.min(FITTED_PREVIEW_MARGIN_Y, child.height / 10);
        float scaleY = spanY <= 0.0001F ? Float.MAX_VALUE : Math.min(child.height * FITTED_PREVIEW_FILL, child.height - 2 * marginY) / spanY;
        int scale = (int) Math.min(scaleX, scaleY);
        if (scale <= 0) {
            return;
        }
        int centreX = offsetX + child.width / 2 - (int) ((minX + maxX) / 2.0F * scale);
        int centreY = offsetY + child.height / 2 + FITTED_PREVIEW_SHIFT_Y - (int) ((minY + maxY) / 2.0F * scale);
        fitCache.put(child.slot, new int[] { key, scale, centreX - offsetX, centreY - offsetY, child.width, child.height, push });
        if (MEASURE_HEADS) {
            System.out.println("[78] fit key=" + key + " bounds x=" + model.V() + ".." + model.RA() + " y=" + model.fa() + ".." + model.EA() + " z=" + model.HA() + ".." + model.G()
                + " proj x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY + " box=" + child.width + "x" + child.height + " scale=" + scale
                + " centre=" + (centreX - offsetX) + "," + (centreY - offsetY) + " xan=" + child.xan2d + " yan=" + child.yan2d + " zan=" + child.zan2d + " zoom=" + child.zoom2d
                + " xof=" + child.xof2d + " yof=" + child.yof2d + " minDepth=" + minDepth + " push=" + push);
        }
        Toolkit.active.DA(centreX, centreY, scale, scale);
    }

    /**
     * Owner A4 (foto\dy.png, Smol Heredit): the npc chat heads (241-244:2) and the Follower Details head (662:1) must fit
     * their frame. The size is the server's choice - 2009scape DialogueInterpreter sends IF_SETANGLE (zoom) with every npc
     * dialogue; ours sends a measured zoom per pet (server NpcChatheadZoom, data\cfg\pets\pet_chathead_zoom.tsv). The client
     * draws exactly what it is sent and only MEASURES here, so the data can be checked and regenerated: per npc head drawn
     * on those components it keeps the largest projected bounding box over all animation frames (pixels from the head's
     * anchor) and prints it with the parent's clip box whenever it grows:
     * "[78] npc head N on IF:C zoom Z ext up/down/left/right room up/down/left/right".
     */
    private static final int[][] NPC_HEAD_COMPONENTS = { { 241, 2 }, { 242, 2 }, { 243, 2 }, { 244, 2 }, { 662, 1 } };
    private static final java.util.HashMap<Long, int[]> headExtents = new java.util.HashMap<>();
    /** Only the team's test client (test hook) or -Drsps.measureHeads=true measures - never a player's client log. */
    private static final boolean MEASURE_HEADS = System.getProperty("rsps.testHook") != null || Boolean.getBoolean("rsps.measureHeads");

    private static void measureNpcHead(Component child, Model model, int anchorX, int anchorY, int scaleX, int scaleY, int clipX1, int clipY1, int clipX2, int clipY2) {
        boolean listed = false;
        for (int[] c : NPC_HEAD_COMPONENTS) {
            if (c[0] == child.slot >>> 16 && c[1] == (child.slot & 0xFFFF)) {
                listed = true;
            }
        }
        if (!listed || !MEASURE_HEADS) {
            return;
        }
        int[] xs = { model.V(), model.RA() };
        int[] ys = { model.fa(), model.EA() };
        int[] zs = { model.HA(), model.G() };
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    Static59.aMatrix_5.project(x, y, z, fitCorner);
                    if (fitCorner[2] <= 16) {
                        return;
                    }
                    float sx = (float) fitCorner[0] / fitCorner[2];
                    float sy = (float) fitCorner[1] / fitCorner[2];
                    minX = Math.min(minX, sx);
                    maxX = Math.max(maxX, sx);
                    minY = Math.min(minY, sy);
                    maxY = Math.max(maxY, sy);
                }
            }
        }
        int up = Math.round(-minY * scaleY);
        int down = Math.round(maxY * scaleY);
        int left = Math.round(-minX * scaleX);
        int right = Math.round(maxX * scaleX);
        long key = ((long) child.slot << 32) | ((long) (child.obj & 0xFFFF) << 16) | (child.zoom2d & 0xFFFF);
        int[] seen = headExtents.get(key);
        if (seen != null && up <= seen[0] && down <= seen[1] && left <= seen[2] && right <= seen[3]) {
            return;
        }
        seen = seen == null ? new int[] { up, down, left, right }
            : new int[] { Math.max(up, seen[0]), Math.max(down, seen[1]), Math.max(left, seen[2]), Math.max(right, seen[3]) };
        headExtents.put(key, seen);
        System.out.println("[78] npc head " + child.obj + " on " + (child.slot >>> 16) + ":" + (child.slot & 0xFFFF) + " zoom " + child.zoom2d
            + " ext " + seen[0] + "/" + seen[1] + "/" + seen[2] + "/" + seen[3]
            + " room " + (anchorY - clipY1) + "/" + (clipY2 - anchorY) + "/" + (anchorX - clipX1) + "/" + (clipX2 - anchorX));
    }

    @OriginalMember(owner = "client!ls", name = "j", descriptor = "I")
    public static volatile int topLevelInterface = -1;

    @OriginalMember(owner = "client!ep", name = "b", descriptor = "Z")
    public static boolean resizableScreen = true;

    @OriginalMember(owner = "client!un", name = "t", descriptor = "I")
    public static int dragLastX = -1;

    @OriginalMember(owner = "client!dka", name = "b", descriptor = "I")
    public static int dragLastY = -1;

    @OriginalMember(owner = "client!fl", name = "a", descriptor = "I")
    public static int dragTicks;

    @OriginalMember(owner = "client!et", name = "c", descriptor = "Z")
    public static boolean targetMode = false;

    @OriginalMember(owner = "client!eq", name = "a", descriptor = "Ljava/lang/String;")
    public static String targetVerb = null;

    @OriginalMember(owner = "client!ea", name = "d", descriptor = "Ljava/lang/String;")
    public static String targetedVerb = null;

    @OriginalMember(owner = "client!kg", name = "M", descriptor = "Lclient!hda;")
    public static Component dragTarget = null;

    @OriginalMember(owner = "client!tf", name = "b", descriptor = "Lclient!hda;")
    public static Component scene = null;

    /** Per-interface-logic-frame mouse-wheel routing state used by world-view zoom. */
    public static boolean mouseWheelOverScene = false;

    public static boolean mouseWheelClaimedByInterface = false;

    public static void beginMouseWheelFrame() {
        RspsMinimapZoom.hovered = false;
        mouseWheelOverScene = false;
        mouseWheelClaimedByInterface = false;
    }

    public static boolean hasModalSubInterface() {
        HashTableIterator iterator = new HashTableIterator(subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.type == SubInterfaceType.MODAL) {
                return true;
            }
        }
        return false;
    }

    /** A MODAL sub-interface that blocks world zoom; the table key is the parent component hash, so its interface is key >>> 16. */
    public static boolean hasSceneModalSubInterface() {
        HashTableIterator iterator = new HashTableIterator(subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (CameraZoomController.blocksWorldZoom(sub.type == SubInterfaceType.MODAL, (int) (sub.key >>> 16))) {
                return true;
            }
        }
        return false;
    }

    @OriginalMember(owner = "client!wn", name = "c", descriptor = "I")
    public static int targetMask;

    @OriginalMember(owner = "client!tf", name = "c", descriptor = "I")
    public static int targetParam;

    @OriginalMember(owner = "client!oda", name = "u", descriptor = "I")
    public static int targetSlot;

    @OriginalMember(owner = "client!faa", name = "i", descriptor = "I")
    public static int targetInvObj = -1;

    @OriginalMember(owner = "client!ci", name = "h", descriptor = "I")
    public static int targetComponent = -1;

    @OriginalMember(owner = "client!lla", name = "f", descriptor = "I")
    public static int targetEnterCursor;

    @OriginalMember(owner = "client!nv", name = "o", descriptor = "I")
    public static int targetEndCursor = -1;

    @OriginalMember(owner = "client!oja", name = "c", descriptor = "Z")
    public static boolean lobbyOpened = false;

    @OriginalMember(owner = "client!sga", name = "g", descriptor = "Z")
    public static boolean loginOpened = false;

    static {
        for (@Pc(87) int i = 0; i < 100; i++) {
            rectangles[i] = new Rectangle();
        }
    }

    @OriginalMember(owner = "client!o", name = "a", descriptor = "(Lclient!sb;Lclient!sb;Lclient!sb;BLclient!sb;)V")
    public static void init(@OriginalArg(0) js5 interfacesJs5, @OriginalArg(1) js5 fontMetricsJs5, @OriginalArg(2) js5 spritesJs5, @OriginalArg(4) js5 modelsJs5) {
        Component.spritesJs5 = spritesJs5;
        Component.interfacesJs5 = interfacesJs5;
        Component.modelsJs5 = modelsJs5;
        InterfaceList.interfaces = new Component[Component.interfacesJs5.groupSize()][];
        InterfaceList.loaded = new boolean[Component.interfacesJs5.groupSize()];
    }

    @OriginalMember(owner = "client!fj", name = "a", descriptor = "(ILclient!hda;)V")
    public static void redraw(@OriginalArg(1) Component component) {
        if (component.lastUpdate == lastDrawCycle) {
            dirtyRectangles[component.rectangle] = true;
        }
    }

    @OriginalMember(owner = "client!oq", name = "b", descriptor = "(I)V")
    public static void redrawAll() {
        for (@Pc(1) int i = 0; i < 100; i++) {
            dirtyRectangles[i] = true;
        }
    }

    @OriginalMember(owner = "client!sh", name = "a", descriptor = "(IIBII)V")
    public static void redrawWithin(@OriginalArg(1) int x, @OriginalArg(4) int y, @OriginalArg(0) int width, @OriginalArg(3) int height) {
        for (@Pc(1) int i = 0; i < rectangleCount; i++) {
            @Pc(6) Rectangle rectangle = rectangles[i];

            if (x < rectangle.x + rectangle.width && x + width > rectangle.x && y < rectangle.height + rectangle.y && height + y > rectangle.y) {
                dirtyRectangles[i] = true;
            }
        }

        OrthoMode.method8927(x, x + width, y, y + height);
    }

    @OriginalMember(owner = "client!cea", name = "a", descriptor = "(II[Lclient!hda;IIIIZIII)V")
    public static void draw(@OriginalArg(2) Component[] children, @OriginalArg(0) int parent, @OriginalArg(1) int screenX, @OriginalArg(10) int screenY, @OriginalArg(3) int x1, @OriginalArg(4) int y1, @OriginalArg(9) int x2, @OriginalArg(5) int y2, @OriginalArg(6) int boundRectangle, @OriginalArg(7) boolean aspectRatio) {
        Toolkit.active.KA(x1, y1, x2, y2);

        for (@Pc(13) int i = 0; i < children.length; i++) {
            @Pc(19) Component child = children[i];
            if ((child == null) || ((parent != child.layer) && ((parent != ROOT) || (dragSource != child)))) {
                continue;
            }

            @Pc(49) int boundsLeft = child.x + screenX;
            @Pc(54) int boundsBottom = child.y + screenY;
            @Pc(61) int boundsRight = boundsLeft + child.width + 1;
            @Pc(69) int boundsTop = boundsBottom + child.height + 1;

            @Pc(74) int rectangle;
            if (boundRectangle == -1) {
                rectangles[rectangleCount].setBounds(child.x + screenX, screenY + child.y, child.width, child.height);
                rectangle = rectangleCount++;
            } else {
                rectangle = boundRectangle;
            }

            child.rectangle = rectangle;
            child.lastUpdate = TimeUtils.clock;

            if (isHidden(child)) {
                continue;
            }
            RspsXpDrops.drawn(child); // N207: is the 2011 XP total box on?

            // Cinematic mode (RspsCinematic, the intro's guard scene): the game frame is not drawn.
            if (RspsCinematic.hide(child)) {
                continue;
            }

            if (child.clientcode != 0) {
                drawSpinningPlayer(child);
            }

            @Pc(125) int offsetX = screenX + child.x;
            @Pc(130) int offsetY = screenY + child.y;
            // N176 (owner 2026-10-22): the Quest Helper panel (1166:1, top left of the game view) moves with Alt + drag and
            // goes back with Alt + right-click like every RuneLite overlay; the spot is kept inside the view it opens in.
            if (child.slot == QUEST_HELPER_PANEL) {
                int[] at = RspsOverlayLayout.place(QUEST_HELPER_LAYOUT, offsetX, offsetY, child.width, child.height, x1, y1, x2 - x1, y2 - y1);
                offsetX = at[0];
                offsetY = at[1];
                if (boundRectangle == -1) {
                    rectangles[rectangle].setBounds(offsetX, offsetY, child.width, child.height);
                }
            }
            RspsSpellbookEdit.drawn(child, offsetX, offsetY); // N189: where each spell is, for dragging
            if (child.slot == RspsPkHotkeys.PRAYER_HASH) RspsPkHud.prayerCell(child.id, offsetX, offsetY, child.width, child.height); // 78Legacy v5 PK HUD

            // RSPS Deadman: both layers of the PvP overlay (381:0 zone state, 381:3 skull timer) are
            // drawn as custom panels (guarded / dangerous icon + text; the player's own head-icon
            // skull + remaining time) instead of the cache's layouts - see DeadmanSkullHud. Their
            // cache children are never drawn. Redrawn every frame, in every display and graphics mode.
            if ((child.slot >>> 16) == DeadmanSkullHud.INTERFACE_ID) {
                // The cache's own skull/text layout of 381 is never drawn (owner: "remove the old hud completely"). The HUD itself
                // is no interface tenant any more (E16): DeadmanSkullHud.drawOverlay draws it after the whole interface tree.
                continue;
            }

            @Pc(132) int local132 = 0;
            @Pc(134) int local134 = 0;
            if (OrthoMode.toolkitActive) {
                local132 = OrthoMode.method2283();
                local134 = Static422.method5771();
            }

            @Pc(145) int transparency = child.transparency;
            if (testOpacity && (serverActiveProperties(child).events != 0 || child.type == Component.TYPE_LAYER) && transparency > 127) {
                transparency = 127;
            }

            if (dragSource == child) {
                if (parent != ROOT && (child.dragRenderBehaviour == DragRenderBehaviour.OFFSET || child.dragRenderBehaviour == DragRenderBehaviour.OFFSET_TRANSPARENT)) {
                    dragOffsetY = screenY;
                    dragChildren = children;
                    dragOffsetX = screenX;
                    continue;
                }

                if (dragging && aBoolean428) {
                    @Pc(216) int mouseX;
                    @Pc(222) int mouseY;
                    mouseX = local132 + MouseMonitor.instance.getRecordedX();
                    mouseY = local134 + MouseMonitor.instance.getRecordedY();
                    mouseY -= dragStartY;
                    mouseX -= dragStartX;

                    if (mouseX < dragParentX) {
                        mouseX = dragParentX;
                    }
                    if (mouseY < dragParentY) {
                        mouseY = dragParentY;
                    }

                    if (dragParentX + dragLayer.width < mouseX - -child.width) {
                        mouseX = dragParentX + dragLayer.width - child.width;
                    }
                    offsetX = mouseX;

                    if (dragParentY + dragLayer.height < mouseY + child.height) {
                        mouseY = dragParentY + dragLayer.height - child.height;
                    }
                    offsetY = mouseY;
                }

                if (child.dragRenderBehaviour == DragRenderBehaviour.OFFSET_TRANSPARENT) {
                    transparency = 128;
                }
            }

            @Pc(216) int childX1;
            @Pc(222) int childY1;
            @Pc(359) int childX2;
            @Pc(371) int childY2;
            if (child.type == Component.TYPE_INVENTORY) {
                childX1 = x1;
                childY2 = y2;
                childY1 = y1;
                childX2 = x2;
            } else {
                @Pc(317) int local317 = offsetX + child.width;
                @Pc(323) int local323 = offsetY + child.height;

                if (child.type == Component.TYPE_LINE) {
                    local323++;
                    local317++;
                }

                childY1 = y1 >= offsetY ? y1 : offsetY;
                childX1 = offsetX > x1 ? offsetX : x1;
                childX2 = x2 <= local317 ? x2 : local317;
                childY2 = local323 < y2 ? local323 : y2;
            }

            if (childX2 > childX1 && childY2 > childY1) {
                if (child.clientcode != 0) {
                    if (child.clientcode == ComponentClientCode.SCENE || child.clientcode == ComponentClientCode.LOGIN_SCENE) {
                        setOptions(offsetY, offsetX, child);

                        if (!OrthoMode.toolkitActive) {
                            Static294.method4339(offsetY, child.clientcode == ComponentClientCode.LOGIN_SCENE, child.width, child.height, offsetX);
                            Toolkit.active.KA(x1, y1, x2, y2);
                        }

                        dirtyRectangles[rectangle] = true;
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.MINIMAP && CutsceneManager.state == 0) {
                        if (child.graphic(Toolkit.active) != null) {
                            Static557.method7331();
                            Minimap.draw(child, Toolkit.active, offsetX, offsetY);
                            flipDirtyRect[rectangle] = true;
                            Toolkit.active.KA(x1, y1, x2, y2);

                            if (OrthoMode.toolkitActive) {
                                if (aspectRatio) {
                                    OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                                } else {
                                    Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                                }
                            }
                        }
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.COLOUR_CHOOSER_HUE) {
                        ColourChooser.drawHue(child, offsetX, offsetY, Toolkit.active);
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.COLOUR_CHOOSER_SATURATION_VALUE) {
                        ColourChooser.drawSaturationValue(Toolkit.active, child.colour % 64, child, offsetX, offsetY);
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.COMPASS) {
                        if (child.graphic(Toolkit.active) != null) {
                            Minimap.drawCompass(child, offsetX, offsetY);
                            flipDirtyRect[rectangle] = true;

                            Toolkit.active.KA(x1, y1, x2, y2);

                            if (OrthoMode.toolkitActive) {
                                if (aspectRatio) {
                                    OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                                } else {
                                    Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                                }
                            }
                        }
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.WORLD_MAP) {
                        WorldMap.draw(child.height, offsetX, offsetY, Js5TextureSource.instance, Toolkit.active, child.width);
                        dirtyRectangles[rectangle] = true;
                        Toolkit.active.KA(x1, y1, x2, y2);
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.WORLD_MAP_OVERVIEW) {
                        WorldMap.drawOverview(child.width, Toolkit.active, child.height, offsetX, offsetY);
                        dirtyRectangles[rectangle] = true;
                        Toolkit.active.KA(x1, y1, x2, y2);
                        continue;
                    }

                    if (child.clientcode == ComponentClientCode.DEBUG_OVERLAY) {
                        if (!Client.displayFps && !Static354.showProfiling) {
                            continue;
                        }

                        @Pc(317) int drawX = offsetX + child.width;
                        @Pc(323) int drawY = offsetY + 15;

                        if (OrthoMode.toolkitActive) {
                            if (aspectRatio) {
                                OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                            } else {
                                Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                            }
                        }

                        if (Client.displayFps) {
                            @Pc(744) int fpsColour = 0xFFFFFF00;
                            if (GameShell.currentFps < 20) {
                                fpsColour = 0xFFFF0000;
                            }

                            Fonts.p12.renderRight("Fps:" + GameShell.currentFps, drawX, drawY, fpsColour, -1);
                            drawY += 15;

                            @Pc(768) Runtime runtime = Runtime.getRuntime();
                            @Pc(777) int memoryUsage = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L);
                            @Pc(779) int memoryColour = 0xFFFFFF00;
                            if (memoryUsage > 0x18000) {
                                if (Client.force64mb) {
                                    Static664.cacheRemoveSoftReferences();

                                    for (@Pc(792) int j = 0; j < 10; j++) {
                                        System.gc();
                                    }

                                    memoryUsage = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L);

                                    if (memoryUsage > 65536) {
                                        ChatHistory.addPrivateError("WARNING: Memory usage over 64MB! Please inform whoever is responsible for the content/area you are using/in.");
                                    }
                                }

                                memoryColour = 0xFFFF0000;
                            }

                            Fonts.p12.renderRight("Mem:" + memoryUsage + "k", drawX, drawY, memoryColour, 0xFFFFFFFF);
                            drawY += 15;

                            Fonts.p12.renderRight("Game: In:" + ServerConnection.GAME.readRate + "B/s Out:" + ServerConnection.GAME.writeRate + "B/s", drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                            drawY += 15;

                            Fonts.p12.renderRight("Lobby: In:" + ServerConnection.LOBBY.readRate + "B/s Out:" + ServerConnection.LOBBY.writeRate + "B/s", drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                            drawY += 15;

                            @Pc(792) int offheap = Toolkit.active.E() / 1024;
                            Fonts.p12.renderRight("Offheap:" + offheap + "k", drawX, drawY, offheap > 65536 ? 0xFFFF0000 : 0xFFFFFF00, -1);
                            drawY += 15;

                            @Pc(936) int entryTotal = 0;
                            @Pc(938) int loadedTotal = 0;
                            @Pc(940) int extrasTotal = 0;
                            for (@Pc(942) int archive = 0; archive < 37; archive++) {
                                if (Client.js5ResourceProviders[archive] != null) {
                                    entryTotal += Client.js5ResourceProviders[archive].entryCount();
                                    loadedTotal += Client.js5ResourceProviders[archive].loadedCount();
                                    extrasTotal += Client.js5ResourceProviders[archive].extrasCount();
                                }
                            }

                            @Pc(986) int extrasPercentage = (extrasTotal * 100) / entryTotal;
                            @Pc(992) int totalPercentage = (loadedTotal * 10000) / entryTotal;

                            @Pc(1018) String cache = "Cache:" + StringTools.formatNumber(0, true, totalPercentage, 2) + "% (" + extrasPercentage + "%)";
                            Fonts.p11.renderRight(cache, drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                            drawY += 12;
                        }

                        if (ParticleManager.particleCount > 0) {
                            Fonts.p11.renderRight("Particles: " + ParticleManager.runningParticleCount + " / " + ParticleManager.particleCount, drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                        }
                        drawY += 12;

                        if (Static354.showProfiling) {
                            Fonts.p11.renderRight("Polys: " + Toolkit.active.I() + " Models: " + Toolkit.active.M(), drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                            drawY += 12;

                            Fonts.p11.renderRight("Ls: " + Static606.anInt8954 + " La: " + Static577.anInt8587 + " NPC: " + Static480.npcCount + " Pl: " + Static179.playerCount, drawX, drawY, 0xFFFFFF00, 0xFFFFFFFF);
                            Static126.method2228();
                            drawY += 12;
                        }

                        dirtyRectangles[rectangle] = true;
                        continue;
                    }
                }

                if (child.type == Component.TYPE_LAYER) {
                    if (child.clientcode == ComponentClientCode.DEBUG_OVERLAY_LAYER && Toolkit.active.bloom()) {
                        Toolkit.active.method7959(offsetX, offsetY, child.width, child.height);
                    }

                    draw(children, child.slot, offsetX - child.scrollX, offsetY - child.scrollY, childX1, childY1, childX2, childY2, rectangle, aspectRatio);

                    if (child.dynamicComponents != null) {
                        draw(child.dynamicComponents, child.slot, offsetX - child.scrollX, offsetY - child.scrollY, childX1, childY1, childX2, childY2, rectangle, aspectRatio);
                    }

                    @Pc(1214) SubInterface sub = (SubInterface) subInterfaces.get(child.slot);
                    if (sub != null) {
                        draw(sub.id, childX1, childY1, childX2, childY2, offsetX, offsetY, rectangle);
                    }

                    if (child.slot == QUEST_HELPER_PANEL) {
                        // N176: the Alt outline, over the whole panel (its own clip would cut the 1 px border off).
                        Toolkit.active.KA(x1, y1, x2, y2);
                        RspsOverlayLayout.decorate(QUEST_HELPER_LAYOUT);
                    }

                    if (child.clientcode == ComponentClientCode.DEBUG_OVERLAY_LAYER) {
                        if (Toolkit.active.bloom()) {
                            Toolkit.active.method7974();
                        }

                        if (CutsceneManager.state == 3) {
                            @Pc(323) int alpha = CutsceneManager.cutsceneFadeAlpha;
                            @Pc(744) int blue = CutsceneManager.cutsceneFadeBlue;
                            @Pc(1255) int red = CutsceneManager.cutsceneFadeRed;
                            @Pc(777) int green = CutsceneManager.cutsceneFadeGreen;

                            if (TimeUtils.clock < CutsceneManager.cutsceneFadeEnd) {
                                @Pc(1276) float f = (float) (TimeUtils.clock - CutsceneManager.cutsceneFadeStart) / (float) (CutsceneManager.cutsceneFadeEnd - CutsceneManager.cutsceneFadeStart);
                                blue = (int) ((f * (float) CutsceneManager.cutsceneFadeBlue) + ((float) Static582.anInt8628 * (1.0F - f)));
                                red = (int) ((f * (float) CutsceneManager.cutsceneFadeRed) + ((float) Static493.anInt7370 * (1.0F - f)));
                                alpha = (int) ((f * (float) CutsceneManager.cutsceneFadeAlpha) + ((float) Static323.anInt5120 * (1.0F - f)));
                                green = (int) ((f * (float) CutsceneManager.cutsceneFadeGreen) + ((float) Static201.anInt8407 * (1.0F - f)));
                            }

                            if (alpha > 0) {
                                Toolkit.active.fillRect(childX1, childY1, childX2 - childX1, -childY1 + childY2, blue << 16 | alpha << 24 | red << 8 | green);
                            }
                        }
                    }

                    Toolkit.active.KA(x1, y1, x2, y2);
                }

                if (currentlyDirtyRect[rectangle] || rectDebug > 1) {
                    if (child.type == Component.TYPE_RECTANGLE) {
                        if (transparency == 0) {
                            if (child.filled) {
                                Toolkit.active.aa(offsetX, offsetY, child.width, child.height, child.colour, 0);
                            } else {
                                Toolkit.active.outlineRect(offsetX, offsetY, child.width, child.height, child.colour, 0);
                            }
                        } else {
                            if (child.filled) {
                                Toolkit.active.aa(offsetX, offsetY, child.width, child.height, 255 - (transparency & 0xFF) << 24 | child.colour & 0xFFFFFF, 1);
                            } else {
                                Toolkit.active.outlineRect(offsetX, offsetY, child.width, child.height, 255 - (transparency & 0xFF) << 24 | child.colour & 0xFFFFFF, 1);
                            }
                        }

                        if (OrthoMode.toolkitActive) {
                            if (aspectRatio) {
                                OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                            } else {
                                Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                            }
                        }
                    } else if (child.type == Component.TYPE_TEXT) {
                        @Pc(1514) Font font = child.font(Toolkit.active);

                        if (font != null) {
                            @Pc(323) int colour = child.colour;
                            @Pc(1533) String text = child.text;

                            if (child.invObject != -1) {
                                @Pc(1543) ObjType objType = ObjTypeList.instance.list(child.invObject);
                                text = objType.name;

                                if (text == null) {
                                    text = "null";
                                }

                                if ((objType.stackable == ObjStackability.ALWAYS || child.invCount != 1) && child.invCount != -1) {
                                    text = "<col=ff9040>" + text + "</col> x" + invText(child.invCount);
                                }
                            }

                            if (child.video != -1) {
                                text = VideoTypeList.subtitles(child.video);

                                if (text == null) {
                                    text = "";
                                }
                            }

                            if (child == dialog) {
                                text = LocalisedText.PLEASEWAIT.localise(Client.language);
                                colour = child.colour;
                            }

                            if (clipComponents) {
                                Toolkit.active.T(offsetX, offsetY, offsetX + child.width, child.height + offsetY);
                            }

                            // Quest Helper: the right dialogue option, numbered and highlighted (RspsQuestHelperHints).
                            colour = RspsQuestHelperHints.dialogueColour(child, text, colour);
                            text = RspsQuestHelperHints.dialogueText(child, text);

                            // Combat tab weapon name stays on one line (CombatTabNameFit, owner 2026-10-03).
                            font = CombatTabNameFit.fit(child, font, text, Toolkit.active, Sprites.nameIcons);

                            font.renderLines(text, offsetX, offsetY, 0, 0, child.width, child.height, child.textAlignX, child.textAlignY, child.maxLines, child.textHeight, colour | ((255 - (transparency & 0xFF)) << 24), child.textShadow ? 255 - (transparency & 0xFF) << 24 : -1, null, Sprites.nameIcons, null);

                            if (clipComponents) {
                                Toolkit.active.KA(x1, y1, x2, y2);
                            }

                            if (text.trim().length() > 0) {
                                if (!clipComponents) {
                                    @Pc(1730) FontMetrics metrics = Fonts.metrics(child.fontGraphic, Toolkit.active);
                                    @Pc(777) int textWidth = metrics.paraWidth(Sprites.nameIcons, text, child.width);
                                    @Pc(779) int textHeight = metrics.stringHeight(child.width, child.textHeight, text, Sprites.nameIcons);

                                    if (OrthoMode.toolkitActive) {
                                        if (aspectRatio) {
                                            OrthoMode.method8927(offsetX, offsetX + textWidth, offsetY, offsetY + textHeight);
                                        } else {
                                            Static595.method7810(offsetY, offsetX + textWidth, textHeight + offsetY, offsetX);
                                        }
                                    }
                                } else if (OrthoMode.toolkitActive) {
                                    if (aspectRatio) {
                                        OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                                    } else {
                                        Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                                    }
                                }
                            }
                        } else if (Component.redrawAll) {
                            redraw(child);
                        }
                    } else if (child.type == Component.TYPE_GRAPHIC) {
                        if (child.skyBox >= 0) {
                            child.skyBox(SkyBoxSphereTypeList.instance, SkyBoxTypeList.instance).method3162(Toolkit.active, offsetY, offsetX, child.width, child.skyboxRenderPitch << 3, child.skyboxRenderYaw << 3, child.height);
                        } else {
                            @Pc(1816) Sprite sprite;
                            if (child.invObject != -1) {
                                @Pc(1836) PlayerModel model = child.objWearCol ? PlayerEntity.self.playerModel : null;
                                sprite = ObjTypeList.instance.getCachedSprite(model, Toolkit.active, RspsUi.objNumMode(child), child.invObject, child.outline, child.invCount, child.graphicShadow | 0xFF000000);
                                if (sprite != null) {
                                    // N319: smaller than the item sprite - its own pixels shrunk by averaging, drawn at their size.
                                    @Pc(1837) Sprite smooth = com.jagex.game.runetek6.config.objtype.RspsObjSmooth.get(Toolkit.active, child.invObject, child.outline, child.invCount, child.graphicShadow | 0xFF000000, RspsUi.objNumMode(child), model != null, child.width, child.height);
                                    if (smooth != null) {
                                        sprite = smooth;
                                    }
                                }
                            } else if (child.video != -1) {
                                sprite = VideoTypeList.frame(child.video, Toolkit.active);
                            } else {
                                sprite = child.sprite(Toolkit.active);
                            }

                            if (sprite != null) {
                                @Pc(323) int scaleWidth = sprite.scaleWidth();
                                @Pc(744) int scaleHeight = sprite.scaleHeight();
                                @Pc(1255) int backgroundColour = ((255 - (transparency & 0xFF)) << 24) | ((child.colour != 0) ? (child.colour & 0xFFFFFF) : 0xFFFFFF);

                                if (child.tiling) {
                                    Toolkit.active.T(offsetX, offsetY, offsetX + child.width, offsetY - -child.height);

                                    if (child.angle2d != 0) {
                                        @Pc(777) int newWidth = (child.width + scaleWidth - 1) / scaleWidth;
                                        @Pc(779) int newHeight = (child.height + scaleHeight - 1) / scaleHeight;

                                        for (@Pc(792) int x = 0; x < newWidth; x++) {
                                            for (@Pc(936) int y = 0; y < newHeight; y++) {
                                                if (child.colour != 0) {
                                                    sprite.renderRotated((float) (x * scaleWidth + offsetX) + ((float) scaleWidth / 2.0F), (float) scaleHeight / 2.0F + (float) (y * scaleHeight + offsetY), 4096, child.angle2d, backgroundColour);
                                                } else {
                                                    sprite.renderRotated((float) (x * scaleWidth + offsetX) + ((float) scaleWidth / 2.0F), (float) (offsetY + scaleHeight * y) + (float) scaleHeight / 2.0F, 4096, child.angle2d);
                                                }
                                            }
                                        }
                                    } else {
                                        if (child.colour != 0 || transparency != 0) {
                                            sprite.renderTiled(offsetX, offsetY, child.width, child.height, 0, backgroundColour, 1);
                                        } else {
                                            sprite.renderTiled(offsetX, offsetY, child.width, child.height);
                                        }
                                    }

                                    Toolkit.active.KA(x1, y1, x2, y2);
                                } else {
                                    if (child.colour != 0 || transparency != 0) {
                                        if (child.angle2d != 0) {
                                            sprite.renderRotated(((float) child.width / 2.0F) + (float) offsetX, (float) child.height / 2.0F + (float) offsetY, child.width * 4096 / scaleWidth, child.angle2d, backgroundColour);
                                        } else if (scaleWidth == child.width && scaleHeight == child.height) {
                                            sprite.render(offsetX, offsetY, 0, backgroundColour, 1);
                                        } else {
                                            sprite.render(offsetX, offsetY, child.width, child.height, 0, backgroundColour, 1);
                                        }
                                    } else {
                                        if (child.angle2d != 0) {
                                            sprite.renderRotated(((float) child.width / 2.0F) + (float) offsetX, (float) offsetY + (float) child.height / 2.0F, child.width * 4096 / scaleWidth, child.angle2d);
                                        } else if (scaleWidth == child.width && scaleHeight == child.height) {
                                            sprite.render(offsetX, offsetY);
                                        } else {
                                            sprite.render(offsetX, offsetY, child.width, child.height);
                                        }
                                    }
                                }
                            } else if (Component.redrawAll) {
                                redraw(child);
                            }
                        }

                        if (OrthoMode.toolkitActive) {
                            if (aspectRatio) {
                                OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                            } else {
                                Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                            }
                        }
                    } else if (child.type == Component.TYPE_MODEL) {
                        Static104.method2033();
                        NPCTypeCustomisation customisation = null;
                        @Pc(2313) Model model = null;
                        @Pc(744) int minY = 0;

                        if (child.invObject != -1) {
                            @Pc(1543) ObjType objType = ObjTypeList.instance.list(child.invObject);
                            if (objType != null) {
                                objType = objType.getStacked(child.invCount);
                                model = objType.model(child.animator, 2048, child.objWearCol ? PlayerEntity.self.playerModel : null, 1, Toolkit.active);

                                if (model != null) {
                                    minY = -model.fa() >> 1;
                                } else {
                                    redraw(child);
                                }
                            }
                        } else if (child.objType == Component.OBJ_TYPE_PLAYERMODEL) {
                            @Pc(1255) int slot = child.obj;

                            if (slot >= 0 && slot < 2048) {
                                @Pc(2341) PlayerEntity player = PlayerList.highResolutionPlayers[slot];

                                if (player != null && (slot == PlayerList.activePlayerSlot || StringTools.intHash(player.nameUnfiltered) == child.objData)) {
                                    model = player.playerModel.bodyModel(ObjTypeList.instance, child.animator, BASTypeList.instance, SeqTypeList.instance, 2048, null, WearposDefaults.instance, IDKTypeList.instance, Toolkit.active, NPCTypeList.instance, null, 0, null, TimedVarDomain.instance);
                                }
                            }
                        } else if (child.objType == Component.OBJ_TYPE_INVENTORY_MALE || child.objType == Component.OBJ_TYPE_INVENTORY_FEMALE) {
                            @Pc(2468) ClientInventory inventory = ClientInventory.get(child.obj, false);

                            if (inventory != null) {
                                model = inventory.method3078(child.objData, Toolkit.active, child.objType == Component.OBJ_TYPE_INVENTORY_FEMALE, child.animator, child.objWearCol ? PlayerEntity.self.playerModel : null);
                            }
                        } else if (child.animator != null && child.animator.isAnimating()) {
                            model = child.model(Toolkit.active, child.animator, BASTypeList.instance, IDKTypeList.instance, SeqTypeList.instance, PlayerEntity.self.playerModel, TimedVarDomain.instance, NPCTypeList.instance, ObjTypeList.instance, 2048, customisation);

                            if (model == null && Component.redrawAll) {
                                redraw(child);
                            }
                        } else {
                            model = child.model(Toolkit.active, null, BASTypeList.instance, IDKTypeList.instance, SeqTypeList.instance, PlayerEntity.self.playerModel, TimedVarDomain.instance, NPCTypeList.instance, ObjTypeList.instance, 2048, customisation);

                            if (model == null && Component.redrawAll) {
                                redraw(child);
                            }
                        }

                        if (model != null) {
                            @Pc(1255) int local1255;
                            if (child.modelAspectRatioX <= 0) {
                                local1255 = 512;
                            } else {
                                local1255 = (child.width << 9) / child.modelAspectRatioX;
                            }

                            @Pc(777) int local777;
                            if (child.modelAspectRatioY <= 0) {
                                local777 = 512;
                            } else {
                                local777 = (child.height << 9) / child.modelAspectRatioY;
                            }

                            @Pc(779) int local779 = child.width / 2 + offsetX;
                            @Pc(792) int local792 = child.height / 2 + offsetY;
                            if (!child.orthoView) {
                                local779 += child.modelOriginX * local1255 >> 9;
                                local792 += child.modelOriginY * local777 >> 9;
                            }

                            Static460.aMatrix_10.makeIdentity();
                            Toolkit.active.setCamera(Static460.aMatrix_10);
                            Toolkit.active.DA(local779, local792, local1255, local777);
                            Toolkit.active.ya();

                            if (child.disableZBuffer) {
                                Toolkit.active.C(false);
                            }

                            if (child.orthoView) {
                                Static59.aMatrix_5.makeRotationX(child.xan2d);
                                Static59.aMatrix_5.rotateAxisY(child.yan2d);
                                Static59.aMatrix_5.rotateAxisZ(child.zan2d);
                                Static59.aMatrix_5.translate(child.modelOriginX, child.modelOriginY, child.modelOriginZ);
                            } else {
                                @Pc(936) int local936 = Trig1.SIN[child.xan2d << 3] * (child.zoom2d << 2) >> 14;
                                @Pc(938) int local938 = Trig1.COS[child.xan2d << 3] * (child.zoom2d << 2) >> 14;
                                Static59.aMatrix_5.makeRotationZ(-child.zan2d << 3);
                                Static59.aMatrix_5.rotateAxisY(child.yan2d << 3);
                                Static59.aMatrix_5.translate(child.xof2d << 2, minY + (child.yof2d << 2) + local936, (child.yof2d << 2) + local938);
                                Static59.aMatrix_5.rotateAxisX(child.xan2d << 3);
                            }

                            child.method3384(Toolkit.active, model, Static59.aMatrix_5, TimeUtils.clock);

                            if (!child.orthoView && hasFittedModel(child) && isFittedPreview(child)) {
                                fitModelToBox(child, model, offsetX, offsetY);
                            } else if (!child.orthoView && child.objType == Component.OBJ_TYPE_NPCHEAD && model != null) {
                                measureNpcHead(child, model, local779, local792, local1255, local777, x1, y1, x2, y2);
                            }

                            if (clipComponents || clipModelComponents && isFittedPreview(child)) {
                                Toolkit.active.T(offsetX, offsetY, offsetX + child.width, child.height + offsetY);
                            }

                            if (child.orthoView) {
                                if (child.modelOrthog) {
                                    model.renderOrtho(Static59.aMatrix_5, null, child.zoom2d, 1);
                                } else {
                                    model.render(Static59.aMatrix_5, null, 1);

                                    if (child.particleSystem != null) {
                                        Toolkit.active.render(child.particleSystem.method3650());
                                    }
                                }
                            } else {
                                if (child.modelOrthog) {
                                    model.renderOrtho(Static59.aMatrix_5, null, child.zoom2d << 2, 1);
                                } else {
                                    model.render(Static59.aMatrix_5, null, 1);

                                    if (child.particleSystem != null) {
                                        Toolkit.active.render(child.particleSystem.method3650());
                                    }
                                }
                            }

                            if (clipComponents || clipModelComponents && isFittedPreview(child)) {
                                Toolkit.active.KA(x1, y1, x2, y2);
                            }

                            if (child.disableZBuffer) {
                                Toolkit.active.C(true);
                            }
                        }

                        if (OrthoMode.toolkitActive) {
                            if (aspectRatio) {
                                OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                            } else {
                                Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                            }
                        }
                    } else if (child.type == Component.TYPE_LINE) {
                        @Pc(323) int local323;
                        @Pc(744) int local744;
                        @Pc(1255) int local1255;

                        if (child.lineDirection) {
                            local323 = child.height + offsetY;
                            local1255 = offsetY;
                            local744 = offsetX + child.width;
                        } else {
                            local1255 = child.height + offsetY;
                            local323 = offsetY;
                            local744 = offsetX + child.width;
                        }

                        if (child.lineWidth != 1) {
                            Toolkit.active.strongLine(offsetX, local323, local744, local1255, child.colour, child.lineWidth, 0);
                        } else {
                            Toolkit.active.line(offsetX, local323, local744, local1255, child.colour, 0);
                        }

                        if (OrthoMode.toolkitActive) {
                            if (aspectRatio) {
                                OrthoMode.method8927(boundsLeft, boundsRight, boundsBottom, boundsTop);
                            } else {
                                Static595.method7810(boundsBottom, boundsRight, boundsTop, boundsLeft);
                            }
                        }
                    }
                }
            }
        }
    }

    @OriginalMember(owner = "client!qq", name = "a", descriptor = "(IIIIIIIZI)V")
    public static void draw(@OriginalArg(0) int id, @OriginalArg(1) int x1, @OriginalArg(3) int y1, @OriginalArg(2) int x2, @OriginalArg(8) int y2, @OriginalArg(6) int screenX, @OriginalArg(4) int screenY, @OriginalArg(5) int rectangle) {
        if (InterfaceList.load(id)) {
            @Pc(17) int local17 = 0;
            @Pc(58) int local58 = 0;
            @Pc(60) int local60 = 0;
            @Pc(62) int local62 = 0;
            @Pc(64) int local64 = 0;

            if (OrthoMode.toolkitActive) {
                local62 = Static582.anInt8629;
                local64 = Static691.anInt10368;
                local17 = Static435.anInt6597;
                local58 = Static320.anInt5085;
                local60 = Static599.anInt8837;
                Static691.anInt10368 = 1;
            }

            if (InterfaceList.cache[id] != null) {
                draw(InterfaceList.cache[id], -1, screenX, screenY, x1, y1, x2, y2, rectangle, rectangle < 0);
            } else {
                draw(InterfaceList.interfaces[id], -1, screenX, screenY, x1, y1, x2, y2, rectangle, rectangle < 0);
            }

            if (OrthoMode.toolkitActive) {
                if (rectangle >= 0 && Static691.anInt10368 == 2) {
                    OrthoMode.method8927(Static435.anInt6597, Static599.anInt8837, Static320.anInt5085, Static582.anInt8629);
                }
                Static320.anInt5085 = local58;
                Static582.anInt8629 = local62;
                Static435.anInt6597 = local17;
                Static691.anInt10368 = local64;
                Static599.anInt8837 = local60;
            }
        } else if (rectangle == -1) {
            for (@Pc(17) int local17 = 0; local17 < 100; local17++) {
                dirtyRectangles[local17] = true;
            }
        } else {
            dirtyRectangles[rectangle] = true;
        }
    }

    @OriginalMember(owner = "client!od", name = "a", descriptor = "(IBILclient!hda;)V")
    public static void setOptions(@OriginalArg(0) int optionsY, @OriginalArg(2) int optionsX, @OriginalArg(3) Component optionsComponent) {
        InterfaceManager.optionsY = optionsY;
        InterfaceManager.optionsComponent = optionsComponent;
        InterfaceManager.optionsX = optionsX;
    }

    @OriginalMember(owner = "client!client", name = "b", descriptor = "(Lclient!hda;)Z")
    public static boolean isHidden(@OriginalArg(0) Component component) {
        if (testOpacity) {
            if (serverActiveProperties(component).events != 0) {
                return false;
            }

            if (component.type == Component.TYPE_LAYER) {
                return false;
            }
        }

        return component.hidden;
    }

    @OriginalMember(owner = "client!qi", name = "a", descriptor = "(ZLclient!hda;)V")
    public static void drawSpinningPlayer(@OriginalArg(1) Component component) {
        if (component.clientcode != ComponentClientCode.SPINNING_PLAYER) {
            return;
        }

        if (PlayerEntity.self.nameUnfiltered == null) {
            component.obj = 0;
            component.objData = 0;
            return;
        }

        component.xan2d = 150;
        component.yan2d = (int) (Math.sin((double) TimeUtils.clock / 40.0D) * 256.0D) & 0x7FF;

        component.objType = Component.OBJ_TYPE_PLAYERMODEL;
        component.obj = PlayerList.activePlayerSlot;
        component.objData = StringTools.intHash(PlayerEntity.self.nameUnfiltered);

        @Pc(55) Animator animator = PlayerEntity.self.animator;
        if (animator == null) {
            component.animator = null;
            return;
        }

        if (component.animator == null) {
            component.animator = new ComponentAnimator();
        }

        component.modelAnimation = animator.getAnimationId();
        component.animator.copyFrom(animator);
    }

    @OriginalMember(owner = "client!client", name = "c", descriptor = "(Lclient!hda;)Lclient!ofa;")
    public static ServerActiveProperties serverActiveProperties(@OriginalArg(0) Component component) {
        @Pc(13) ServerActiveProperties properties = (ServerActiveProperties) serverActiveProperties.get(((long) component.slot << 32) + (long) component.id);
        return properties != null ? properties : component.serverActiveProperties;
    }

    @OriginalMember(owner = "client!fw", name = "a", descriptor = "(BI)V")
    public static void resetServerActiveProperties(@OriginalArg(1) int id) {
        for (@Pc(11) Node node = serverActiveProperties.first(); node != null; node = serverActiveProperties.next()) {
            if ((long) id == (node.key >> 48 & 0xFFFFL)) {
                node.unlink();
            }
        }
    }

    @OriginalMember(owner = "client!lg", name = "b", descriptor = "(II)Ljava/lang/String;")
    public static String invText(@OriginalArg(1) int count) {
        @Pc(8) String text = Integer.toString(count);
        for (@Pc(13) int i = text.length() - 3; i > 0; i -= 3) {
            text = text.substring(0, i) + "," + text.substring(i);
        }

        if (text.length() > 9) {
            return " <col=00ff80>" + text.substring(0, text.length() - 8) + LocalisedText.MILLION.localise(Client.language) + " (" + text + ")</col>";
        } else if (text.length() > 6) {
            return " <col=ffffff>" + text.substring(0, text.length() - 4) + LocalisedText.THOUSAND.localise(Client.language) + " (" + text + ")</col>";
        } else {
            return " <col=ffff00>" + text + "</col>";
        }
    }

    @OriginalMember(owner = "client!vn", name = "a", descriptor = "(JI)V")
    public static void method7930(@OriginalArg(0) long arg0) {
        if (Static334.activeTiles != null) {
            if (Camera.mode == CameraMode.MODE_DEFAULT || Camera.mode == CameraMode.MODE_SMOOTH_RESET) {
                Static604.method7903(arg0);
            } else if (Camera.mode == CameraMode.MODE_FOLLOWCOORD) {
                Static349.method5121(arg0);
            }
        }

        ParticleManager.tick(TimeUtils.clock, Toolkit.active);

        if (topLevelInterface != -1) {
            animate(topLevelInterface);
        }

        for (@Pc(54) int i = 0; i < rectangleCount; i++) {
            if (dirtyRectangles[i]) {
                flipDirtyRect[i] = true;
            }

            currentlyDirtyRect[i] = dirtyRectangles[i];
            dirtyRectangles[i] = false;
        }

        lastDrawCycle = TimeUtils.clock;
        setOptions(-1, -1, null);

        if (topLevelInterface != -1) {
            rectangleCount = 0;
            method3833();
        }

        Toolkit.active.la();
        RspsUi.drawTooltip();
        MiniMenu.draw(Toolkit.active);

        @Pc(116) int cursor = MiniMenu.cursor();
        if (cursor == -1) {
            cursor = targetEndCursor;
        }
        if (cursor == -1) {
            cursor = Cursor.dflt;
        }

        Static115.method2136(cursor);
        @Pc(136) int size = PlayerEntity.self.getSize() << 8;
        Static220.method3198(Static35.currentTick, size + PlayerEntity.self.z, PlayerEntity.self.x + size, PlayerEntity.self.level);
        Static35.currentTick = 0;
    }

    @OriginalMember(owner = "client!at", name = "a", descriptor = "(II)V")
    public static void animate(@OriginalArg(1) int id) {
        if (InterfaceList.load(id)) {
            animate(-1, InterfaceList.interfaces[id]);
        }
    }

    @OriginalMember(owner = "client!uf", name = "a", descriptor = "(II[Lclient!hda;)V")
    public static void animate(@OriginalArg(1) int layer, @OriginalArg(2) Component[] components) {
        for (@Pc(3) int i = 0; i < components.length; i++) {
            @Pc(9) Component component = components[i];

            if (component != null && component.layer == layer && !isHidden(component)) {
                if (component.type == Component.TYPE_LAYER) {
                    animate(component.slot, components);

                    if (component.dynamicComponents != null) {
                        animate(component.slot, component.dynamicComponents);
                    }

                    @Pc(56) SubInterface sub = (SubInterface) subInterfaces.get(component.slot);
                    if (sub != null) {
                        animate(sub.id);
                    }
                }

                if (component.type == Component.TYPE_MODEL && component.modelAnimation != -1) {
                    if (component.animator == null) {
                        component.animator = new ComponentAnimator();
                        component.animator.update(true, component.modelAnimation);
                    }

                    // N319 (owner 2026-10-24, N283: the Drop Viewer's models stand still): there the standing animation gives only the
                    // pose - its first frame - and never plays on (the npcs moved). The store's pet showcase keeps playing its idle.
                    if (!isStillPreview(component) && component.animator.tick(Static35.currentTick) && component.animator.isFinished()) {
                        component.animator.resetImmediately();
                    }
                }
            }
        }
    }

    @OriginalMember(owner = "client!aq", name = "a", descriptor = "(I)V")
    public static void method680() {
        ParticleManager.tick(TimeUtils.clock, Toolkit.active);
        if (topLevelInterface != -1) {
            animate(topLevelInterface);
        }
        for (@Pc(23) int local23 = 0; local23 < rectangleCount; local23++) {
            if (dirtyRectangles[local23]) {
                flipDirtyRect[local23] = true;
            }
            currentlyDirtyRect[local23] = dirtyRectangles[local23];
            dirtyRectangles[local23] = false;
        }
        lastDrawCycle = TimeUtils.clock;
        if (topLevelInterface != -1) {
            rectangleCount = 0;
            method3833();
        }
        Toolkit.active.la();
        RspsUi.drawTooltip();
        MiniMenu.draw(Toolkit.active);
        @Pc(77) int local77 = MiniMenu.cursor();
        if (local77 == -1) {
            local77 = targetEndCursor;
        }
        if (local77 == -1) {
            local77 = Cursor.dflt;
        }
        Static115.method2136(local77);
        Static35.currentTick = 0;
    }

    @OriginalMember(owner = "client!ic", name = "a", descriptor = "(I)V")
    public static void method3833() {
        dragChildren = null;
        @Pc(46) int x1 = 0;
        @Pc(48) int y1 = 0;
        if (OrthoMode.toolkitActive) {
            x1 = OrthoMode.method2283();
            y1 = Static422.method5771();
        }
        // RSPS Deadman HUD: N105 (owner 2026-10-17, reverses E16 10-03) - the HUD lies BEHIND every open window (bank, GE, drop
        // viewer, info window, world map), so it is drawn in the scene pass (Static294.method4339, beside RspsHud) under the
        // interface tree instead of after it; the scene pass needs the canvas origin of this frame.
        DeadmanSkullHud.frameOriginX = x1;
        DeadmanSkullHud.frameOriginY = y1;
        if (OrthoMode.toolkitActive && getWindowMode() != 1) {
            Static294.method4339(0, MainLogicManager.step == 3 || MainLogicManager.step == 7, OrthoMode.method7779(), OrthoMode.method1260(), 0);
        }

        draw(topLevelInterface, x1, y1, GameShell.canvasWid + x1, GameShell.canvasHei + y1, x1, y1, -1);

        RspsPkHud.drawInterfaceOverlay(); // 78Legacy v5 PK HUD: outline of the protect prayer against your target's weapon

        if (dragChildren != null) {
            draw(dragChildren, ROOT, dragOffsetX, dragOffsetY, x1, y1, x1 + GameShell.canvasWid, y1 + GameShell.canvasHei, dragLayer.rectangle, true);
            dragChildren = null;
        }
    }

    @OriginalMember(owner = "client!bda", name = "c", descriptor = "(I)I")
    public static int getWindowMode() {
        if (GameShell.fsframe != null) {
            return WindowMode.FULLSCREEN;
        } else if (resizableScreen) {
            return WindowMode.RESIZABLE;
        } else {
            return WindowMode.FIXED;
        }
    }

    @OriginalMember(owner = "client!client", name = "a", descriptor = "([Lclient!hda;IIIIIIIIIII)V")
    public static void logicComponentList(@OriginalArg(0) Component[] components, @OriginalArg(1) int layer, @OriginalArg(2) int parentX1, @OriginalArg(3) int parentY1, @OriginalArg(4) int parentX2, @OriginalArg(5) int parentY2, @OriginalArg(6) int scrollDeltaX, @OriginalArg(7) int scrollDeltaY, @OriginalArg(8) int mouseX1, @OriginalArg(9) int mouseY1, @OriginalArg(10) int mouseX2, @OriginalArg(11) int mouseY2) {
        for (@Pc(1) int i = 0; i < components.length; i++) {
            @Pc(6) Component component = components[i];
            if (component == null || component.layer != layer) {
                continue;
            }

            @Pc(19) int startX = component.x + scrollDeltaX;
            @Pc(24) int startY = component.y + scrollDeltaY;
            @Pc(30) int x1;
            @Pc(32) int y1;
            @Pc(34) int x2;
            @Pc(36) int y2;

            if (component.type == Component.TYPE_INVENTORY) {
                x1 = parentX1;
                y1 = parentY1;
                x2 = parentX2;
                y2 = parentY2;
            } else {
                @Pc(42) int endX = startX + component.width;
                @Pc(47) int endY = startY + component.height;

                if (component.type == Component.TYPE_LINE) {
                    endX++;
                    endY++;
                }

                x1 = startX > parentX1 ? startX : parentX1;
                y1 = startY > parentY1 ? startY : parentY1;
                x2 = endX < parentX2 ? endX : parentX2;
                y2 = endY < parentY2 ? endY : parentY2;
            }

            if ((component.type == Component.TYPE_LAYER) || component.hasHook || (serverActiveProperties(component).events != 0) || (component == dragLayer) || (component.clientcode == ComponentClientCode.MINIMAP) || (component.clientcode == ComponentClientCode.WORLD_MAP_OPTIONS) || (component.clientcode == ComponentClientCode.SCENE) || (component.clientcode == ComponentClientCode.LOGIN_SCENE)) {
                if (isHidden(component)) {
                    continue;
                }
                // N158 (2026-10-22): a frame component the intro's cinematic mode does not draw (RspsCinematic.hide) takes no
                // clicks either - the invisible resizable logout X opened the Logout tab once the player reached the hall.
                if (RspsCinematic.hide(component)) {
                    continue;
                }

                @Pc(42) int orthoDeltaX = 0;
                @Pc(47) int orthoDeltaY = 0;
                if (OrthoMode.toolkitActive) {
                    orthoDeltaX = OrthoMode.method2283();
                    orthoDeltaY = Static422.method5771();
                }

                if (component == dragSource && getServerDragLayer(dragSource) != null) {
                    Static702.aBoolean797 = true;
                    dragLastX = startX;
                    dragLastY = startY;
                }

                if (component.hasOpKey || x1 < x2 && y1 < y2) {
                    if (component.noClickThrough && mouseX2 >= x1 && mouseY2 >= y1 && mouseX2 < x2 && mouseY2 < y2) {
                        for (@Pc(220) HookRequest hook = (HookRequest) hookRequests.first(); hook != null; hook = (HookRequest) hookRequests.next()) {
                            if (hook.mouseEvent) {
                                hook.unlink();
                                hook.source.hovered = false;
                            }
                        }

                        if (dragTicks == 0) {
                            dragSource = null;
                            dragLayer = null;
                        }

                        MiniMenu.anInt6964 = 0;
                        WorldMap.hovered = false;
                        WorldMap.clicked = false;

                        if (!MiniMenu.open) {
                            MiniMenu.reset();
                        }
                    }

                    @Pc(308) boolean clickMask = component.clickMask && component.type == Component.TYPE_GRAPHIC && component.transparency == 0 && component.skyBox < 0 && component.invObject == -1 && component.video == -1 && !component.tiling && component.angle2d == 0;

                    @Pc(310) boolean hovered = false;
                    if (MouseMonitor.instance.getRecordedX() + orthoDeltaX >= x1 && MouseMonitor.instance.getRecordedY() + orthoDeltaY >= y1 && MouseMonitor.instance.getRecordedX() + orthoDeltaX < x2 && MouseMonitor.instance.getRecordedY() + orthoDeltaY < y2) {
                        if (clickMask) {
                            @Pc(353) Graphic graphic = component.graphic(Toolkit.active);

                            if (graphic != null && graphic.scaleWidth == component.width && graphic.scaleHeight == component.height) {
                                @Pc(380) int x = MouseMonitor.instance.getRecordedX() + orthoDeltaX - startX;
                                @Pc(388) int y = MouseMonitor.instance.getRecordedY() + orthoDeltaY - startY;

                                if (y >= 0 && y < graphic.lineOffsets.length) {
                                    @Pc(402) int lineOffset = graphic.lineOffsets[y];

                                    if (x >= lineOffset && x <= lineOffset + graphic.lineWidths[y]) {
                                        hovered = true;
                                    }
                                }
                            } else {
                                hovered = true;
                            }
                        } else {
                            hovered = true;
                        }
                    }

                    if (hovered && Static611.mouseWheelRotation != 0) {
                    if (component.clientcode == ComponentClientCode.SCENE) {
                        mouseWheelOverScene = true;
                        mouseWheelClaimedByInterface = false; // Scene covers earlier background layers.
                        } else if (component.onScrollWheel != null || component.noClickThrough || component.type == Component.TYPE_INVENTORY || component.clientcode != 0) {
                            mouseWheelClaimedByInterface = true;
                        }
                    }

                    if (!targetMode && hovered) {
                        if (component.mouseOverCursor >= 0) {
                            targetEndCursor = component.mouseOverCursor;
                        } else if (component.noClickThrough) {
                            targetEndCursor = -1;
                        }
                    }

                    if (!MiniMenu.open && hovered) {
                        addMiniMenuOptions(component, mouseX2 - startX, mouseX2 - startY);
                    }

                    @Pc(462) boolean pressedOver = false;
                    if (MouseMonitor.instance.isLeftDown() && hovered) {
                        pressedOver = true;
                    }

                    @Pc(474) boolean clicked = false;
                    @Pc(479) MouseLog log = (MouseLog) Static226.mouseLogs.first();
                    if (log != null && log.getType() == MouseLog.TYPE_PRESS_LEFT && log.getX() >= x1 && log.getY() >= y1 && log.getX() < x2 && log.getY() < y2) {
                        if (clickMask) {
                            @Pc(524) Graphic graphic = component.graphic(Toolkit.active);

                            if (graphic != null && graphic.scaleWidth == component.width && graphic.scaleHeight == component.height) {
                                @Pc(549) int x = log.getX() - startX;
                                @Pc(555) int y = log.getY() - startY;

                                if (y >= 0 && y < graphic.lineOffsets.length) {
                                    @Pc(569) int lineOffset = graphic.lineOffsets[y];

                                    if (x >= lineOffset && x <= lineOffset + graphic.lineWidths[y]) {
                                        clicked = true;
                                    }
                                }
                            } else {
                                clicked = true;
                            }
                        } else {
                            clicked = true;
                        }
                    }

                    if (component.opKeys != null && !debugconsole.isOpen()) {
                        for (@Pc(402) int key = 0; key < component.opKeys.length; key++) {
                            // RSPS place mode: its keys (arrows, PgUp/PgDn ...) never also drive the gameframe's camera op keys.
                            // OSRS keybinding (owner 2026-10-11): F1-F12 open only the tab bound to them, not the 667 tab button's own key.
                            if (RspsDev.ownsKeyCode(component.opKeys[key]) || RspsFunctionKeys.ownsKeyCode(component.opKeys[key])) {
                                continue;
                            }
                            if (KeyboardMonitor.instance.isPressed(component.opKeys[key])) {
                                if (component.opKeysIgnoreHeld == null || TimeUtils.clock >= component.opKeysIgnoreHeld[key]) {
                                    @Pc(634) byte mask = component.opChars[key];

                                    if ((mask == 0) || ((((mask & 0x8) == 0) || (!KeyboardMonitor.instance.isPressed(86) && !KeyboardMonitor.instance.isPressed(82) && !KeyboardMonitor.instance.isPressed(81))) && (((mask & 0x2) == 0) || KeyboardMonitor.instance.isPressed(86)) && (((mask & 0x1) == 0) || KeyboardMonitor.instance.isPressed(82)) && (((mask & 0x4) == 0) || KeyboardMonitor.instance.isPressed(81)))) {
                                        if (key < 10) {
                                            ifButtonXSend(component.slot, -1, "", key + 1);
                                        } else if (key == 10) {
                                            endTargetMode();

                                            @Pc(726) ServerActiveProperties properties = serverActiveProperties(component);
                                            enterTargetMode(properties.getTargetMask(), component, properties.targetParam);

                                            targetVerb = getComponentTargetVerb(component);
                                            if (targetVerb == null) {
                                                targetVerb = "Null";
                                            }

                                            targetedVerb = component.opBase + "<col=ffffff>";
                                        }

                                        @Pc(555) int delay = component.opKeyRates[key];
                                        if (component.opKeysIgnoreHeld == null) {
                                            component.opKeysIgnoreHeld = new int[component.opKeys.length];
                                        }

                                        if (delay != 0) {
                                            component.opKeysIgnoreHeld[key] = TimeUtils.clock + delay;
                                        } else {
                                            component.opKeysIgnoreHeld[key] = Integer.MAX_VALUE;
                                        }
                                    }
                                }
                            } else if (component.opKeysIgnoreHeld != null) {
                                component.opKeysIgnoreHeld[key] = 0;
                            }
                        }
                    }

                    if (clicked) {
                        dragTryPickup(component, orthoDeltaX + log.getX() - startX, orthoDeltaY + log.getY() - startY);
                    }

                    if (dragSource != null && dragSource != component && hovered && serverActiveProperties(component).isDragTarget()) {
                        dragTarget = component;
                    }

                    if (component == dragLayer) {
                        aBoolean428 = true;
                        dragParentX = startX;
                        dragParentY = startY;
                    }

                    if (component.hasHook || component.clientcode != 0) {
                        if (hovered && Static611.mouseWheelRotation != 0 && component.onScrollWheel != null) {
                            mouseWheelClaimedByInterface = true;
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.mouseEvent = true;
                            hook.source = component;
                            hook.mouseY = Static611.mouseWheelRotation;
                            hook.arguments = component.onScrollWheel;
                            hookRequests.addLast(hook);
                        }

                        if (dragSource != null) {
                            clicked = false;
                            pressedOver = false;
                        } else if (MiniMenu.open || (component.clientcode != ComponentClientCode.WORLD_MAP && MiniMenu.anInt6964 > 0)) {
                            clicked = false;
                            pressedOver = false;
                            hovered = false;
                        }

                        if (component.clientcode != 0) {
                            if (component.clientcode == ComponentClientCode.SCENE || component.clientcode == ComponentClientCode.LOGIN_SCENE) {
                                scene = component;

                                if (Static456.aSkyBox_3 != null) {
                                    Static456.aSkyBox_3.method3168(ClientOptions.instance.skydetail.getValue(), component.height, Toolkit.active);
                                }

                                if (component.clientcode == ComponentClientCode.SCENE) {
                                    if (MiniMenu.open || mouseX2 < x1 || mouseY2 < y1 || mouseX2 >= x2 || mouseY2 >= y2) {
                                        continue;
                                    }

                                    MiniMenu.addEntries3DView(Toolkit.active, mouseX1, mouseY1);

                                    for (@Pc(991) Class8_Sub1 local991 = (Class8_Sub1) Static149.A_ENTITY_LIST___4.first(); local991 != null; local991 = (Class8_Sub1) Static149.A_ENTITY_LIST___4.next()) {
                                        if (mouseX2 >= local991.anInt108 && mouseX2 < local991.anInt109 && mouseY2 >= local991.anInt112 && mouseY2 < local991.anInt111) {
                                            MiniMenu.reset();
                                            MiniMenu.addEntityEntries(local991.aClass8_Sub2_Sub1_Sub2_1);
                                        }
                                    }
                                }
                            }

                            if (component.clientcode == ComponentClientCode.MINIMAP) {
                                @Pc(524) Graphic graphic = component.graphic(Toolkit.active);
                                if (graphic == null || Minimap.toggle != 0 && Minimap.toggle != 3 || MiniMenu.open || mouseX2 < x1 || mouseY2 < y1 || mouseX2 >= x2 || mouseY2 >= y2) {
                                    continue;
                                }

                                @Pc(549) int x = mouseX2 - startX;
                                @Pc(555) int y = mouseY2 - startY;
                                @Pc(569) int lineOffset = graphic.lineOffsets[y];
                                if (x < lineOffset || x > lineOffset + graphic.lineWidths[y]) {
                                    continue;
                                }

                                x -= component.width / 2;
                                y -= component.height / 2;

                                @Pc(1125) int yaw;
                                if (Camera.mode == CameraMode.MODE_FOLLOWCOORD) {
                                    yaw = (int) Camera.playerCameraYaw & 0x3FFF;
                                } else {
                                    yaw = (int) Camera.playerCameraYaw + Camera.yawOffset & 0x3FFF;
                                }

                                @Pc(1137) int sinYaw = Trig1.SIN[yaw];
                                @Pc(1141) int cosYaw = Trig1.COS[yaw];
                                if (Camera.mode != CameraMode.MODE_FOLLOWCOORD) {
                                    sinYaw = sinYaw * (Camera.scaleOffset + 256) >> 8;
                                    cosYaw = cosYaw * (Camera.scaleOffset + 256) >> 8;
                                }
                                // RuneLite Minimap "Zoom": screen pixels back to tiles at the zoomed scale.
                                sinYaw = RspsMinimapZoom.unscale(sinYaw);
                                cosYaw = RspsMinimapZoom.unscale(cosYaw);
                                RspsMinimapZoom.hovered = true;

                                @Pc(1170) int local1170 = ((y * sinYaw) + (x * cosYaw)) >> 14;
                                @Pc(1180) int local1180 = ((y * cosYaw) - (x * sinYaw)) >> 14;

                                @Pc(1191) int local1191;
                                @Pc(1199) int local1199;
                                if (Camera.mode == CameraMode.MODE_FOLLOWCOORD) {
                                    local1191 = (Camera.anInt6262 >> 9) + (local1170 >> 2);
                                    local1199 = (Camera.anInt4018 >> 9) - (local1180 >> 2);
                                } else {
                                    @Pc(1208) int local1208 = (PlayerEntity.self.getSize() - 1) * 256;
                                    local1191 = (PlayerEntity.self.x - local1208 >> 9) + (local1170 >> 2);
                                    local1199 = (PlayerEntity.self.z - local1208 >> 9) - (local1180 >> 2);
                                }

                                if (targetMode && (targetMask & TargetMask.TGT_GROUND) != 0) {
                                    @Pc(1243) Component target = InterfaceList.getComponent(targetSlot, targetComponent);

                                    if (target != null) {
                                        MiniMenu.addEntryInner(false, component.invObject, 1L, local1191, local1199, targetVerb, MiniMenuAction.TGT_GROUND, true, targetEnterCursor, " ->", (component.id << 0) | component.slot, true);
                                    } else {
                                        endTargetMode();
                                    }
                                } else {
                                    if (Client.modeGame == ModeGame.STELLAR_DAWN) {
                                        MiniMenu.addEntryInner(false, -1, 1L, local1191, local1199, LocalisedText.FACEHERE.localise(Client.language), MiniMenuAction.FACE_SQUARE, true, -1, "", 0L, true);
                                    }

                                    MiniMenu.addEntryInner(false, -1, 1L, local1191, local1199, Static331.moveText, MiniMenuAction.WALK, true, Static331.moveCursor, "", 0L, true);
                                    // RuneLite Minimap: right-click the minimap to reset its zoom.
                                    if (RspsMinimapZoom.isZoomed()) {
                                        MiniMenu.addEntryInner(false, -1, 0L, 0, 0, "Reset", RspsMinimapZoom.ACTION_RESET + 2000, true, -1, "<col=ff9040>Zoom", 0L, false);
                                    }
                                }

                                continue;
                            }

                            if (component.clientcode == ComponentClientCode.WORLD_MAP) {
                                WorldMap.component = component;

                                if (hovered) {
                                    WorldMap.hovered = true;
                                }

                                if (clicked) {
                                    @Pc(402) int local402 = (int) ((double) (orthoDeltaX + log.getX() - startX - component.width / 2) * 2.0D / (double) WorldMap.currentZoom);
                                    @Pc(549) int local549 = (int) -((double) (orthoDeltaY + log.getY() - startY - component.height / 2) * 2.0D / (double) WorldMap.currentZoom);
                                    @Pc(555) int local555 = WorldMap.displayX + local402 + WorldMap.areaX;
                                    @Pc(569) int local569 = WorldMap.displayZ + local549 + WorldMap.areaZ;

                                    @Pc(1383) WorldMapArea area = WorldMap.getArea();
                                    if (area == null) {
                                        continue;
                                    }

                                    @Pc(1388) int[] local1388 = new int[3];
                                    area.projectSource(local1388, local555, local569);

                                    if (local1388 != null) {
                                        int level = local1388[0];
                                        int y = local1388[2];
                                        int x = local1388[1];

                                        if (KeyboardMonitor.instance.isPressed(82) && Client.staffModLevel >= ClientConfig.TELEPORT_MIN_STAFF_LEVEL) { // K-05: admins only
                                            Static624.teleport(level, y, x);
                                            continue;
                                        }

                                        WorldMap.clicked = true;
                                        WorldMap.clickedLevel = level;
                                        WorldMap.clickedX = x;
                                        WorldMap.clickedY = y;
                                    }

                                    MiniMenu.anInt6964 = 1;
                                    Static1.aBoolean821 = false;
                                    dragStartX = MouseMonitor.instance.getRecordedX();
                                    dragStartY = MouseMonitor.instance.getRecordedY();
                                    continue;
                                }

                                if (pressedOver && MiniMenu.anInt6964 > 0) {
                                    if (MiniMenu.anInt6964 == 1 && (dragStartX != MouseMonitor.instance.getRecordedX() || dragStartY != MouseMonitor.instance.getRecordedY())) {
                                        Static661.anInt6055 = WorldMap.displayX;
                                        Static417.anInt6399 = WorldMap.displayZ;
                                        MiniMenu.anInt6964 = 2;
                                    }
                                    if (MiniMenu.anInt6964 == 2) {
                                        Static1.aBoolean821 = true;
                                        WorldMap.method8711(Static661.anInt6055 + (int) ((double) (dragStartX - MouseMonitor.instance.getRecordedX()) * 2.0D / (double) WorldMap.targetZoom));
                                        Static182.method2786(Static417.anInt6399 - (int) ((double) (dragStartY - MouseMonitor.instance.getRecordedY()) * 2.0D / (double) WorldMap.targetZoom));
                                    }
                                    continue;
                                }

                                if (MiniMenu.anInt6964 > 0 && !Static1.aBoolean821) {
                                    if ((Client.mouseButtons == 1 || MiniMenu.topEntryIsIfButtonX1()) && MiniMenu.innerEntryCount > 2) {
                                        MiniMenu.method6223(dragStartX, dragStartY);
                                    } else if (MiniMenu.isPopulated()) {
                                        MiniMenu.method6223(dragStartX, dragStartY);
                                    }
                                }

                                MiniMenu.anInt6964 = 0;
                                continue;
                            }

                            if (component.clientcode == ComponentClientCode.WORLD_MAP_OVERVIEW) {
                                if (pressedOver) {
                                    WorldMap.clickedOverview(component.width, orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX, orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY, component.height);
                                }
                                continue;
                            }

                            if (component.clientcode == ComponentClientCode.WORLD_MAP_OPTIONS) {
                                WorldMap.setOptions(startX, startY, component);
                                continue;
                            }
                        }

                        if (!component.clicked && clicked) {
                            component.clicked = true;

                            if (component.onClick != null) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.mouseEvent = true;
                                hook.source = component;
                                hook.mouseX = orthoDeltaX + log.getX() - startX;
                                hook.mouseY = orthoDeltaY + log.getY() - startY;
                                hook.arguments = component.onClick;
                                hookRequests.addLast(hook);
                            }
                        }

                        if (component.clicked && pressedOver && component.onClickRepeat != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.mouseEvent = true;
                            hook.source = component;
                            hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                            hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                            hook.arguments = component.onClickRepeat;
                            hookRequests.addLast(hook);
                        }

                        if (component.clicked && !pressedOver) {
                            component.clicked = false;

                            if (component.onRelease != null) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.mouseEvent = true;
                                hook.source = component;
                                hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                                hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                                hook.arguments = component.onRelease;
                                Static59.A_DEQUE___33.addLast(hook);
                            }
                        }

                        if (pressedOver && component.onHold != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.mouseEvent = true;
                            hook.source = component;
                            hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                            hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                            hook.arguments = component.onHold;
                            hookRequests.addLast(hook);
                        }

                        if (!component.hovered && hovered) {
                            component.hovered = true;

                            if (component.onMouseOver != null) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.mouseEvent = true;
                                hook.source = component;
                                hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                                hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                                hook.arguments = component.onMouseOver;
                                hookRequests.addLast(hook);
                            }
                        }

                        if (component.hovered && hovered && component.onMouseRepeat != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.mouseEvent = true;
                            hook.source = component;
                            hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                            hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                            hook.arguments = component.onMouseRepeat;
                            hookRequests.addLast(hook);
                        }

                        if (component.hovered && !hovered) {
                            component.hovered = false;

                            if (component.onMouseLeave != null) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.mouseEvent = true;
                                hook.source = component;
                                hook.mouseX = orthoDeltaX + MouseMonitor.instance.getRecordedX() - startX;
                                hook.mouseY = orthoDeltaY + MouseMonitor.instance.getRecordedY() - startY;
                                hook.arguments = component.onMouseLeave;
                                Static59.A_DEQUE___33.addLast(hook);
                            }
                        }

                        if (component.onTimer != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onTimer;
                            Static618.A_DEQUE___68.addLast(hook);
                        }

                        if (component.onVarcTransmit != null && Static52.varcUpdateCount > component.lastVarcUpdate) {
                            if (component.varcTriggers == null || Static52.varcUpdateCount - component.lastVarcUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onVarcTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label768:
                                for (@Pc(402) int j = component.lastVarcUpdate; j < Static52.varcUpdateCount; j++) {
                                    @Pc(549) int local549 = Static278.anIntArray350[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.varcTriggers.length; k++) {
                                        if (component.varcTriggers[k] == local549) {
                                            @Pc(2022) HookRequest hook = new HookRequest();
                                            hook.source = component;
                                            hook.arguments = component.onVarcTransmit;
                                            hookRequests.addLast(hook);
                                            break label768;
                                        }
                                    }
                                }
                            }

                            component.lastVarcUpdate = Static52.varcUpdateCount;
                        }

                        if (component.onVarcstrTransmit != null && Static455.varcstrUpdateCount > component.lastVarcstrUpdate) {
                            if (component.varcstrTriggers == null || Static455.varcstrUpdateCount - component.lastVarcstrUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onVarcstrTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label744:
                                for (@Pc(402) int j = component.lastVarcstrUpdate; j < Static455.varcstrUpdateCount; j++) {
                                    @Pc(549) int local549 = Static268.anIntArray332[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.varcstrTriggers.length; k++) {
                                        if (component.varcstrTriggers[k] == local549) {
                                            @Pc(2022) HookRequest hook = new HookRequest();
                                            hook.source = component;
                                            hook.arguments = component.onVarcstrTransmit;
                                            hookRequests.addLast(hook);
                                            break label744;
                                        }
                                    }
                                }
                            }

                            component.lastVarcstrUpdate = Static455.varcstrUpdateCount;
                        }

                        if (component.onVarTransmit != null && Static635.varpUpdateCount > component.lastVarpUpdate) {
                            if (component.varpTriggers == null || Static635.varpUpdateCount - component.lastVarpUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onVarTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label720:
                                for (@Pc(402) int j = component.lastVarpUpdate; j < Static635.varpUpdateCount; j++) {
                                    @Pc(549) int local549 = Static142.anIntArray225[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.varpTriggers.length; k++) {
                                        if (component.varpTriggers[k] == local549) {
                                            @Pc(2022) HookRequest hook = new HookRequest();
                                            hook.source = component;
                                            hook.arguments = component.onVarTransmit;
                                            hookRequests.addLast(hook);
                                            break label720;
                                        }
                                    }
                                }
                            }

                            component.lastVarpUpdate = Static635.varpUpdateCount;
                        }

                        if (component.onInvTransmit != null && ClientInventory.updateCount > component.lastInvUpdate) {
                            if (component.inventoryTriggers == null || ClientInventory.updateCount - component.lastInvUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onInvTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label696:
                                for (@Pc(402) int j = component.lastInvUpdate; j < ClientInventory.updateCount; j++) {
                                    @Pc(549) int local549 = ClientInventory.updates[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.inventoryTriggers.length; k++) {
                                        if (component.inventoryTriggers[k] == local549) {
                                            @Pc(2022) HookRequest hook = new HookRequest();
                                            hook.source = component;
                                            hook.arguments = component.onInvTransmit;
                                            hookRequests.addLast(hook);
                                            break label696;
                                        }
                                    }
                                }
                            }

                            component.lastInvUpdate = ClientInventory.updateCount;
                        }

                        if (component.onStatTransmit != null && Static366.statUpdateCount > component.lastStatUpdate) {
                            if (component.statTriggers == null || Static366.statUpdateCount - component.lastStatUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onStatTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label672:
                                for (@Pc(402) int j = component.lastStatUpdate; j < Static366.statUpdateCount; j++) {
                                    @Pc(549) int local549 = Static395.statUpdates[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.statTriggers.length; k++) {
                                        if (component.statTriggers[k] == local549) {
                                            @Pc(2022) HookRequest hook = new HookRequest();
                                            hook.source = component;
                                            hook.arguments = component.onStatTransmit;
                                            hookRequests.addLast(hook);
                                            break label672;
                                        }
                                    }
                                }
                            }

                            component.lastStatUpdate = Static366.statUpdateCount;
                        }

                        if (component.onVarclanTransmit != null && Static710.varclanUpdateCount > component.lastVarclanUpdate) {
                            if (component.varclanTriggers == null || Static710.varclanUpdateCount - component.lastVarclanUpdate > 32) {
                                @Pc(877) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.arguments = component.onVarclanTransmit;
                                hookRequests.addLast(hook);
                            } else {
                                label648:
                                for (@Pc(402) int j = component.lastVarclanUpdate; j < Static710.varclanUpdateCount; j++) {
                                    @Pc(549) int local549 = Static265.varclanUpdates[j & 0x1F];

                                    for (@Pc(555) int k = 0; k < component.varclanTriggers.length; k++) {
                                        if (component.varclanTriggers[k] == local549) {
                                            @Pc(2022) HookRequest local2022 = new HookRequest();
                                            local2022.source = component;
                                            local2022.arguments = component.onVarclanTransmit;
                                            hookRequests.addLast(local2022);
                                            break label648;
                                        }
                                    }
                                }
                            }

                            component.lastVarclanUpdate = Static710.varclanUpdateCount;
                        }

                        if (ChatHistory.lastTransmit > component.lastScriptTransmit && component.onChatTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onChatTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (FriendsList.lastTransmit > component.lastScriptTransmit && component.onFriendTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onFriendTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (Static352.lastClanTransmit > component.lastScriptTransmit && component.onClanTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onClanTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (Static400.lastClanSettingsTransmit > component.lastScriptTransmit && component.onClanSettingsTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onClanSettingsTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (Static39.lastClanChannelTransmit > component.lastScriptTransmit && component.onClanChannelTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onClanChannelTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (StockmarketManager.lastTransmit > component.lastScriptTransmit && component.onStockTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onStockTransmit;
                            hookRequests.addLast(hook);
                        }

                        if (Static321.lastMiscTransmit > component.lastScriptTransmit && component.onMiscTransmit != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onMiscTransmit;
                            hookRequests.addLast(hook);
                        }

                        component.lastScriptTransmit = World.tick;

                        if (component.onKey != null) {
                            for (@Pc(402) int j = 0; j < Static671.anInt10026; j++) {
                                @Pc(2682) HookRequest hook = new HookRequest();
                                hook.source = component;
                                hook.keyCode = Static194.AN_KEYBOARD_EVENT_ARRAY_1[j].getKeyCode();
                                hook.keyChar = Static194.AN_KEYBOARD_EVENT_ARRAY_1[j].getKeyChar();
                                hook.arguments = component.onKey;
                                hookRequests.addLast(hook);
                            }
                        }

                        if (Camera.splineFinished && component.onCamFinished != null) {
                            @Pc(877) HookRequest hook = new HookRequest();
                            hook.source = component;
                            hook.arguments = component.onCamFinished;
                            hookRequests.addLast(hook);
                        }
                    }

                    if (component.type == Component.TYPE_GRAPHIC && component.skyBox != -1) {
                        component.skyBox(SkyBoxSphereTypeList.instance, SkyBoxTypeList.instance).method3168(ClientOptions.instance.skydetail.getValue(), component.height, Toolkit.active);
                    }

                    Static542.prefetchSprite(component);

                    if (component.type == 0) {
                        logicComponentList(components, component.slot, x1, y1, x2, y2, startX - component.scrollX, startY - component.scrollY, mouseX1, mouseY1, mouseX2, mouseY2);

                        if (component.dynamicComponents != null) {
                            logicComponentList(component.dynamicComponents, component.slot, x1, y1, x2, y2, startX - component.scrollX, startY - component.scrollY, mouseX1, mouseY1, mouseX2, mouseY2);
                        }

                        @Pc(2824) SubInterface sub = (SubInterface) subInterfaces.get(component.slot);
                        if (sub != null) {
                            if (Client.modeGame == ModeGame.RUNESCAPE && sub.type == Component.TYPE_LAYER && !MiniMenu.open && hovered && !testOpacity) {
                                MiniMenu.reset();
                            }

                            mainLogic(sub.id, x1, y1, mouseX2, y2, startX, startY, mouseX1, mouseY1, mouseY2, x2);
                        }
                    }
                }
            } else if (x1 < x2 && y1 < y2) {
                Static542.prefetchSprite(component);
            }
        }
    }

    @OriginalMember(owner = "client!sfa", name = "a", descriptor = "(Lclient!hda;Z)Lclient!hda;")
    public static Component getServerDragLayer(@OriginalArg(0) Component arg0) {
        @Pc(15) Component local15 = Static84.method1657(arg0);
        if (local15 == null) {
            local15 = arg0.dragLayer;
        }
        return local15;
    }

    @OriginalMember(owner = "client!dn", name = "a", descriptor = "(IIILjava/lang/String;I)V")
    public static void ifButtonXSend(@OriginalArg(1) int idAndSlot, @OriginalArg(2) int component, @OriginalArg(3) String arg2, @OriginalArg(4) int op) {
        @Pc(8) Component button = InterfaceList.getComponent(idAndSlot, component);
        if (button == null) {
            return;
        }

        if (button.onOp != null) {
            @Pc(19) HookRequest hook = new HookRequest();
            hook.arguments = button.onOp;
            hook.op = op;
            hook.opBase = arg2;
            hook.source = button;
            ScriptRunner.executeHookInner(hook);
        }

        if (MainLogicManager.step != 11 || !serverActiveProperties(button).isOpEnabled(op - 1)) {
            return;
        }
        // 78Legacy v4: every prayer / quick-prayer click that really goes out counts in the script prayer model.
        RspsPrayerModel.sent(idAndSlot, component, op);

        if (op == 1) {
            @Pc(64) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON1, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 2) {
            @Pc(64) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON2, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 3) {
            @Pc(64) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON3, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 4) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON4, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 5) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON5, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 6) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON6, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 7) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON7, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 8) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON8, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 9) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON9, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        } else if (op == 10) {
            @Pc(148) ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTON10, ServerConnection.GAME.isaac);
            ClientMessage.addComponentData(idAndSlot, button.invObject, message, component);
            ServerConnection.GAME.send(message);
        }
    }

    @OriginalMember(owner = "client!md", name = "a", descriptor = "(IBLclient!hda;I)V")
    public static void enterTargetMode(@OriginalArg(0) int targetMask, @OriginalArg(2) Component target, @OriginalArg(3) int targetParam) {
        if (target == null) {
            return;
        }

        if (target.onTargetEnter != null) {
            @Pc(14) HookRequest hook = new HookRequest();
            hook.source = target;
            hook.arguments = target.onTargetEnter;
            ScriptRunner.executeHookInner(hook);
        }

        InterfaceManager.targetInvObj = target.invObject;
        InterfaceManager.targetSlot = target.slot;
        InterfaceManager.targetMode = true;
        InterfaceManager.targetParam = targetParam;
        InterfaceManager.targetEnterCursor = target.targetEnterCursor;
        InterfaceManager.targetComponent = target.id;
        InterfaceManager.targetEndCursor = target.targetEndCursor;
        InterfaceManager.targetMask = targetMask;
        redraw(target);
    }

    @OriginalMember(owner = "client!or", name = "h", descriptor = "(I)V")
    public static void endTargetMode() {
        if (!targetMode) {
            return;
        }

        @Pc(14) Component target = InterfaceList.getComponent(InterfaceManager.targetSlot, InterfaceManager.targetComponent);
        if (target != null && target.onTargetLeave != null) {
            @Pc(25) HookRequest hook = new HookRequest();
            hook.arguments = target.onTargetLeave;
            hook.source = target;
            ScriptRunner.executeHookInner(hook);
        }

        InterfaceManager.targetEndCursor = -1;
        InterfaceManager.targetMode = false;
        InterfaceManager.targetInvObj = -1;

        if (target != null) {
            redraw(target);
        }
    }

    @OriginalMember(owner = "client!sr", name = "a", descriptor = "(ILclient!hda;ZI)V")
    public static void dragTryPickup(@OriginalArg(1) Component dragSource, @OriginalArg(3) int dragStartX, @OriginalArg(0) int dragStartY) {
        if (InterfaceManager.dragSource != null || MiniMenu.open || (dragSource == null || getServerDragLayer(dragSource) == null)) {
            return;
        }

        InterfaceManager.dragSource = dragSource;
        InterfaceManager.dragLayer = getServerDragLayer(dragSource);
        InterfaceManager.dragStartY = dragStartY;
        InterfaceManager.dragStartX = dragStartX;
        InterfaceManager.dragging = false;
        InterfaceManager.dragTicks = 0;
    }

    @OriginalMember(owner = "client!nk", name = "a", descriptor = "(IIIIIIIIIIII)V")
    public static void mainLogic(@OriginalArg(8) int id, @OriginalArg(11) int parentX1, @OriginalArg(3) int parentY1, @OriginalArg(10) int mouseX2, @OriginalArg(0) int parentY2, @OriginalArg(7) int scrollDeltaX, @OriginalArg(1) int scrollDeltaY, @OriginalArg(9) int mouseX1, @OriginalArg(4) int mouseY1, @OriginalArg(5) int mouseY2, @OriginalArg(6) int parentX2) {
        if (!InterfaceList.load(id)) {
            return;
        }

        if (InterfaceList.cache[id] == null) {
            logicComponentList(InterfaceList.interfaces[id], -1, parentX1, parentY1, parentX2, parentY2, scrollDeltaX, scrollDeltaY, mouseX1, mouseY1, mouseX2, mouseY2);
        } else {
            logicComponentList(InterfaceList.cache[id], -1, parentX1, parentY1, parentX2, parentY2, scrollDeltaX, scrollDeltaY, mouseX1, mouseY1, mouseX2, mouseY2);
        }
    }

    @OriginalMember(owner = "client!nw", name = "a", descriptor = "(Lclient!hda;II)Ljava/lang/String;")
    public static String getOp(@OriginalArg(0) Component component, @OriginalArg(1) int op) {
        if (serverActiveProperties(component).isOpEnabled(op) || component.onOp != null) {
            if (component.ops != null && op < component.ops.length && component.ops[op] != null && component.ops[op].trim().length() != 0) {
                return component.ops[op];
            } else {
                return testOpacity ? "Hidden-" + op : null;
            }
        } else {
            return null;
        }
    }

    @OriginalMember(owner = "client!lv", name = "a", descriptor = "(Lclient!hda;B)Ljava/lang/String;")
    public static String getComponentTargetVerb(@OriginalArg(0) Component component) {
        if (serverActiveProperties(component).getTargetMask() != 0) {
            if (component.targetVerb != null && component.targetVerb.trim().length() != 0) {
                return component.targetVerb;
            } else {
                return testOpacity ? "Hidden-use" : null;
            }
        } else {
            return null;
        }
    }

    @OriginalMember(owner = "client!fb", name = "a", descriptor = "(IIIIZ)Lclient!aha;")
    public static SubInterface openSubInterface(@OriginalArg(0) int type, @OriginalArg(1) int id, @OriginalArg(2) int idAndSlot, @OriginalArg(4) boolean fromClient) {
        @Pc(7) SubInterface sub = new SubInterface();
        sub.id = id;
        sub.type = type;
        subInterfaces.put(idAndSlot, sub);
        AvTrace.log("if open " + id + " at " + (idAndSlot >>> 16) + ":" + (idAndSlot & 0xFFFF) + " type=" + type
            + " loaded=" + InterfaceList.loaded[id] + (fromClient ? " (client script)" : ""));

        restartInterfaceAnims(id);

        @Pc(26) Component parent = InterfaceList.list(idAndSlot);
        if (parent != null) {
            redraw(parent);
        }

        if (dialog != null) {
            redraw(dialog);
            dialog = null;
        }

        MiniMenu.openButtons();

        if (parent != null) {
            calculateLayerDimensions(parent, !fromClient);
        }

        if (!fromClient) {
            ScriptRunner.executeOnLoad(id);
        }

        if (!fromClient && topLevelInterface != -1) {
            runHookImmediate(IMMEDIATE_HOOK_TYPE_SUBCHANGE, topLevelInterface);
        }

        return sub;
    }

    @OriginalMember(owner = "client!dq", name = "a", descriptor = "(II)V")
    public static void restartInterfaceAnims(@OriginalArg(0) int id) {
        if (!InterfaceList.load(id)) {
            return;
        }

        @Pc(13) Component[] children = InterfaceList.interfaces[id];
        for (@Pc(15) int i = 0; i < children.length; i++) {
            @Pc(20) Component child = children[i];

            if (child != null && child.animator != null) {
                child.animator.resetImmediately();
            }
        }
    }

    @OriginalMember(owner = "client!eja", name = "a", descriptor = "(III)V")
    public static void runHookImmediate(@OriginalArg(1) int type, @OriginalArg(2) int id) {
        if (InterfaceList.load(id)) {
            runHookImmediate(InterfaceList.interfaces[id], type);
        }
    }

    @OriginalMember(owner = "client!client", name = "a", descriptor = "([Lclient!hda;II)V")
    public static void runHookImmediate(@OriginalArg(0) Component[] children, @OriginalArg(2) int type) {
        for (@Pc(1) int i = 0; i < children.length; i++) {
            @Pc(11) Component child = children[i];
            if (child == null) {
                continue;
            }

            if (child.type == Component.TYPE_LAYER) {
                if (child.dynamicComponents != null) {
                    runHookImmediate(child.dynamicComponents, type);
                }

                @Pc(38) SubInterface sub = (SubInterface) subInterfaces.get(child.slot);
                if (sub != null) {
                    runHookImmediate(type, sub.id);
                }
            }

            @Pc(58) HookRequest hook;
            if (type == IMMEDIATE_HOOK_TYPE_DIALOGABORT && child.onDialogAbort != null) {
                hook = new HookRequest();
                hook.source = child;
                hook.arguments = child.onDialogAbort;
                ScriptRunner.executeHookInner(hook);
            }

            if (type == IMMEDIATE_HOOK_TYPE_SUBCHANGE && child.onSubChange != null) {
                if (child.id >= 0) {
                    @Pc(88) Component sub = InterfaceList.list(child.slot);

                    if (sub == null || sub.staticComponents == null || sub.staticComponents.length <= child.id || sub.staticComponents[child.id] != child) {
                        continue;
                    }
                }

                hook = new HookRequest();
                hook.source = child;
                hook.arguments = child.onSubChange;
                ScriptRunner.executeHookInner(hook);
            }
        }
    }

    @OriginalMember(owner = "client!eda", name = "a", descriptor = "(Lclient!hda;BZ)V")
    public static void calculateLayerDimensions(@OriginalArg(0) Component layer, @OriginalArg(2) boolean execScript) {
        @Pc(16) int width = layer.scrollWidth == 0 ? layer.width : layer.scrollWidth;
        @Pc(37) int height = layer.scrollHeight == 0 ? layer.height : layer.scrollHeight;

        calculateComponentListDimensions(InterfaceList.interfaces[layer.slot >> 16], layer.slot, width, height, execScript);

        if (layer.dynamicComponents != null) {
            calculateComponentListDimensions(layer.dynamicComponents, layer.slot, width, height, execScript);
        }

        @Pc(72) SubInterface sub = (SubInterface) subInterfaces.get(layer.slot);
        if (sub != null) {
            calculateComponentListDimensions(execScript, sub.id, height, width);
        }
    }

    @OriginalMember(owner = "client!al", name = "a", descriptor = "(ZIIII)V")
    public static void calculateComponentListDimensions(@OriginalArg(0) boolean execScript, @OriginalArg(1) int arg1, @OriginalArg(3) int height, @OriginalArg(4) int width) {
        if (InterfaceList.load(arg1)) {
            calculateComponentListDimensions(InterfaceList.interfaces[arg1], -1, width, height, execScript);
        }
    }

    @OriginalMember(owner = "client!gq", name = "a", descriptor = "(ZIZI[Lclient!hda;I)V")
    public static void calculateComponentListDimensions(@OriginalArg(4) Component[] children, @OriginalArg(5) int parent, @OriginalArg(1) int width, @OriginalArg(3) int height, @OriginalArg(2) boolean execScript) {
        for (@Pc(5) int i = 0; i < children.length; i++) {
            @Pc(14) Component child = children[i];

            if (child != null && child.layer == parent) {
                resize(child, width, height, execScript);
                reposition(child, width, height);

                if (child.scrollX > child.scrollWidth - child.width) {
                    child.scrollX = child.scrollWidth - child.width;
                }
                if (child.scrollX < 0) {
                    child.scrollX = 0;
                }
                if (child.scrollY > child.scrollHeight - child.height) {
                    child.scrollY = child.scrollHeight - child.height;
                }
                if (child.scrollY < 0) {
                    child.scrollY = 0;
                }

                if (child.type == Component.TYPE_LAYER) {
                    calculateLayerDimensions(child, execScript);
                }
            }
        }
    }

    @OriginalMember(owner = "client!pw", name = "a", descriptor = "(ZIIILclient!hda;)V")
    public static void resize(@OriginalArg(4) Component component, @OriginalArg(3) int parentWidth, @OriginalArg(1) int parentHeight, @OriginalArg(0) boolean execScript) {
        @Pc(6) int widthBefore = component.width;
        @Pc(16) int heightBefore = component.height;

        if (component.resizeModeX == 0) {
            component.width = component.originalWidth;
        } else if (component.resizeModeX == 1) {
            component.width = parentWidth - component.originalWidth;
        } else if (component.resizeModeX == 2) {
            component.width = (component.originalWidth * parentWidth) >> 14;
        }

        if (component.resizeModeY == 0) {
            component.height = component.originalHeight;
        } else if (component.resizeModeY == 1) {
            component.height = parentHeight - component.originalHeight;
        } else if (component.resizeModeY == 2) {
            component.height = (parentHeight * component.originalHeight) >> 14;
        }

        if (component.resizeModeX == 4) {
            component.width = (component.height * component.resizeAspectRatioY) / component.resizeAspectRatioX;
        }

        if (component.resizeModeY == 4) {
            component.height = (component.width * component.resizeAspectRatioX) / component.resizeAspectRatioY;
        }

        if (testOpacity && (serverActiveProperties(component).events != 0 || component.type == Component.TYPE_LAYER)) {
            if (component.height < 5 && component.width < 5) {
                component.height = 5;
                component.width = 5;
            } else {
                if (component.width <= 0) {
                    component.width = 5;
                }
                if (component.height <= 0) {
                    component.height = 5;
                }
            }
        }

        if (component.clientcode == ComponentClientCode.SCENE) {
            scene = component;
        }

        if (execScript && component.onResize != null && (component.width != widthBefore || component.height != heightBefore)) {
            @Pc(225) HookRequest hook = new HookRequest();
            hook.arguments = component.onResize;
            hook.source = component;
            hookRequests.addLast(hook);
        }
    }

    /** N176: the Quest Helper panel (server QuestHelperPanelImportTool: interface 1166, component 1) and its overlay spot. */
    static final int QUEST_HELPER_PANEL = (1166 << 16) | 1;
    static final String QUEST_HELPER_LAYOUT = "questhelper";

    /** N193: the resizable gameframe's modal slot and the two right-hand blocks it must stay clear of. */
    static final int RESIZABLE_MODAL = (746 << 16) | 12;
    static final int RESIZABLE_MINIMAP = (746 << 16) | 13;
    static final int RESIZABLE_SIDE_PANEL = (746 << 16) | 79;

    /**
     * Left edge of a right-anchored (x mode 2) gameframe block in a view [width] wide, from its own definition - the same
     * formula as below, so the order in which the root's children are laid out does not matter. -1 when it is not loaded.
     */
    static int rightAnchoredLeft(int slot, int width) {
        Component c = InterfaceList.list(slot);
        if (c == null || c.reposModeX != 2) {
            return -1;
        }
        return width - c.width - c.originalX;
    }

    /**
     * N193: x of the resizable modal slot in a view [width] wide whose right column starts at [minimapLeft] / [sidePanelLeft]
     * (-1 = not there): the centre of the free area when the view's centre would run into that column, else -1 (keep the
     * game's own position).
     */
    static int narrowModalX(int width, int modalWidth, int minimapLeft, int sidePanelLeft) {
        int free = minimapLeft > 0 && sidePanelLeft > 0 ? Math.min(minimapLeft, sidePanelLeft) : Math.max(minimapLeft, sidePanelLeft);
        if (free <= 0 || (width + modalWidth) / 2 <= free) {
            return -1;
        }
        return Math.max(0, (free - modalWidth) / 2);
    }

    @OriginalMember(owner = "client!or", name = "a", descriptor = "(Lclient!hda;III)V")
    public static void reposition(@OriginalArg(0) Component component, @OriginalArg(1) int width, @OriginalArg(2) int height) {
        if (component.reposModeY == 0) {
            component.y = component.originalY;
        } else if (component.reposModeY == 1) {
            component.y = component.originalY + ((height - component.height) / 2);
        } else if (component.reposModeY == 2) {
            component.y = height - component.height - component.originalY;
        } else if (component.reposModeY == 3) {
            component.y = (component.originalY * height) >> 14;
        } else if (component.reposModeY == 4) {
            component.y = ((height - component.height) / 2) + ((height * component.originalY) >> 14);
        } else {
            component.y = height - ((component.originalY * height) >> 14) - component.height;
        }

        if (component.reposModeX == 0) {
            component.x = component.originalX;
        } else if (component.reposModeX == 1) {
            component.x = component.originalX + ((width - component.width) / 2);
        } else if (component.reposModeX == 2) {
            component.x = width - component.width - component.originalX;
        } else if (component.reposModeX == 3) {
            component.x = (component.originalX * width) >> 14;
        } else if (component.reposModeX == 4) {
            component.x = ((width - component.width) / 2) + ((width * component.originalX) >> 14);
        } else {
            component.x = width - ((width * component.originalX) >> 14) - component.width;
        }

        // 78 Store window (interface 1151, component 1; owner 2026-10-11 bugs 022108 / 030753): its 120 px left offset keeps the
        // minimap off the close button in a wide view, but in a narrow one - fixed mode's 765 px full screen, Stretched Mode's
        // real sizes - it pushed the window off the left edge. Kept inside the view it belongs to.
        if (component.slot == ((1151 << 16) | 1)) {
            component.x = Math.max(0, Math.min(component.x, width - component.width));
            component.y = Math.max(0, Math.min(component.y, height - component.height));
        }

        // N193 (owner 2026-10-22: "in resizable mode the shop doesnt layout correctly in the middle of the screen it now opens a
        // little bit to the right"): the resizable modal slot (746:12 - stores, bank, every main window) is centred on the whole
        // view; when that would run into the right-hand column (minimap 746:13, side panel 746:79) the gameframe script pushes
        // it right up against the minimap instead. A narrow view (a small window, Stretched Mode's real sizes) then shows the
        // window off to the right. It is centred in the free area left of that column instead; wide views are unchanged.
        if (component.slot == RESIZABLE_MODAL) {
            int x = narrowModalX(width, component.width, rightAnchoredLeft(RESIZABLE_MINIMAP, width), rightAnchoredLeft(RESIZABLE_SIDE_PANEL, width));
            if (x >= 0) {
                component.x = x;
            }
        }

        if (testOpacity && (serverActiveProperties(component).events != 0 || component.type == Component.TYPE_LAYER)) {
            if (component.x < 0) {
                component.x = 0;
            } else if (width < component.width + component.x) {
                component.x = width - component.width;
            }

            if (component.y < 0) {
                component.y = 0;
            } else if (component.y + component.height > height) {
                component.y = height - component.height;
            }
        }
    }

    /** Set while GameframeSwitch applies a mode whose WINDOW_STATUS the server already has. */
    private static boolean windowStatusAlreadySent;

    /**
     * A2 round 6 (class 206): the player's own window-mode choice (Graphics Options, fullscreen enter/leave, dev console "wm").
     * In game a switch to the other gameframe (fixed 548 <-> resizable/fullscreen 746) waits until the server's new gameframe
     * arrives and then changes the canvas in the same frame (GameframeSwitch); everything else switches at once.
     */
    public static void requestWindowMode(int mode, int width, int height) {
        if (GameframeSwitch.host == null) {
            GameframeSwitch.host = new ClientHost();
        }
        GameframeSwitch.request(mode, width, height);
    }

    /** The client side of GameframeSwitch. */
    static final class ClientHost implements GameframeSwitch.Host {
        @Override
        public int appliedMode() {
            return getWindowMode();
        }

        @Override
        public boolean inGame() {
            return ServerConnection.GAME.connection != null && MainLogicStep.isAtGameScreen(MainLogicManager.step);
        }

        @Override
        public void apply(int mode, int width, int height, boolean sendStatus) {
            boolean before = windowStatusAlreadySent;
            windowStatusAlreadySent = !sendStatus;
            try {
                changeWindowMode(mode, width, height, false);
            } finally {
                windowStatusAlreadySent = before;
            }
        }

        @Override
        public void sendWindowStatus(int mode) {
            ServerConnectionReader.sendWindowStatus(mode);
        }

        @Override
        public void presentCanvas() {
            applyPendingCanvas();
        }

        @Override
        public boolean prepareCanvas() {
            return prepareOffscreenCanvas();
        }

        @Override
        public void presented() {
            // The offscreen first frame is on screen: from the next frame the game draws into the window again, laid out for the
            // real window size (the resize loop: Static712 -> resizeCanvas drops the offscreen framebuffer).
            if (com.jagex.StretchedMode.glPrerenderEnd()) {
                Client.changingWindowMode = true;
                GameShell.fullredraw = true;
            }
        }

        @Override
        public long now() {
            return com.jagex.core.util.SystemTimer.safetime();
        }

        @Override
        public void log(String line) {
            System.out.println(line);
        }
    }

    @OriginalMember(owner = "client!mt", name = "a", descriptor = "(IIIZI)V")
    public static void changeWindowMode(@OriginalArg(1) int mode, @OriginalArg(2) int width, @OriginalArg(4) int height, @OriginalArg(3) boolean different) {
        if (mode == WindowMode.FULLSCREEN) {
            java.awt.Rectangle desktop = com.jagex.graphics.awt.DesktopDisplay.bounds();
            width = desktop.width;
            height = desktop.height;
        }
        OrthoMode.enter();
        Client.nextWindowModeChange = 0L;

        @Pc(10) int current = getWindowMode();
        if (mode == WindowMode.FULLSCREEN || current == WindowMode.FULLSCREEN) {
            different = true;
        }
        if (!Toolkit.active.method7983()) {
            different = true;
        }

        windowModeChanged(mode, current, width, height, different);

        // RSPS: fixed mode draws a 765 x 503 canvas, so the window fits it exactly (no black borders); back to
        // resizable, the window returns to its saved resizable size.
        // Not while RuneLite Stretched Mode fills the window (owner 2026-10-11 A2 "fixed mode does not stretch at all"): every
        // resize ends here 500 ms later (client.java nextWindowModeChange), so the fit pulled a stretched fixed window back to
        // 765 x 503 at once. RuneLite (ClientUI) never sizes the frame on a mode change; a maximised window is left alone too.
        if (GameframeSwitch.deferringCanvas()) {
            // A2 round 7: a landed switch - the fit waits for the new mode's first frame (applyPendingCanvas), like the canvas.
            pendingFitMode = mode;
            pendingFitFrom = current;
        } else if (GameShell.fsframe == null && ClientOptions.instance != null && !com.jagex.StretchedMode.fillsWindow()
            && !com.jagex.graphics.awt.DesktopDisplay.maximised()) {
            if (mode == WindowMode.FIXED) {
                com.jagex.graphics.awt.DesktopDisplay.restoreWindowSize(Client.loadingScreenWidth, Client.loadingScreenHeight);
            } else if (mode == WindowMode.RESIZABLE && current == WindowMode.FIXED) {
                restoreRememberedWindow();
            }
        }
    }

    /** A2 round 7: the window part of a landed switch, waiting for the first frame of the new mode (GameframeSwitch.mayPresent). */
    private static boolean pendingCanvas;
    private static int pendingFitMode = -1;
    private static int pendingFitFrom = -1;

    /**
     * A2 round 7 (Keurmeester A2 round 6 FAIL: Stretched Mode OFF, resizable -> fixed showed a BLACK window for 0.25 s maximised and
     * ~1 s in the 944x581 window). A landed window-mode switch laid the game out at once AND resized the AWT canvas at once; a
     * resized OpenGL window is wiped, and the first frame of the new mode came only after the rest of the packet batch, the new
     * gameframe's scripts and any client freeze. With Stretched Mode on the canvas keeps the window's size, which is why that path
     * never went black. Now the canvas size / place, the toolkit surface and the client's own fixed-window fit are applied here,
     * right before the new mode's first frame is drawn (client.mainredraw -> GameframeSwitch.mayPresent), so the window shows the
     * old mode's last picture until the new picture replaces it - the OSRS / RuneLite client never resizes its canvas on a mode
     * switch either (fixed is drawn inside the same canvas).
     */
    static void applyPendingCanvas() {
        int fitMode = pendingFitMode;
        int fitFrom = pendingFitFrom;
        pendingFitMode = -1;
        pendingFitFrom = -1;
        // A2 round 7 part 2: the first frame of the new mode is drawn offscreen already (prepareOffscreenCanvas) - the window and
        // canvas take the new size now, and the flip right after this shows that finished frame.
        boolean offscreen = com.jagex.StretchedMode.glPrerendering();
        java.awt.Dimension before = offscreen ? com.jagex.graphics.awt.DesktopDisplay.gameArea() : null;
        java.awt.Dimension expected = offscreen ? targetGameArea(fitMode, fitFrom) : null;
        if (fitMode != -1 && fitsWindow()) {
            if (fitMode == WindowMode.FIXED) {
                com.jagex.graphics.awt.DesktopDisplay.restoreWindowSize(Client.loadingScreenWidth, Client.loadingScreenHeight);
            } else if (fitMode == WindowMode.RESIZABLE && fitFrom == WindowMode.FIXED) {
                restoreRememberedWindow();
            }
        }
        if (offscreen) {
            // The fit runs on the event thread (and a maximise reaches the frame a moment later): the canvas follows the frame.
            com.jagex.graphics.awt.DesktopDisplay.awaitWindow(before, expected, 400L);
            pendingCanvas = true;
        }
        if (!pendingCanvas) {
            return;
        }
        pendingCanvas = false;
        if (GameShell.canvas == null || Toolkit.active == null) {
            return;
        }
        // The frame may have changed size just now (the fit): the resize loop lays the current mode out in it, sizes and places the
        // canvas and resizes the toolkit surface (Static712 = what every window resize runs).
        Static712.method9329((byte) 11);
        GameShell.fullredraw = true;
    }

    /** The client's own fixed-window fit / remembered-window restore applies (not stretched, not maximised, not fullscreen). */
    private static boolean fitsWindow() {
        return GameShell.fsframe == null && ClientOptions.instance != null && !com.jagex.StretchedMode.fillsWindow()
            && !com.jagex.graphics.awt.DesktopDisplay.maximised();
    }

    /** The game area the window will have once the pending fit of a landed switch ran (null without a window). */
    private static java.awt.Dimension targetGameArea(int fitMode, int fitFrom) {
        if (fitMode != -1 && fitsWindow()) {
            if (fitMode == WindowMode.FIXED) {
                return com.jagex.graphics.awt.DesktopDisplay.fittedGameArea(Client.loadingScreenWidth, Client.loadingScreenHeight);
            }
            if (fitMode == WindowMode.RESIZABLE && fitFrom == WindowMode.FIXED) {
                ClientOptions options = ClientOptions.instance;
                return com.jagex.graphics.awt.DesktopDisplay.restoredGameArea(Client.loadingScreenWidth, Client.loadingScreenHeight,
                    options.windowMaximised, options.windowWidth, options.windowHeight);
            }
        }
        return com.jagex.graphics.awt.DesktopDisplay.gameArea();
    }

    /**
     * A2 round 7 part 2 (nakijker A2 round 7 FOUTEN 1: Stretched OFF, fixed -> the remembered MAXIMISED resizable window was black
     * for the whole first draw, 1468 ms): the hold of a landed switch ends - with OpenGL (framebuffer objects) and Stretched Mode
     * off the new mode is laid out for the window it is about to have and its first frame is drawn into the offscreen framebuffer,
     * while the window and canvas keep the old picture; GameframeSwitch.beforeFlip then fits the window, resizes the canvas and the
     * flip blits that finished frame (the RuneLite GPU plugin: frames are drawn offscreen and only finished ones are shown). True =
     * drawn offscreen; false = nothing changes size, or the toolkit draws straight into the canvas (the round-7 order).
     */
    static boolean prepareOffscreenCanvas() {
        if ((!pendingCanvas && pendingFitMode == -1) || !(Toolkit.active instanceof GlToolkit) || OrthoMode.toolkitActive
            || GameShell.fsframe != null || GameShell.canvas == null || com.jagex.StretchedMode.active()
            || com.jagex.StretchedMode.windowCanvas() // A2 round 8: the canvas fills the window - nothing changes size
            || !((GlToolkit) Toolkit.active).rspsStretchSupported()) {
            return false;
        }
        java.awt.Dimension target = targetGameArea(pendingFitMode, pendingFitFrom);
        if (target == null || target.width <= 0 || target.height <= 0) {
            return false;
        }
        // The layout the resize loop (Static712) gives that window, without touching the frame or the AWT canvas yet.
        GameShell.frameWid = target.width;
        GameShell.frameHei = target.height;
        if (getWindowMode() == WindowMode.FIXED) {
            GameShell.topMargin = 0;
            GameShell.leftMargin = Math.max(0, (GameShell.frameWid - Client.loadingScreenWidth) / 2);
            GameShell.canvasHei = Client.loadingScreenHeight;
            GameShell.canvasWid = Client.loadingScreenWidth;
            com.jagex.StretchedMode.layout(true);
        } else {
            applyMaxScreenSize();
            com.jagex.StretchedMode.layout(false);
        }
        if (!com.jagex.StretchedMode.glPrerender(GameShell.canvasWid, GameShell.canvasHei)) {
            return false;
        }
        // The toolkit's surface takes that size; the stretch framebuffer is bound as its window (StretchedMode.glSync).
        ((GlToolkit) Toolkit.active).method7013();
        if (!com.jagex.StretchedMode.glOwnsFrame()) {
            com.jagex.StretchedMode.glPrerenderEnd();
            ((GlToolkit) Toolkit.active).method7013();
            return false;
        }
        if (topLevelInterface != -1) {
            refreshTopLevelInterface(true);
        }
        for (int i = 0; i < 100; i++) {
            dirtyRectangles[i] = true;
        }
        redrawAll();
        GameShell.fullredraw = true;
        System.out.println("[78] window mode " + getWindowMode() + ": first frame drawn offscreen at " + GameShell.canvasWid + "x" + GameShell.canvasHei
            + " for a " + target.width + "x" + target.height + " window (the window keeps the old picture until it is drawn)");
        return true;
    }

    /**
     * RuneLite ClientUI rememberScreenBounds (owner 2026-10-11, Stretched Mode "net als de officiele versie"): the window size
     * and maximised state the player last had come back. Only for a window this client itself fitted to the game (a fixed
     * window without stretching); a window the player sized is never touched. RuneLite itself never shrinks the window, so
     * this undoes only our own fixed-mode fit - when going back to resizable, and when Stretched Mode starts filling a fixed
     * window (switched on, or the account's saved setting arriving at login).
     */
    /**
     * RuneLite ClientUI setLastNormalBounds / saveClientBoundsConfig: the player's own window is kept - its game area while
     * it is not maximised, and whether it is maximised. Not while this client itself fits a fixed window to the unstretched
     * game (that size is not the player's) and not in fullscreen.
     */
    static void rememberWindowBounds() {
        ClientOptions options = ClientOptions.instance;
        java.awt.Dimension game = com.jagex.graphics.awt.DesktopDisplay.gameArea();
        // Not before the start-up has put the remembered window back (RuneLite restores the bounds before it listens): the
        // loading screen's default 978x709 window overwrote the player's size (tested 2026-10-11).
        if (options == null || game == null || GameShell.fsframe != null || !windowRestored) {
            return;
        }
        if (getWindowMode() == WindowMode.FIXED && !com.jagex.StretchedMode.fillsWindow()) {
            return;
        }
        boolean maximised = com.jagex.graphics.awt.DesktopDisplay.maximised();
        boolean changed = maximised != options.windowMaximised;
        options.windowMaximised = maximised;
        if (!maximised && game.width >= 765 && game.height >= 503 && (options.windowWidth != game.width || options.windowHeight != game.height)) {
            options.windowWidth = game.width;
            options.windowHeight = game.height;
            changed = true;
        }
        if (changed) {
            System.out.println("[78] window remembered: " + (maximised ? "maximised, " : "") + options.windowWidth + "x" + options.windowHeight);
            ClientOptions.save();
        }
    }

    /** Set once the start-up has applied the remembered window mode and window (Loading). */
    static boolean windowRestored;

    static void restoreRememberedWindow() {
        restoreRememberedWindow(false);
    }

    /** [atStart]: the client's own start-up window (RuneLite restores the bounds at every start), else only a fitted fixed window. */
    static void restoreRememberedWindow(boolean atStart) {
        ClientOptions options = ClientOptions.instance;
        if (options == null || GameShell.fsframe != null) {
            return;
        }
        com.jagex.graphics.awt.DesktopDisplay.restoreIfGameSized(atStart, Client.loadingScreenWidth, Client.loadingScreenHeight,
            options.windowMaximised, options.windowWidth, options.windowHeight);
    }

    /**
     * Lays the game out again inside the full-screen frame (client loop, Client.changingWindowMode while in fullscreen): a
     * Stretched Mode setting or the sidebar opening changed the layout. The client's own resize check only looks at a windowed
     * frame, so before this every such change waited until fullscreen was left (night run 2026-10-12: Resizable scaling 100 %
     * in fullscreen left the game at 1280x720). Same frame and screen size: windowModeChanged neither leaves nor re-enters
     * fullscreen and resizes the canvas like a windowed resize.
     */
    public static void relayoutFullscreen() {
        if (GameShell.fsframe == null || Toolkit.active == null) {
            return;
        }
        windowModeChanged(WindowMode.FULLSCREEN, WindowMode.FULLSCREEN, GameShell.lastFullscreenWidth, GameShell.lastFullscreenHeight,
            !Toolkit.active.method7983());
    }

    @OriginalMember(owner = "client!li", name = "a", descriptor = "(IIIIIZ)V")
    public static void windowModeChanged(@OriginalArg(2) int newMode, @OriginalArg(0) int oldMode, @OriginalArg(4) int width, @OriginalArg(1) int height, @OriginalArg(5) boolean different) {
        if (GameShell.fsframe != null && (newMode != WindowMode.FULLSCREEN || width != GameShell.lastFullscreenWidth || height != GameShell.lastFullscreenHeight)) {
            exitFullscreen(GameShell.signLink, GameShell.fsframe);
            GameShell.fsframe = null;
        }

        if (newMode == WindowMode.FULLSCREEN && GameShell.fsframe == null) {
            GameShell.fsframe = createFullscreenFrame(GameShell.signLink, width, height, 0, 0);

            if (GameShell.fsframe != null) {
                GameShell.lastFullscreenWidth = width;
                GameShell.lastFullscreenHeight = height;
                ClientOptions.save();
                // RuneLite keeps its sidebar on screen in fullscreen: it moves into the full-screen frame (RspsSidebar).
                RspsSidebar.enterFullscreen(GameShell.fsframe);
            }
        }

        if (newMode == WindowMode.FULLSCREEN && GameShell.fsframe == null) {
            windowModeChanged(ClientOptions.instance.screenSizeDefault.getValue() == WindowMode.FULLSCREEN ? WindowMode.RESIZABLE : ClientOptions.instance.screenSizeDefault.getValue(), oldMode, -1, -1, true);
            return;
        }

        @Pc(95) Container topContainer;
        if (GameShell.fsframe != null) {
            GameShell.frameHei = GameShell.fsframe.getHeight();
            // The 78 Plugins sidebar takes the right edge of the full-screen frame; the game gets the rest.
            GameShell.frameWid = GameShell.fsframe.getWidth() - RspsSidebar.fullscreenWidth();
            topContainer = GameShell.fsframe;
        } else if (GameShell.frame != null) {
            @Pc(110) Insets insets = GameShell.frame.getInsets();
            GameShell.frameWid = GameShell.frame.getSize().width - insets.right - insets.left;

            @Pc(126) int negativeTop = -insets.top;
            GameShell.frameHei = GameShell.frame.getSize().height + negativeTop - insets.bottom;

            topContainer = GameShell.frame;
        } else {
            if (GameShell.loaderApplet == null) {
                topContainer = GameShell.instance;
            } else {
                topContainer = GameShell.loaderApplet;
            }

            GameShell.frameWid = topContainer.getSize().width;
            GameShell.frameHei = topContainer.getSize().height;
        }

        if (newMode == WindowMode.FIXED) {
            GameShell.topMargin = 0;
            // Never negative (like the resizable branch): a window narrower than the fixed frame must not push its left side off screen.
            GameShell.leftMargin = Math.max(0, (GameShell.frameWid - Client.loadingScreenWidth) / 2);
            GameShell.canvasHei = Client.loadingScreenHeight;
            GameShell.canvasWid = Client.loadingScreenWidth;
        } else {
            applyMaxScreenSize();
        }
        // RuneLite Stretched Mode: the game keeps its real size, the canvas is drawn stretched over the window.
        com.jagex.StretchedMode.layout(newMode == WindowMode.FIXED);

        if (Client.modeWhere != ModeWhere.LIVE) {
            @Pc(178) boolean tooSmall;
            if (GameShell.canvasWid < 1024 && GameShell.canvasHei < 768) {
                tooSmall = true;
            } else {
                tooSmall = false;
            }
        }

        if (different) {
            Static574.method7572();
        } else if (GameframeSwitch.deferringCanvas()) {
            // A2 round 7: a landed switch - the canvas keeps the old picture until the new mode's first frame (applyPendingCanvas).
            pendingCanvas = true;
        } else {
            GameShell.canvas.setSize(com.jagex.StretchedMode.canvasWidth(), com.jagex.StretchedMode.canvasHeight());

            if (OrthoMode.toolkitActive) {
                OrthoMode.method7606(GameShell.canvas);
            } else {
                Toolkit.active.resizeCanvas(GameShell.canvas, GameShell.canvasWid, GameShell.canvasHei);
            }

            if (topContainer == GameShell.frame) {
                @Pc(110) Insets local110 = GameShell.frame.getInsets();
                GameShell.canvas.setLocation(GameShell.leftMargin + local110.left, GameShell.topMargin + local110.top);
            } else {
                GameShell.canvas.setLocation(GameShell.leftMargin, GameShell.topMargin);
            }
        }

        if (newMode >= WindowMode.RESIZABLE) {
            resizableScreen = true;
        } else {
            resizableScreen = false;
        }

        if (topLevelInterface != -1) {
            refreshTopLevelInterface(true);
        }

        // A2 round 6: a switch drawn together with its gameframe already sent its WINDOW_STATUS when the player chose it.
        if (ServerConnection.GAME.connection != null && MainLogicStep.isAtGameScreen(MainLogicManager.step) && !windowStatusAlreadySent) {
            ServerConnectionReader.sendWindowStatus();
        }

        for (@Pc(258) int i = 0; i < 100; i++) {
            dirtyRectangles[i] = true;
        }

        GameShell.fullredraw = true;
    }

    @OriginalMember(owner = "client!uda", name = "a", descriptor = "(IZ)V")
    public static void refreshTopLevelInterface(@OriginalArg(1) boolean id) {
        calculateComponentListDimensions(id, topLevelInterface, GameShell.canvasHei, GameShell.canvasWid);
    }

    @OriginalMember(owner = "client!nea", name = "a", descriptor = "(ILclient!hda;II)V")
    public static void addMiniMenuOptions(@OriginalArg(1) Component component, @OriginalArg(2) int arg1, @OriginalArg(3) int arg2) {
        // Diagnostic logging for gear switcher - log component data when menu opens
        RspsGearSwitcher.observeMenuComponent(component);
        // 78Legacy v4: right-click setup "Add Wear / Prayer ... to Script N" (RspsScriptMenu; client only).
        RspsScriptMenu.addComponentOptions(component);
        // N72: OSRS spell filters - "Enable/Disable spell filtering" on the magic tab stone, the Filters button, the filter menu.
        RspsSpellFilters.addOptions(component);

        if (targetMode) {
            @Pc(16) ParamType param = targetParam == -1 ? null : ParamTypeList.instance.list(targetParam);

            if (serverActiveProperties(component).isUseTarget() && (targetMask & TargetMask.TGT_BUTTON) != 0 && (param == null || component.param(param.defaultint, targetParam) != param.defaultint)) {
                MiniMenu.addEntryInner(false, component.invObject, 0L, component.id, component.slot, targetVerb, MiniMenuAction.IF_BUTTONT, true, targetEnterCursor, targetedVerb + " -> " + component.opBase, (component.id << 0) | component.slot, false);
            }
        }

        for (@Pc(97) int i = 9; i >= 5; i--) {
            @Pc(106) String op = getOp(component, i);

            if (op != null) {
                MiniMenu.addEntryInner(false, component.invObject, i + 1, component.id, component.slot, op, MiniMenuAction.IF_BUTTONX2, true, opCursor(i, component), component.opBase, (component.id << 0) | component.slot, false);
            }
        }

        @Pc(106) String targetVerb = getComponentTargetVerb(component);
        if (targetVerb != null) {
            MiniMenu.addEntryInner(false, component.invObject, 0L, component.id, component.slot, targetVerb, MiniMenuAction.TGT_BUTTON, true, component.targetOpCursor, component.opBase, (component.id << 0) | component.slot, false);
        }

        for (@Pc(193) int i = 4; i >= 0; i--) {
            @Pc(204) String op = getOp(component, i);

            if (op != null) {
                MiniMenu.addEntryInner(false, component.invObject, i + 1, component.id, component.slot, op, MiniMenuAction.IF_BUTTONX1, true, opCursor(i, component), component.opBase, (component.id << 0) | component.slot, false);
            }
        }

        if (serverActiveProperties(component).isPauseButton()) {
            if (component.pauseText == null) {
                MiniMenu.addEntryInner(false, component.invObject, 0L, component.id, component.slot, LocalisedText.CONTINUE.localise(Client.language), MiniMenuAction.PAUSE_BUTTON, true, -1, "", (component.id << 0) | component.slot, false);
            } else {
                MiniMenu.addEntryInner(false, component.invObject, 0L, component.id, component.slot, component.pauseText, MiniMenuAction.PAUSE_BUTTON, true, -1, "", (component.id << 0) | component.slot, false);
            }
        }
    }

    @OriginalMember(owner = "client!ln", name = "a", descriptor = "(ILclient!hda;I)I")
    public static int opCursor(@OriginalArg(0) int op, @OriginalArg(1) Component component) {
        if (!serverActiveProperties(component).isOpEnabled(op) && component.onOp == null) {
            return -1;
        } else if (component.opCursors == null || op >= component.opCursors.length) {
            return -1;
        } else {
            return component.opCursors[op];
        }
    }

    @OriginalMember(owner = "client!oh", name = "a", descriptor = "(IZ)V")
    public static void openLogin(@OriginalArg(1) boolean unloaded) {
        if (unloaded) {
            if (topLevelInterface != -1) {
                discard(topLevelInterface);
            }

            for (@Pc(21) SubInterface sub = (SubInterface) subInterfaces.first(); sub != null; sub = (SubInterface) subInterfaces.next()) {
                if (!sub.isLinked()) {
                    sub = (SubInterface) subInterfaces.first();

                    if (sub == null) {
                        break;
                    }
                }

                closeSubInterface(sub, true, false);
            }

            topLevelInterface = -1;
            subInterfaces = new IterableHashTable(8);
            InterfaceList.reset();
            topLevelInterface = GraphicsDefaults.instance.login_interface;
            refreshTopLevelInterface(false);
            redrawAll();
            ScriptRunner.executeOnLoad(topLevelInterface);
        }

        Static300.method4389();
        lobbyOpened = false;
        MiniMenu.setCancelEntry();
        targetEndCursor = -1;
        Static115.method2136(Cursor.dflt);
        PlayerEntity.self = new PlayerEntity();
        PlayerEntity.self.z = Static501.mapLength * 512 / 2;
        PlayerEntity.self.x = Static720.mapWidth * 512 / 2;
        PlayerEntity.self.pathX[0] = Static720.mapWidth / 2;
        Camera.z = 0;
        Camera.x = 0;
        PlayerEntity.self.pathZ[0] = Static501.mapLength / 2;

        if (Camera.mode == CameraMode.MODE_FOLLOWPLAYER) {
            Camera.z = Camera.moveToZ << 9;
            Camera.x = Camera.moveToX << 9;
        } else {
            Camera.splineTick();
        }

        loginOpened();
    }

    @OriginalMember(owner = "client!go", name = "a", descriptor = "(I)V")
    public static void loginOpened() {
        loginOpened = true;
    }

    @OriginalMember(owner = "client!as", name = "a", descriptor = "(ZZ)V")
    public static void openLobby(@OriginalArg(0) boolean unloaded) {
        if (unloaded) {
            if (topLevelInterface != -1) {
                discard(topLevelInterface);
            }

            for (@Pc(16) SubInterface sub = (SubInterface) subInterfaces.first(); sub != null; sub = (SubInterface) subInterfaces.next()) {
                if (!sub.isLinked()) {
                    sub = (SubInterface) subInterfaces.first();

                    if (sub == null) {
                        break;
                    }
                }

                closeSubInterface(sub, true, false);
            }

            topLevelInterface = -1;
            subInterfaces = new IterableHashTable(8);
            InterfaceList.reset();
            topLevelInterface = GraphicsDefaults.instance.lobby_interface;
            refreshTopLevelInterface(false);
            redrawAll();
            ScriptRunner.executeOnLoad(topLevelInterface);
        }

        lobbyOpened = true;
    }

    @OriginalMember(owner = "client!ku", name = "a", descriptor = "(IZ)V")
    public static void discard(@OriginalArg(0) int id) {
        if (id != -1 && InterfaceList.loaded[id]) {
            Component.interfacesJs5.discardUnpacked(id);
            InterfaceList.interfaces[id] = null;
            InterfaceList.cache[id] = null;
            InterfaceList.loaded[id] = false;
        }
    }

    @OriginalMember(owner = "client!od", name = "a", descriptor = "(BZZLclient!aha;)V")
    public static void closeSubInterface(@OriginalArg(3) SubInterface sub, @OriginalArg(2) boolean discard, @OriginalArg(1) boolean fromClient) {
        @Pc(6) int subId = sub.id;
        @Pc(10) int idAndSlot = (int) sub.key;
        sub.unlink();
        AvTrace.log("if close " + subId + " at " + (idAndSlot >>> 16) + ":" + (idAndSlot & 0xFFFF) + " type=" + sub.type
            + (fromClient ? " (client)" : ""));

        if (discard) {
            discard(subId);
        }

        resetServerActiveProperties(subId);

        @Pc(27) Component inter = InterfaceList.list(idAndSlot);
        if (inter != null) {
            redraw(inter);
        }

        MiniMenu.openButtons();

        if (!fromClient && topLevelInterface != -1) {
            runHookImmediate(IMMEDIATE_HOOK_TYPE_SUBCHANGE, topLevelInterface);
        }

        @Pc(55) HashTableIterator iterator = new HashTableIterator(subInterfaces);
        for (@Pc(60) SubInterface child = (SubInterface) iterator.first(); child != null; child = (SubInterface) iterator.next()) {
            if (!child.isLinked()) {
                child = (SubInterface) iterator.first();

                if (child == null) {
                    break;
                }
            }

            if (child.type == SubInterfaceType.OVERLAY_CLIENT) {
                @Pc(84) int key = (int) child.key;

                if (subId == key >>> 16) {
                    closeSubInterface(child, true, fromClient);
                }
            }
        }
    }

    private InterfaceManager() {
        /* empty */
    }

    @OriginalMember(owner = "client!ci", name = "a", descriptor = "(IZ)V")
    public static void close() {
        @Pc(13) ClientMessage message = ClientMessage.create(ClientProt.CLOSE_MODAL, ServerConnection.GAME.isaac);
        ServerConnection.GAME.send(message);

        for (@Pc(22) SubInterface sub = (SubInterface) subInterfaces.first(); sub != null; sub = (SubInterface) subInterfaces.next()) {
            if (!sub.isLinked()) {
                sub = (SubInterface) subInterfaces.first();

                if (sub == null) {
                    break;
                }
            }

            if (sub.type == 0) {
                closeSubInterface(sub, true, true);
            }
        }

        if (dialog != null) {
            redraw(dialog);
            dialog = null;
        }
    }

    @OriginalMember(owner = "client!un", name = "a", descriptor = "(Lclient!vq;ILjava/awt/Frame;)V")
    public static void exitFullscreen(@OriginalArg(0) SignLink signLink, @OriginalArg(2) Frame frame) {
        // Back into the main frame before the full-screen frame goes (posted ahead of the exit, which waits for the event thread).
        RspsSidebar.leaveFullscreen();
        SignedResource resource = signLink.fullscreenExit(frame);
        while (resource.status == SignedResourceStatus.IDLE) TimeUtils.sleep(10L);
        // Never trap the game thread in a retry loop on desktop/driver failure.
        frame.setVisible(false);
        frame.dispose();
        if (resource.status != SignedResourceStatus.SUCCESS) {
            System.err.println("Fullscreen cleanup failed; the window was disposed.");
        }
    }

    @OriginalMember(owner = "client!ph", name = "a", descriptor = "(ILclient!vq;IIII)Ljava/awt/Frame;")
    public static Frame createFullscreenFrame(@OriginalArg(1) SignLink signlink, @OriginalArg(4) int width, @OriginalArg(3) int height, @OriginalArg(0) int oldWidth, @OriginalArg(2) int oldHeight) {
        if (!signlink.supportsFullscreen()) {
            return null;
        }

        @Pc(18) FullscreenMode[] properties = SignLink.fullscreenModes(signlink);
        if (properties == null) {
            return null;
        }

        @Pc(25) boolean found = false;
        for (@Pc(27) int i = 0; i < properties.length; i++) {
            if (properties[i].width == width && properties[i].height == height && (oldHeight == 0 || oldHeight == properties[i].refreshrate) && (!found || properties[i].bits > oldWidth)) {
                found = true;
                oldWidth = properties[i].bits;
            }
        }

        if (!found) {
            return null;
        }

        @Pc(101) SignedResource resource = signlink.fullscreenEnter(width, height, oldWidth, oldHeight);
        while (resource.status == SignedResourceStatus.IDLE) {
            TimeUtils.sleep(10L);
        }

        @Pc(112) Frame frame = (Frame) resource.result;
        if (frame == null) {
            return null;
        } else if (resource.status == SignedResourceStatus.ERROR) {
            exitFullscreen(signlink, frame);
            return null;
        } else {
            return frame;
        }
    }

    @OriginalMember(owner = "client!kda", name = "a", descriptor = "(I)V")
    public static void applyMaxScreenSize() {
        // RSPS owner 2026-09-24: resizable always fills the whole window. The 2011 max-screen-size cap (forced to
        // 800x600 on low detail) left black borders around the game.
        int maximum = MaxScreenSize.ANY;
        int[] size = DisplaySizing.canvas(GameShell.frameWid, GameShell.frameHei, maximum, GameShell.fsframe != null);
        GameShell.canvasWid = size[0];
        GameShell.canvasHei = size[1];
        GameShell.leftMargin = Math.max(0, (GameShell.frameWid - size[0]) / 2);
        GameShell.topMargin = 0;
    }

    @OriginalMember(owner = "client!nfa", name = "a", descriptor = "(IIIII)V")
    public static void flipDirtyRectWithin(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int width, @OriginalArg(3) int height) {
        for (@Pc(5) int i = 0; i < rectangleCount; i++) {
            @Pc(10) Rectangle rectangle = rectangles[i];

            if (rectangle.x + rectangle.width > x && rectangle.x < x + width && rectangle.y + rectangle.height > y && rectangle.y < y + height) {
                flipDirtyRect[i] = true;
            }
        }
        OrthoMode.method8927(x, x + width, y, height + y);
    }

    @OriginalMember(owner = "client!rj", name = "a", descriptor = "(BLclient!hda;)Lclient!hda;")
    public static Component getParentLayer(@OriginalArg(1) Component component) {
        if (component.layer != -1) {
            return InterfaceList.list(component.layer);
        }

        @Pc(25) int parent = component.slot >>> 16;
        @Pc(30) HashTableIterator iterator = new HashTableIterator(subInterfaces);

        for (@Pc(35) SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id == parent) {
                return InterfaceList.list((int) sub.key);
            }
        }

        return null;
    }

    @OriginalMember(owner = "client!bia", name = "a", descriptor = "(ILclient!hda;)V")
    public static void resizeAndReposition(@OriginalArg(1) Component component) {
        @Pc(7) Component parent = getParentLayer(component);

        @Pc(24) int width;
        @Pc(27) int height;
        if (parent == null) {
            height = GameShell.canvasHei;
            width = GameShell.canvasWid;
        } else {
            width = parent.width;
            height = parent.height;
        }

        resize(component, width, height, false);
        reposition(component, width, height);
    }
}
