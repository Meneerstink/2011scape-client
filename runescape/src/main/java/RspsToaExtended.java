import com.jagex.core.util.TimeUtils;
import com.jagex.game.runetek6.config.npctype.NPCType;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * N610 (owner 13-11 'we willen toa extended plugin van runelite volledig geen namaak maar exact'): RuneLite plugin "Toa Extended"
 * (github Blunkie/runelite-toaextended a55d7aa3, ca.plugins.toaextended, BSD 2-Clause, rdutta / LlemonDuck / TheStonedTurtle /
 * Ron Young). Settings: RspsHudConfig plugin "toaextended" (the 114 ConfigItems of ToaExtendedConfig).
 *
 * Built per room in the plugin's order. Part 1 = Zebak (boss/zebak/Zebak.java + ZebakSceneOverlay.java):
 *  - zebakProjectileTiles: the target tile of every rock / jug / venom projectile while it flies (danger outline + fill);
 *  - zebakWaveTile / zebakBloodCloudTile: Tile (smooth position) or True Tile (server tile) under every wave / blood cloud;
 *  - zebakHealthCounter: hp until Zebak's next breakpoint (85/70/55/40 % of the boss HP HUD maximum) over his head;
 *  - zebakHideWaves: waves are not drawn (RuneLite RenderableDrawListener -> Static102.method2026).
 * Ids: the plugin reads OSRS ids; we use the local copies from data/cfg/toa/ids.tsv (OSRS id in each comment).
 * Not on our server (simplified ToA, owner 11-06 'A': no invocations): blood magic (gfx 377) and blood clouds ('Not Just a Head'),
 * venom splash 1555 - their settings exist exactly, they just never find anything to mark.
 * Still to build (dossier N610): model outline (jug cyan / boulder green / blood magic), the prayer overlay (prayerMode).
 */
final class RspsToaExtended {
    static final String ID = "toaextended";

    // boss/zebak/Zebak.java - NpcID
    private static final String ZEBAK_NAME = "Zebak";
    private static final int NPC_ZEBAK_DEAD = 15218; // ZEBAK_11733
    private static final int NPC_WAVE = 15166; // WAVE 11738
    private static final int NPC_BLOOD_CLOUD = 15170; // BLOOD_CLOUD 11742
    private static final int NPC_BLOOD_CLOUD_2 = 15171; // BLOOD_CLOUD_11743
    // PROJECTILE_ID_ROCK 2172 / _JUG 2173 / _VENOM 2194 (spotanims); _VENOM_SPLASH 1555 is not used by our Zebak
    private static final int PROJ_ROCK = 3348;
    private static final int PROJ_JUG = 3349;
    private static final int PROJ_VENOM = 3244;
    private static final double[] ZEBAK_HP_PERCENT_BREAKPOINTS = {0.85, 0.70, 0.55, 0.40};

    // boss/baba/Baba.java - NpcID / projectile / graphics object ids
    private static final String BABA_NAME = "Ba-Ba";
    private static final int NPC_BOULDER = 15180; // BOULDER_11782 (not weakened; 11783 = weakened is not marked)
    private static final int NPC_RUBBLE = 15182; // RUBBLE_11784
    private static final int PROJ_BABA_BOULDER = 3324; // PROJECTILE_ID_BOULDER 2245 TOA_BABA_BALLS_TRAVEL
    private static final int PROJ_BABOON_ATTACK = 3322; // PROJECTILE_ID_BABOON_ATTACK 2243 TOA_BABOON_RANGED_TRAVEL_2
    private static final int PROJ_SARCOPHAGUS = 3325; // PROJECTILE_ID_SARCOPHAGUS_ATTACK 2246 TOA_BABA_SKULL_PROJ
    private static final int GFX_FALLING_BOULDER = 3326; // 2250 TOA_BABA_ROCK_FALL
    private static final int GFX_FALLING_BOULDER_2 = 3327; // 2251 TOA_BABA_ROCK_FALL_FASTER
    private static final int GFX_SHOCKWAVE_SMALL = 3394; // GRAPHICS_OBJECT_ID_SHOCKWAVE_SMALL 1447 GARGBOSS_DEBRIS_SHADOW_120
    private static final int GFX_SHOCKWAVE_LARGE = -1; // 1448 GARGBOSS_DEBRIS_SHADOW_150: not imported (our Ba-Ba sends neither yet)
    private static final int FALLING_BOULDER_TICK_COUNT = 7;
    private static final double[] BABA_HP_PERCENT_BREAKPOINTS = {0.66, 0.33};
    private static final int CYCLES_PER_TICK = 30; // Constants.GAME_TICK_LENGTH 600 / CLIENT_TICK_LENGTH 20

    // boss/kephri/Kephri.java + KephriSceneOverlay.java
    private static final String KEPHRI_NAME = "Kephri";
    private static final int NPC_KEPHRI_DEAD = 15217; // KEPHRI_11722 (onNpcChanged -> npc = null)
    private static final int NPC_EGG = 15161; // EGG_11728
    private static final int NPC_SCARAB_SWARM = 15124; // SCARAB_SWARM_11723
    private static final int NPC_AGILE_SCARAB = 15123; // AGILE_SCARAB 11727
    private static final int SEQ_FIREBALL_ATTACK = 16645; // ANIM_ID_FIREBALL_ATTACK 9577
    private static final int SEQ_SPECIAL_ATTACK = 16646; // ANIM_ID_SPECIAL_ATTACK 9578
    private static final int SEQ_INACTIVE = 16647; // ANIM_ID_INACTIVE 9579
    private static final int SEQ_SCARAB_SWARM_DEATH = 16718; // ANIM_ID_SCARAB_SWARM_DEATH 9607
    private static final int PROJ_AGILE_SCARAB = 3239; // PROJECTILE_ID_AGILE_SCARAB 2152
    private static final int PROJ_PRE_FIREBALL = 3392; // PROJECTILE_ID_PRE_FIREBALL 1481
    private static final int PROJ_FIREBALL = 3393; // PROJECTILE_ID_FIREBALL 2266
    private static final int PROJ_BOMBER_SCARAB = 3342; // PROJECTILE_ID_BOMBER_SCARAB 2147
    private static final int ATK_COUNT_INIT = 3;
    private static final int ATK_COUNT_MAX = 5;
    private static final int TICK_COUNT_EGG = 15;
    /** KephriSceneOverlay.eggRadius: path level >= 4 -> 5, else 3 (our ToA has no path levels: always 3). */
    private static final int EGG_RADIUS = 3;

    // boss/akkha/Akkha.java + AkkhaFinalStand.java + AkkhaSceneOverlay.java
    private static final String AKKHA_NAME = "Akkha";
    private static final int SEQ_AKKHA_MELEE = 16336; // ANIM_ID_MELEE 9770
    private static final int SEQ_AKKHA_MISSILES = 16338; // ANIM_ID_MISSILES 9772
    private static final int SEQ_AKKHA_MAGIC = 16340; // ANIM_ID_MAGIC 9774
    private static final int AKKHA_ATTACK_TICK_COUNT = 6;
    private static final int NPC_UNSTABLE_ORB = 15191; // UNSTABLE_ORB 11804
    private static final double[] AKKHA_HP_PERCENT_BREAKPOINTS = {0.8, 0.6, 0.4, 0.2};
    /** Akkha.AttackStyle colours: MELEE red, RANGE green, MAGE blue (AKKHA_11795 white: no local form, never shown). */
    private static final int STYLE_MELEE = 0xFFFF0000;
    private static final int STYLE_RANGE = 0xFF00FF00;
    private static final int STYLE_MAGE = 0xFF0000FF;

    // boss/warden/phase2/WardenP2.java + WardenP2SceneOverlay.java
    private static final int NPC_ELIDINIS_P2 = 15105; // ELIDINIS_WARDEN_11753 (11754 not imported)
    private static final int NPC_TUMEKEN_P2 = 15129; // TUMEKENS_WARDEN_11756 (11757 not imported)
    private static final int NPC_CORE = 15136; // CORE 11770
    private static final int NPC_CORE_2 = 15198; // CORE_11771
    private static final int NPC_ELIDINIS_P3 = 15134; // ELIDINIS_WARDEN_11761 (phase 3: not phase 2)
    private static final int NPC_TUMEKEN_P3 = 15135; // TUMEKENS_WARDEN_11762
    /** SlamDirection RIGHT(9674, 9675) / LEFT(9676, 9677) / MIDDLE(9678, 9679): our baked seqs of 11761 (17015-17020) and 11762 (17053-17058). */
    private static final int SEQ_SLAM_ELIDINIS = 17015, SEQ_SLAM_TUMEKEN = 17053;
    private static final int SEQ_WARDEN_P3_CHARGE = 17023, SEQ_WARDEN_P3_CHARGE_2 = 17061; // NPC_WARDENS_CHARGE01 9682 of 11761 / 11762
    /** WardenP3 POINT_LEFT_COORD (31, 36) / MIDDLE (32, 36) / RIGHT (33, 36) in region 15696; the P3 Warden stands at (30, 25): dx 1-3, dz 11. */
    private static final int SAFE_TILE_DZ = 11;

    /** SlamDirection.byAnimId: 0 RIGHT, 1 LEFT, 2 MIDDLE, -1 none. */
    private static int slamDirection(int animId) {
        for (int base : new int[] {SEQ_SLAM_ELIDINIS, SEQ_SLAM_TUMEKEN}) {
            int d = animId - base;
            if (d >= 0 && d < 6) {
                return d / 2;
            }
        }
        return -1;
    }
    private static final int SEQ_WARDEN_STANDING_UP = 16785; // ANIMATION_ID_WARDEN_STANDING_UP 9672
    private static final int GFX_LIGHTNING_1447 = 3394; // GRAPHICS_OBJECT_IDS_LIGHTNING_TILE 1447
    private static final int GFX_LIGHTNING_2197 = 3298;
    private static final int GFX_LIGHTNING_2198 = 3299;
    private static final int PROJ_BLACK_SKULL = 3360; // PROJECTILE_ID_BLACK_SKULL 2210
    private static final int PROJ_LIGHTNING = 3313; // PROJECTILE_ID_LIGHTNING 2225
    private static final int PROJ_CORE = 3320; // PROJECTILE_ID_CORE 2240

    // boss/warden/phase3/WardenP3.java + WardenP3SceneOverlay.java
    private static final int NPC_ENERGY_SIPHON = 15178; // ENERGY_SIPHON 11772
    private static final int NPC_DEAD_SIPHON = 15179; // NullNpcID.NULL_11773
    private static final int GFX_RED_LIGHTNING = 3235; // GRAPHICS_OBJECT_ID_RED_LIGHTNING 1446
    private static final int PROJ_ENERGY_SIPHON = 3314; // 2226
    private static final int PROJ_ENERGY_SIPHON_CHARGE = 3315; // 2227
    private static final int PROJ_TILE_DEBRIS = 3316; // 2228
    private static final int[] PROJ_ZEBAK_P3 = {3350, 3351, 3291, 3352, 3353, 3293}; // 2176 2177 2181 2178 2179 2187
    private static final double[] WARDEN_P3_HP_PERCENT_BREAKPOINTS = {0.80, 0.60, 0.40, 0.20, 0.05};
    private static final int BABA_BOULDER_TICKS = 7;
    private static final int RED_LIGHTNING_TICKS = 4;
    /** ENERGY_SIPHON_BASE_TICKS 7 + energySiphonSet; our Wardens have no charging form (11763/11764 not imported): set stays 0. */
    private static final int ENERGY_SIPHON_BASE_TICKS = 7;

    private static NPCEntity wardenP3;
    /** WardenP3.akkhaPrayer: set by Akkha's phantom (AKKHAS_PHANTOM 11777 = 15108); -1 = null. */
    private static int akkhaPrayerP3 = -1;
    private static final int NPC_AKKHAS_PHANTOM = 15108;
    private static final int SEQ_AKKHA_STYLE_SWITCH = 16343; // ANIMATION_ID_AKKHA_STYLE_SWITCH 9777
    private static final Map<NPCEntity, Integer> siphons = new java.util.IdentityHashMap<NPCEntity, Integer>();
    private static final Map<SpotAnimation, Integer> p3Boulders = new java.util.IdentityHashMap<SpotAnimation, Integer>();
    private static final Map<SpotAnimation, Integer> redLightning = new java.util.IdentityHashMap<SpotAnimation, Integer>();

    private static boolean wardenP2;
    /** WardenP3.nextSlamDirection (0 RIGHT, 1 LEFT, 2 MIDDLE) + drawSafeTile: the slam indicator (wardenSlamIndicator). */
    private static int nextSlam;
    private static boolean drawSafeTile;
    private static NPCEntity core;
    private static int coreTicks;

    private static NPCEntity akkha;
    private static int akkhaStyle = STYLE_MELEE;
    private static int akkhaTicksUntilNextAttack;

    private static NPCEntity kephri;
    private static int kephriAnim = -1;
    private static int atkCount;
    private static int prevAtkCount;
    private static int seenFours;
    private static int numOfFours;
    private static final Map<NPCEntity, Integer> eggs = new java.util.IdentityHashMap<NPCEntity, Integer>();

    private static NPCEntity zebak;
    private static NPCEntity baba;
    private static NPCEntity counted;
    private static int[] breakpoints;
    private static int breakpointsMax = -1;
    /** Baba.fallingBouldersToTicks: the client cycle each falling boulder was first seen (GraphicsObjectCreated). */
    private static final Map<SpotAnimation, Integer> fallingBoulders = new java.util.IdentityHashMap<SpotAnimation, Integer>();

    private static final Map<String, Sprite> TEXT = new HashMap<String, Sprite>();
    private static Toolkit textToolkit;

    private RspsToaExtended() {
    }

    static boolean on() {
        return RspsHudConfig.enabled(ID);
    }

    private static boolean bool(String key) {
        return RspsHudConfig.bool(ID + "." + key);
    }

    private static String get(String key) {
        return RspsHudConfig.get(ID + "." + key);
    }

    private static int argb(String key) {
        return RspsHudConfig.colour(ID + "." + key);
    }

    /** The npc's type after multinpc morphing (what NpcID compares in RuneLite: npc.getId()). */
    private static NPCType type(NPCEntity npc) {
        NPCType type = npc.type;
        if (type != null && type.multinpcs != null) {
            type = type.getMultiNPC(TimedVarDomain.instance);
        }
        return type;
    }

    /** <Boss>.isEnabled: raidState.getCurrentRoom() == ROOM - here: a loaded npc with the boss's name (<Boss>.onNpcSpawned). */
    private static NPCEntity find(String name) {
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || !node.npc.hasType()) {
                continue;
            }
            NPCType type = type(node.npc);
            if (type != null && name.equals(type.name)) {
                return node.npc;
            }
        }
        return null;
    }

    private static int typeId(Object entity) {
        if (!(entity instanceof NPCEntity) || !((NPCEntity) entity).hasType()) {
            return -1;
        }
        NPCType type = type((NPCEntity) entity);
        return type == null ? -1 : headIconBase(type.id);
    }

    /**
     * OSRS gives a boss one npc id per overhead prayer and the plugin's id sets hold all of them (e.g. NPC_IDS_WARDEN 11753/11754/
     * 11756/11757). Our server shows the overhead with a copy npc (data/cfg/toa/headicons.tsv, NpcHeadIconCloneTool): back to its base.
     */
    private static int headIconBase(int id) {
        if (id >= 15203 && id <= 15206) {
            return 15106; // Akkha
        }
        switch (id) {
            case 15207:
            case 15208:
                return NPC_ELIDINIS_P2;
            case 15209:
            case 15210:
                return NPC_TUMEKEN_P2;
            case 15211:
                return NPC_ELIDINIS_P3;
            case 15212:
                return NPC_TUMEKEN_P3;
            default:
                return id;
        }
    }

    // ---- RenderableDrawListener (Static102.method2026: an entity that is not drawn is not put in the scene) ------------------

    // ---- RuneLite events ---------------------------------------------------------------------------------------------------

    /** GameTick (ServerConnectionReader, after PLAYER_INFO): Kephri.onGameTick - eggs count down, swarms at Kephri drop out. */
    static void gameTick() {
        tickCount++; // client.getTickCount()
        if (on()) {
            hetTick();
        }
        // Zebak.onGameTick: attackProjectiles decrement + removeIf(isExpired); new rocks seen before the tick count from now
        if (zebak != null && on()) {
            queueZebakProjectiles();
            tickAttackProjectiles();
        } else if (wardenP2 && on()) {
            queueWardenP2Projectiles(); // WardenP2.onGameTick: the same queue rules
            tickAttackProjectiles();
        } else if (wardenP3 != null && on()) {
            queueZebakProjectiles(); // WardenP3.onProjectileMoved: Zebak's rocks, + getZebakProjectileTickCount() (3)
            tickAttackProjectiles();
        } else {
            clearAttackProjectiles();
        }
        // WardenP3.onGameTick: boulders, red lightning and siphons count down (a siphon may go below 0: its timer then hides)
        if (wardenP3 != null && on()) {
            scanP3Spots();
            decrement(p3Boulders);
            decrement(redLightning);
            for (Map.Entry<NPCEntity, Integer> e : siphons.entrySet()) {
                e.setValue(e.getValue() - 1);
            }
        }
        // WardenP2.onGameTick: coreToTicks + 1
        if (core != null) {
            coreTicks++;
        }
        // Akkha.onGameTick
        if (akkhaTicksUntilNextAttack > 0) {
            --akkhaTicksUntilNextAttack;
        }
        if (kephri == null || !on()) {
            eggs.clear();
            return;
        }
        // onNpcSpawned: a new egg starts at TICK_COUNT_EGG; onNpcDespawned removes it
        java.util.Set<NPCEntity> loaded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<NPCEntity, Boolean>());
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node != null && typeId(node.npc) == NPC_EGG) {
                loaded.add(node.npc);
                if (!eggs.containsKey(node.npc)) {
                    // RuneLite: NpcSpawned puts 15 and the GameTick of that same tick makes it 14. Our hook runs at PLAYER_INFO, before
                    // that tick's NPC_INFO, so an egg first seen here spawned last tick: 14, and this tick's decrement below -> 13.
                    eggs.put(node.npc, TICK_COUNT_EGG - 1);
                }
            }
        }
        java.util.Iterator<Map.Entry<NPCEntity, Integer>> it = eggs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<NPCEntity, Integer> e = it.next();
            int v = e.getValue() - 1; // eggsToTicks.replaceAll((k, v) -> v - 1)
            if (v <= 0 || !loaded.contains(e.getKey())) {
                it.remove();
            } else {
                e.setValue(v);
            }
        }
    }

    /** AnimationChanged: Kephri.onAnimationChanged -> updateAttackCount (the AttackCounter setting). */
    static void animationChanged(PathingEntity entity, int animId) {
        if (entity != null && entity == wardenP3 && on()) {
            int dir = slamDirection(animId);
            if (dir >= 0) {
                // WardenP3.onAnimationChanged: a slam hides the safe tile, the next slam follows RIGHT -> LEFT -> MIDDLE -> RIGHT
                drawSafeTile = false;
                nextSlam = (dir + 1) % 3;
            } else if (animId == SEQ_WARDEN_P3_CHARGE || animId == SEQ_WARDEN_P3_CHARGE_2) {
                // WardenP3.onNpcChanged (Warden -> charging form 11763/11764): our Wardens have no charging form, they play
                // NPC_WARDENS_CHARGE01 9682 when the siphon set starts - same moment; drawSafeTile true (direction kept, as the plugin)
                drawSafeTile = true;
            }
            return;
        }
        if (wardenP3 != null && entity instanceof NPCEntity && typeId(entity) == NPC_AKKHAS_PHANTOM && on()) {
            // WardenP3.onAnimationChanged (AKKHAS_PHANTOM): style switch toggles missiles <-> magic; magic / range attacks set it
            if (animId == SEQ_AKKHA_STYLE_SWITCH) {
                akkhaPrayerP3 = akkhaPrayerP3 == PRAY_MISSILES ? PRAY_MAGIC : PRAY_MISSILES;
            } else if (animId == SEQ_AKKHA_MAGIC) {
                akkhaPrayerP3 = PRAY_MAGIC;
            } else if (animId == SEQ_AKKHA_MISSILES) {
                akkhaPrayerP3 = PRAY_MISSILES;
            }
            return;
        }
        if (entity != null && entity == akkha && on()) {
            // Akkha.onAnimationChanged: an attack sets the style and 6 ticks until the next attack
            int style = animId == SEQ_AKKHA_MELEE ? STYLE_MELEE : animId == SEQ_AKKHA_MISSILES ? STYLE_RANGE
                : animId == SEQ_AKKHA_MAGIC ? STYLE_MAGE : 0;
            if (style != 0) {
                akkhaStyle = style;
                akkhaTicksUntilNextAttack = AKKHA_ATTACK_TICK_COUNT;
                AvTrace.log("[toaext] akkha attack anim=" + animId + " target=" + akkha.target + " self=" + PlayerList.activePlayerSlot);
            }
            return;
        }
        if (entity == null || entity != kephri || !on()) {
            return;
        }
        if (animId == SEQ_FIREBALL_ATTACK) {
            if (atkCount > 0) {
                --atkCount;
            }
            return;
        }
        boolean medic = "MEDIC".equals(get("kephriAttackCounter"));
        if (animId == SEQ_SPECIAL_ATTACK) {
            if (!medic) {
                atkCount = ATK_COUNT_MAX;
                return;
            }
            if (prevAtkCount <= ATK_COUNT_INIT) {
                atkCount = prevAtkCount = ATK_COUNT_MAX - 1;
                numOfFours = 1;
                seenFours = 0;
            } else if (prevAtkCount == (ATK_COUNT_MAX - 1)) {
                if (++seenFours == numOfFours) {
                    prevAtkCount = ATK_COUNT_MAX;
                }
                atkCount = prevAtkCount;
            } else if (prevAtkCount == ATK_COUNT_MAX) {
                atkCount = prevAtkCount = ATK_COUNT_MAX - 1;
                seenFours = 0;
                if (numOfFours < 3) {
                    numOfFours++;
                }
            }
        } else if (animId == SEQ_INACTIVE) {
            atkCount = medic ? ATK_COUNT_MAX : Math.max(atkCount + 1, 2);
        }
    }

    /** WorldArea.isInMeleeDistance: the tile touches the npc's footprint orthogonally (not diagonally, not inside). */
    private static boolean inMeleeDistance(NPCEntity npc, NPCType type, int tx, int tz) {
        int size = Math.max(1, type.size);
        int x0 = npc.pathX[0];
        int z0 = npc.pathZ[0];
        boolean xIn = tx >= x0 && tx < x0 + size;
        boolean zIn = tz >= z0 && tz < z0 + size;
        return (xIn && (tz == z0 - 1 || tz == z0 + size)) || (zIn && (tx == x0 - 1 || tx == x0 + size));
    }

    static boolean hidden(Object entity) {
        if (hetRoom && on()) {
            return hetHidden(entity);
        }
        if ((zebak == null && baba == null && kephri == null && akkha == null && !wardenP2 && wardenP3 == null) || !on()) {
            return false;
        }
        if (wardenP3 != null) {
            return wardenP3Hidden(entity);
        }
        if (wardenP2) {
            // WardenP2.shouldDraw: lightning projectiles and the lightning tile graphics objects
            if (entity instanceof ProjectileAnimation) {
                return ((ProjectileAnimation) entity).id == PROJ_LIGHTNING && bool("wardenHideLightningProjectiles");
            }
            if (entity instanceof SpotAnimation) {
                int id = ((SpotAnimation) entity).id;
                return (id == GFX_LIGHTNING_1447 || id == GFX_LIGHTNING_2197 || id == GFX_LIGHTNING_2198) && bool("wardenHideLightningTiles");
            }
            return false;
        }
        if (kephri != null) {
            return kephriHidden(entity);
        }
        if (akkha != null) {
            // AkkhaFinalStand.shouldDraw
            return typeId(entity) == NPC_UNSTABLE_ORB && bool("akkhaHideUnstableOrbs");
        }
        if (zebak != null) {
            // Zebak.shouldDraw: NPC id == NpcID.WAVE -> !config.zebakHideWaves()
            return typeId(entity) == NPC_WAVE && bool("zebakHideWaves");
        }
        // Baba.shouldDraw
        if (entity instanceof NPCEntity) {
            return typeId(entity) == NPC_BOULDER && bool("babaHideNonWeakenedRollingBoulders");
        }
        if (entity instanceof ProjectileAnimation) {
            int id = ((ProjectileAnimation) entity).id;
            return id == PROJ_BABA_BOULDER ? bool("babaHideRollingBoulderProjectiles")
                : id == PROJ_BABOON_ATTACK && bool("babaHideBaboonProjectiles");
        }
        if (entity instanceof SpotAnimation) {
            int id = ((SpotAnimation) entity).id;
            return (id == GFX_FALLING_BOULDER || id == GFX_FALLING_BOULDER_2) && bool("babaHideFallingBoulders");
        }
        return false;
    }

    // ---- scene overlay (RspsHud.render) --------------------------------------------------------------------------------------

    private static String lastRoom = "-";
    private static int viewWidth;
    private static int viewHeight;

    static void render(int screenX, int screenY, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        RspsModelOutline.drawAll(screenX, screenY); // ModelOutlineRenderer: the outlines queued while this frame's npcs were drawn
        if (!on() || PlayerEntity.self == null || Toolkit.active == null) {
            zebak = null;
            baba = null;
            kephri = null;
            akkha = null;
            wardenP2 = false;
            core = null;
            wardenP3 = null;
            siphons.clear();
            p3Boulders.clear();
            redLightning.clear();
            fallingBoulders.clear();
            eggs.clear();
            return;
        }
        zebak = find(ZEBAK_NAME);
        baba = zebak == null ? find(BABA_NAME) : null;
        NPCEntity foundKephri = zebak == null && baba == null ? find(KEPHRI_NAME) : null;
        if (foundKephri != null && typeId(foundKephri) == NPC_KEPHRI_DEAD) {
            foundKephri = null; // Kephri.onNpcChanged: KEPHRI_11722 -> npc = null
        }
        if (foundKephri != kephri) {
            // Kephri.reset()
            eggs.clear();
            atkCount = prevAtkCount = seenFours = numOfFours = 0;
            kephri = foundKephri;
        }
        NPCEntity foundAkkha = zebak == null && baba == null && kephri == null ? find(AKKHA_NAME) : null;
        // WardenP2.isEnabled (RaidRoom.WARDEN_P2): a phase 2 Warden or a core is loaded and no phase 3 Warden
        boolean p2 = false;
        boolean p3 = false;
        NPCEntity foundCore = null;
        if (zebak == null && baba == null && kephri == null && foundAkkha == null) {
            for (int i = 0; i < NPCList.size; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                int id = node == null ? -1 : typeId(node.npc);
                // room = RaidRoom.WARDEN_P2: also our transmogs in that phase - kneeling 11755/11758 (15130/15131) and the fallen
                // Warden's wreck 11759/11760 (15132/15133); the overlays themselves still use NPC_IDS_WARDEN (15105/15129) as the plugin
                p2 |= id == NPC_ELIDINIS_P2 || id == NPC_TUMEKEN_P2 || id == NPC_CORE || id == NPC_CORE_2
                    || (id >= 15130 && id <= 15133);
                p3 |= id == NPC_ELIDINIS_P3 || id == NPC_TUMEKEN_P3;
                if (id == NPC_CORE || id == NPC_CORE_2) {
                    foundCore = node.npc;
                }
            }
        }
        wardenP2 = p2 && !p3;
        NPCEntity foundP3 = null;
        if (p3) {
            for (int i = 0; i < NPCList.size; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                int id = node == null ? -1 : typeId(node.npc);
                if (id == NPC_ELIDINIS_P3 || id == NPC_TUMEKEN_P3) {
                    foundP3 = node.npc;
                }
            }
        }
        if (foundP3 != wardenP3) {
            // WardenP3.reset()
            siphons.clear();
            p3Boulders.clear();
            redLightning.clear();
            akkhaPrayerP3 = -1;
            // WardenP3.reset: nextSlamDirection RIGHT, drawSafeTile false; onNpcSpawned (a Warden): drawSafeTile true
            nextSlam = 0;
            drawSafeTile = foundP3 != null;
            wardenP3 = foundP3;
        }
        if (foundCore != core) {
            // onNpcSpawned: coreToTicks = (npc, 0); onNpcDespawned: null
            core = foundCore;
            coreTicks = 0;
        }
        if (foundAkkha != akkha) {
            // Akkha.reset() (also the "Challenge started: Akkha." chat line: style back to MELEE)
            akkhaStyle = STYLE_MELEE;
            akkhaTicksUntilNextAttack = 0;
            akkha = foundAkkha;
        }
        NPCEntity boss = zebak != null ? zebak : baba != null ? baba : akkha;
        if (boss != counted) {
            // <Boss>.onNpcDespawned -> reset(): hpBreakpoints = null
            breakpoints = null;
            breakpointsMax = -1;
            counted = boss;
        }
        if (baba == null) {
            fallingBoulders.clear();
        }
        if (zebak != null) {
            renderZebak(screenX, screenY);
        } else if (baba != null) {
            renderBaba(screenX, screenY);
        } else if (kephri != null) {
            renderKephri(screenX, screenY);
        } else if (akkha != null) {
            renderAkkha(screenX, screenY);
        } else if (wardenP2) {
            renderWardenP2(screenX, screenY);
        } else if (wardenP3 != null) {
            renderWardenP3(screenX, screenY);
        }
        if (akkha != null) {
            akkhaSeenAt = System.currentTimeMillis();
        }
        // trace (C:\RSPS\trace.on): which room module is active, once per change
        String room = zebak != null ? "zebak" : baba != null ? "baba" : kephri != null ? "kephri" : akkha != null ? "akkha"
            : wardenP2 ? "wardenP2" : wardenP3 != null ? "wardenP3" : hetRoom ? "het" : "-";
        if (!room.equals(lastRoom)) {
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < NPCList.size && i < 40; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                if (node != null && node.npc.hasType()) {
                    ids.append(' ').append(node.npc.type.id).append('/').append(typeId(node.npc));
                }
            }
            AvTrace.log("[toaext] kamer=" + room + " npcs(basis/morph):" + ids);
            lastRoom = room;
        }
        if (hetRoom) {
            renderHet(screenX, screenY);
        }
        apmekenRoom = false;
        if (zebak == null && baba == null && kephri == null && akkha == null && !wardenP2 && wardenP3 == null) {
            for (int i = 0; i < NPCList.size && !apmekenRoom; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                apmekenRoom = node != null && apmekenBaboon(typeId(node.npc));
            }
        }
        if (apmekenRoom) {
            renderApmeken(screenX, screenY);
        }
        if (zebak == null && baba == null && kephri == null && akkha == null && !wardenP2 && wardenP3 == null && !apmekenRoom) {
            renderScabaras(screenX, screenY);
        }
        if (akkhaRoom() && zebak == null && baba == null && kephri == null && !wardenP2 && wardenP3 == null) {
            scanMemoryElements(); // AkkhaMemoryBlast is enabled for the whole room, also while Akkha is invisible
            renderMemoryBlast(screenX, screenY);
        }
        if (zebak != null || akkha != null || wardenP2 || wardenP3 != null || akkhaRoom()) {
            if (wardenP3 != null) {
                queueZebakProjectiles();
                queuedProjectiles.retainAll(liveProjectiles());
            }
            renderPrayerInfobox(screenX, screenY, viewWidth, viewHeight); // PrayerInfoboxOverlay (BOTTOM_RIGHT)
        }
    }

    /** AkkhaSceneOverlay.render + AkkhaFinalStand.npcHighlight (spec outline + memory blast: still to build). */
    private static void renderAkkha(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        NPCType at = type(akkha);
        if (bool("akkhaHealthCounter")) {
            healthCounter(akkha, AKKHA_HP_PERCENT_BREAKPOINTS, -1, screenX, screenY);
        }
        if (bool("akkhaAttackTickCounter") && akkhaTicksUntilNextAttack > 0 && akkha.target != -1) {
            // renderAttackTickCounter: over Akkha's target (actor.getInteracting()), red at 1
            PathingEntity target = null;
            if (akkha.target < 32768) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(akkha.target);
                target = node == null ? null : node.npc;
            } else {
                // as ServerConnectionReader / Static527: the local player is PlayerEntity.self, not highResolutionPlayers[its slot]
                int slot = akkha.target - 32768;
                target = slot == PlayerList.activePlayerSlot ? PlayerEntity.self : PlayerList.highResolutionPlayers[slot];
            }
            if (target != null) {
                Static418.method7860(target.level, target.z, 0, target.x, 0); // getCanvasTextLocation(g, text, 0): zOffset 0 = ground height
                if (OverlayManager.hitmarkpos[0] >= 0) {
                    textAt(Integer.toString(akkhaTicksUntilNextAttack), screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1],
                        akkhaTicksUntilNextAttack == 1 ? 0xFFFF0000 : 0xFFFFFFFF);
                }
            }
        }
        String akkhaTile = get("akkhaTile");
        if (!"OFF".equals(akkhaTile) && at != null) {
            // renderTileOutline: the attack style colour, no fill, stroke 1
            npcTile(akkha, at, "TRUE_TILE".equals(akkhaTile), akkhaStyle, 0, screenX, screenY);
        }
        String orbTile = get("akkhaUnstableOrbTiles");
        boolean radius = bool("akkhaUnstableOrbRadius");
        if ("OFF".equals(orbTile) && !radius) {
            return;
        }
        int outline = argb("dangerOutlineColor");
        int fill = argb("dangerFillColor");
        java.util.Set<Long> radiusTiles = new java.util.HashSet<Long>();
        int distance = Math.max(0, Math.min(7, Integer.parseInt(get("akkhaRadiusDistance"))));
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || node.npc.level != level || typeId(node.npc) != NPC_UNSTABLE_ORB) {
                continue;
            }
            NPCEntity orb = node.npc;
            NPCType ot = type(orb);
            if (!"OFF".equals(orbTile)) {
                npcTile(orb, ot, "TRUE_TILE".equals(orbTile), outline, fill, screenX, screenY);
            }
            if (radius) {
                // getUnstableOrbLocalPoints: orientation / 256 (RuneLite 2048 units; ours 16384 -> / 2048): 0 S, 1 SW, 2 W, 3 NW,
                // 4 N, 5 NE, 6 E, 7 SE; then 'distance' tiles from the orb's local point in that direction
                int octant = (orb.yaw.value & 0x3FFF) / 2048;
                int[] dxs = {0, -1, -1, -1, 0, 1, 1, 1};
                int[] dzs = {-1, -1, 0, 1, 1, 1, 0, -1};
                int size = Math.max(1, ot.size);
                for (int d = 1; d <= distance; d++) {
                    int xd = d * dxs[octant];
                    int zd = d * dzs[octant];
                    int fx = orb.x + xd * 512 + xd * 512 * (size - 1) / 2;
                    int fz = orb.z + zd * 512 + zd * 512 * (size - 1) / 2;
                    radiusTiles.add(key(fx >> 9, fz >> 9));
                }
            }
        }
        for (long t : radiusTiles) {
            // a Set of LocalPoints: each tile drawn once (getCanvasTilePoly per point)
            tile(level, (int) (t >> 32) * 512 + 256, (int) t * 512 + 256, 1, outline, fill, screenX, screenY);
        }
    }

    /** WardenP2.npcHighlight + WardenP2SceneOverlay.render (prayer overlay: still to build). */
    private static void renderWardenP2(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        queueWardenP2Projectiles();
        queuedProjectiles.retainAll(liveProjectiles());
        NPCEntity warden = null;
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || node.npc.level != level) {
                continue;
            }
            int id = typeId(node.npc);
            NPCType t = type(node.npc);
            if (id == NPC_ELIDINIS_P2 || id == NPC_TUMEKEN_P2) {
                warden = node.npc; // onNpcChanged: the Warden that is in its phase 2 form
                if (bool("wardenTile")) {
                    npcTile(node.npc, t, true, argb("tileOutlineColor"), argb("tileFillColor"), screenX, screenY);
                }
            }
        }
        if (bool("wardenP2HealthCounter") && warden != null && RspsHudBossBar.isOpen()
            && (warden.actionAnimations == null || warden.actionAnimations[0] != SEQ_WARDEN_STANDING_UP)) {
            // onScriptPostFired(HP_HUD_UPDATE): hpRemaining = maximum - current
            int max = RspsHudBossBar.maxHp();
            int cur = RspsHudBossBar.currentHp();
            if (max > 1 && cur > 0 && max - cur > 0) {
                Static418.method7860(warden.level, warden.z, 0, warden.x, 0);
                if (OverlayManager.hitmarkpos[0] >= 0) {
                    textAt(Integer.toString(max - cur), screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1], 0xFFFFFFFF);
                }
            }
        }
        if (bool("wardenCoreTickTimer") && core != null) {
            // renderCoreTickTimer: getCanvasTextLocation(g, text, logicalHeight + 40), OverlayUtil.renderTextLocation (white, shadow)
            String text = Integer.toString(coreTicks);
            Static418.method7860(core.level, core.z, core.getY() + 40, core.x, 0);
            if (OverlayManager.hitmarkpos[0] >= 0) {
                int w = RspsHud.width(RspsHud.metrics(), text);
                RspsHud.text(RspsHud.font(), text, screenX + OverlayManager.hitmarkpos[0] - w / 2, screenY + OverlayManager.hitmarkpos[1], 0xFFFFFFFF, false); // RspsHud.text wants ARGB (alpha 0 = only the shadow showed)
            }
        }
        int outline = argb("dangerOutlineColor");
        int fill = argb("dangerFillColor");
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            ProjectileAnimation p = n.projectileAnimation;
            int remaining = p.t2 - TimeUtils.clock;
            if (remaining <= 0 || p.targetX < 0 || p.level != level) {
                continue;
            }
            int tx = p.targetX >> 9;
            int tz = p.targetZ >> 9;
            if (p.id == PROJ_CORE && bool("wardenCoreTile")) {
                // renderCoreTile: magenta, fill (255, 0, 255, 10)
                tile(p.level, tx * 512 + 256, tz * 512 + 256, 1, 0xFFFF00FF, 0x0AFF00FF, screenX, screenY);
            } else if (p.id == PROJ_BLACK_SKULL && bool("wardenBlackSkullProjectileTile")) {
                tile(p.level, tx * 512 + 256, tz * 512 + 256, 1, outline, fill, screenX, screenY);
            } else if (p.id == PROJ_LIGHTNING && bool("wardenLightningProjectileTile")) {
                // renderLightningProjectileTiles: a 7x7 around the target without the inner arms of the cross, one Area per bolt
                int ticks = remaining / CYCLES_PER_TICK + 1;
                int rgb = ticks <= 2 ? 0xFF0000 : ticks == 3 ? 0xFFC800 : 0xFFFF00;
                java.util.Set<Long> area = new java.util.HashSet<Long>();
                int size = 3;
                for (int dx = -size; dx <= size; dx++) {
                    for (int dz = -size; dz <= size; dz++) {
                        if (dx == 0 && (dz < size && dz > -size && dz != 0)) {
                            continue;
                        }
                        if (dz == 0 && (dx < size && dx > -size && dx != 0)) {
                            continue;
                        }
                        area.add(key(tx + dx, tz + dz));
                    }
                }
                tileArea(p.level, area, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
            }
        }
    }

    private static void decrement(Map<SpotAnimation, Integer> map) {
        java.util.Iterator<Map.Entry<SpotAnimation, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SpotAnimation, Integer> e = it.next();
            int v = e.getValue() - 1;
            if (v <= 0) {
                it.remove();
            } else {
                e.setValue(v);
            }
        }
    }

    /** WardenP3.onGraphicsObjectCreated / onNpcSpawned: new boulders, red lightning and siphons get their start value. */
    private static void scanP3Spots() {
        for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
            SpotAnimation s = n.spotAnimation;
            if ((s.id == GFX_FALLING_BOULDER || s.id == GFX_FALLING_BOULDER_2) && !p3Boulders.containsKey(s)) {
                p3Boulders.put(s, BABA_BOULDER_TICKS);
            } else if (s.id == GFX_RED_LIGHTNING && !redLightning.containsKey(s)) {
                redLightning.put(s, RED_LIGHTNING_TICKS);
            }
        }
        java.util.Set<NPCEntity> loaded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<NPCEntity, Boolean>());
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node != null) {
                loaded.add(node.npc);
                if (typeId(node.npc) == NPC_ENERGY_SIPHON && !siphons.containsKey(node.npc)) {
                    siphons.put(node.npc, ENERGY_SIPHON_BASE_TICKS);
                }
            }
        }
        siphons.keySet().retainAll(loaded);
    }

    /** WardenP3.shouldDraw. */
    private static boolean wardenP3Hidden(Object entity) {
        if (entity instanceof NPCEntity) {
            return typeId(entity) == NPC_DEAD_SIPHON && bool("wardenHideDeadEnergySiphon");
        }
        if (entity instanceof ProjectileAnimation) {
            int id = ((ProjectileAnimation) entity).id;
            if (id == PROJ_ENERGY_SIPHON_CHARGE) {
                return bool("wardenHideEnergySiphonChargeProjectiles");
            }
            if (id == PROJ_ENERGY_SIPHON) {
                return bool("wardenHideEnergySiphonProjectile");
            }
            if (id == PROJ_TILE_DEBRIS) {
                return bool("wardenHideTileDebrisProjectile");
            }
            if (id == PROJ_PRE_FIREBALL || id == PROJ_FIREBALL) {
                return bool("wardenHideKephriFireballProjectile");
            }
            for (int z : PROJ_ZEBAK_P3) {
                if (id == z) {
                    return bool("wardenHideZebakProjectile");
                }
            }
            return false;
        }
        if (entity instanceof SpotAnimation) {
            int id = ((SpotAnimation) entity).id;
            if (id == GFX_FALLING_BOULDER || id == GFX_FALLING_BOULDER_2) {
                return bool("wardenHideBabaFallingBoulders");
            }
            return id == GFX_RED_LIGHTNING && bool("wardenHideRedLightning");
        }
        return false;
    }

    /** WardenP3SceneOverlay.render (slam indicator needs the P3 region origin of our instance; prayer overlay: both still to build). */
    private static void renderWardenP3(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        scanP3Spots(); // GraphicsObjectCreated / NpcSpawned between ticks
        if (bool("wardenSlamIndicator") && drawSafeTile) {
            // renderSlamIndicator: getSafeTileLocalPoint (RIGHT -> left tile x31, LEFT -> right tile x33, MIDDLE -> x32), GREEN / (0,255,0,20)
            int dx = nextSlam == 0 ? 1 : nextSlam == 1 ? 3 : 2;
            int tx = wardenP3.pathX[0] + dx;
            int tz = wardenP3.pathZ[0] + SAFE_TILE_DZ;
            tile(wardenP3.level, tx * 512 + 256, tz * 512 + 256, 1, 0xFF00FF00, 0x1400FF00, screenX, screenY);
        }
        if (bool("wardenP3HealthCounter")) {
            healthCounter(wardenP3, WARDEN_P3_HP_PERCENT_BREAKPOINTS, -1, screenX, screenY);
        }
        if (bool("wardenEnergySiphonTickTimer")) {
            for (Map.Entry<NPCEntity, Integer> e : siphons.entrySet()) {
                int ticks = e.getValue();
                NPCEntity npc = e.getKey();
                if (ticks < 0 || typeId(npc) != NPC_ENERGY_SIPHON || npc.level != level) {
                    continue;
                }
                // getCanvasTextLocation(g, text, logicalHeight + 40) + OverlayUtil.renderTextLocation: red at 1, else white
                String text = Integer.toString(ticks);
                Static418.method7860(npc.level, npc.z, npc.getY() + 40, npc.x, 0);
                if (OverlayManager.hitmarkpos[0] >= 0) {
                    int w = RspsHud.width(RspsHud.metrics(), text);
                    RspsHud.text(RspsHud.font(), text, screenX + OverlayManager.hitmarkpos[0] - w / 2, screenY + OverlayManager.hitmarkpos[1],
                        ticks == 1 ? 0xFFFF0000 : 0xFFFFFFFF, false);
                }
            }
        }
        String fireball = get("wardenKephriFireballTile");
        boolean siphonTiles = bool("wardenEnergySiphonProjectileTile");
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            ProjectileAnimation p = n.projectileAnimation;
            int remaining = p.t2 - TimeUtils.clock;
            if (remaining <= 0 || p.targetX < 0 || p.level != level) {
                continue;
            }
            if (p.id == PROJ_ENERGY_SIPHON && siphonTiles) {
                // renderEnergySiphonProjectileTile: magenta, fill (255, 0, 255, 20)
                tile(p.level, (p.targetX >> 9) * 512 + 256, (p.targetZ >> 9) * 512 + 256, 1, 0xFFFF00FF, 0x14FF00FF, screenX, screenY);
            } else if (p.id == PROJ_FIREBALL && !"OFF".equals(fireball)) {
                // renderKephriFireballTile: red at <= 1 tick, else orange; AERIAL = 3x3 around the target point
                int ticks = remaining / CYCLES_PER_TICK + 1;
                int rgb = ticks <= 1 ? 0xFF0000 : 0xFFC800;
                boolean aerial = "AERIAL".equals(fireball);
                int cx = aerial ? p.targetX : (p.targetX >> 9) * 512 + 256;
                int cz = aerial ? p.targetZ : (p.targetZ >> 9) * 512 + 256;
                tile(p.level, cx, cz, aerial ? 3 : 1, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
            }
        }
        if (bool("wardenBabaFallingBoulderTile")) {
            for (Map.Entry<SpotAnimation, Integer> e : p3Boulders.entrySet()) {
                SpotAnimation s = e.getKey();
                int rgb = e.getValue() <= 2 ? 0xFF0000 : 0xFFC800;
                if (s.level == level) {
                    tile(s.level, (s.x >> 9) * 512 + 256, (s.z >> 9) * 512 + 256, 1, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
                }
            }
        }
        if (bool("wardenRedLightningTiles")) {
            for (Map.Entry<SpotAnimation, Integer> e : redLightning.entrySet()) {
                SpotAnimation s = e.getKey();
                int ticks = e.getValue();
                int rgb = ticks <= 1 ? 0xFF0000 : ticks == 2 ? 0xFF4D00 : 0xFFFF00;
                if (s.level == level) {
                    tile(s.level, (s.x >> 9) * 512 + 256, (s.z >> 9) * 512 + 256, 1, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
                }
            }
        }
    }

    // ---- PrayerWidgetOverlay (boss/PrayerWidgetOverlay.java + AttackProjectile.java) ----------------------------------------

    /** Prayer: 0 = PROTECT_FROM_MAGIC, 1 = PROTECT_FROM_MISSILES, 2 = PROTECT_FROM_MELEE. */
    private static final int PRAY_MAGIC = 0;
    private static final int PRAY_MISSILES = 1;
    private static final int PRAY_MELEE = 2;
    /** getPrayerColor: magic blue, missiles green, melee red. */
    private static final int[] PRAYER_COLOUR = {0xFF0000FF, 0xFF00FF00, 0xFFFF0000};
    /** The protect cells per book (271:8 grid slots): normal Protect from X, curses Deflect X, Ruinous Dampen X (closest equivalent). */
    private static final int[][] PROTECT_SLOT = {{17, 18, 19}, {7, 8, 9}, {6, 7, 8}};
    private static final int BOX_W = 10; // DIMENSION 10 x 5
    private static final int BOX_H = 5;
    private static final int TICK_PIXEL_SIZE = 60;

    /** AttackProjectile: prayer, ticks left, priority; the queue is ordered by ticks, then priority. */
    private static final class AttackProjectile {
        final int prayer;
        final int priority;
        int ticks;

        AttackProjectile(int prayer, int ticks, int priority) {
            this.prayer = prayer;
            this.ticks = ticks;
            this.priority = priority;
        }
    }

    private static final java.util.Comparator<AttackProjectile> ORDER = new java.util.Comparator<AttackProjectile>() {
        @Override
        public int compare(AttackProjectile a, AttackProjectile b) {
            return a.ticks != b.ticks ? Integer.compare(a.ticks, b.ticks) : Integer.compare(a.priority, b.priority);
        }
    };
    private static final java.util.PriorityQueue<AttackProjectile> attackProjectiles = new java.util.PriorityQueue<AttackProjectile>(11, ORDER);
    private static final java.util.Set<ProjectileAnimation> queuedProjectiles =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ProjectileAnimation, Boolean>());
    private static long lastTickTime;

    private static final int[][] cells = new int[64][4];
    private static final boolean[] cellSeen = new boolean[64];

    /** InterfaceManager: where each cell of the prayer grid is drawn this frame (only while the prayer tab is open). */
    static void prayerCell(int slot, int x, int y, int w, int h) {
        if (slot >= 0 && slot < cells.length) {
            cells[slot][0] = x;
            cells[slot][1] = y;
            cells[slot][2] = w;
            cells[slot][3] = h;
            cellSeen[slot] = true;
        }
    }

    /** Zebak.onProjectileMoved: magic / ranged rocks -> PROTECT_FROM_MAGIC / MISSILES, cyclesToTicks(remaining) + 3 (path level < 4). */
    private static void queueZebakProjectiles() {
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            ProjectileAnimation p = n.projectileAnimation;
            int remaining = p.t2 - TimeUtils.clock;
            if (remaining <= 0 || queuedProjectiles.contains(p)) {
                continue;
            }
            int prayer = p.id == 3350 || p.id == 3351 ? PRAY_MAGIC : p.id == 3352 || p.id == 3353 ? PRAY_MISSILES : -1;
            if (prayer >= 0) {
                queuedProjectiles.add(p);
                attackProjectiles.add(new AttackProjectile(prayer, remaining / CYCLES_PER_TICK + 1 + 3, 0));
            }
        }
    }

    /** <Boss>.onGameTick: every queued attack loses a tick; at 0 it is gone. */
    private static void tickAttackProjectiles() {
        lastTickTime = System.currentTimeMillis();
        if (attackProjectiles.isEmpty()) {
            return;
        }
        java.util.List<AttackProjectile> all = new java.util.ArrayList<AttackProjectile>(attackProjectiles);
        attackProjectiles.clear();
        for (AttackProjectile a : all) {
            if (a.ticks > 0) {
                a.ticks--;
            }
            if (a.ticks != 0) {
                attackProjectiles.add(a);
            }
        }
    }

    private static java.util.Set<ProjectileAnimation> liveProjectiles() {
        java.util.Set<ProjectileAnimation> live = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ProjectileAnimation, Boolean>());
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            live.add(n.projectileAnimation);
        }
        return live;
    }

    // WardenP2 PROJECTILE_ID_*: white skull 2241, red skull 2224, arcane scimitar 2204, white arrow 2206, blue spell 2208
    private static final int PROJ_WHITE_SKULL = 3378;
    private static final int PROJ_RED_SKULL = 3312;
    private static final int PROJ_ARCANE_SCIMITAR = 3359;
    private static final int PROJ_WHITE_ARROW = 3306;
    private static final int PROJ_BLUE_SPELL = 3308;
    private static final int WARDEN_SPECIAL_PROJECTILE_TICKS = 5;
    private static int lastStartCycle;

    /**
     * WardenP2.onProjectileMoved: only projectiles at the local player (or at nobody); one per start cycle (lastStartCycle); skulls
     * count cyclesToTicks(getProjectileCycles) = remaining cycles + 3 per tile between where it started and the player; the three
     * specials always WARDEN_SPECIAL_PROJECTILE_TICKS.
     */
    private static void queueWardenP2Projectiles() {
        PlayerEntity self = PlayerEntity.self;
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            ProjectileAnimation p = n.projectileAnimation;
            int remaining = p.t2 - TimeUtils.clock;
            if (remaining <= 0 || queuedProjectiles.contains(p)) {
                continue;
            }
            if (p.entity2 > 0 || (p.entity2 < 0 && -p.entity2 - 1 != PlayerList.activePlayerSlot)) {
                continue; // getInteracting() is someone else
            }
            int prayer;
            int ticks;
            if (p.id == PROJ_WHITE_SKULL || p.id == PROJ_RED_SKULL) {
                int distance = Math.max(Math.abs((p.startX >> 9) - (self.x >> 9)), Math.abs((p.startZ >> 9) - (self.z >> 9)));
                ticks = (remaining + distance * 3) / CYCLES_PER_TICK + 1;
                prayer = p.id == PROJ_WHITE_SKULL ? PRAY_MISSILES : PRAY_MAGIC;
            } else if (p.id == PROJ_ARCANE_SCIMITAR || p.id == PROJ_WHITE_ARROW || p.id == PROJ_BLUE_SPELL) {
                ticks = WARDEN_SPECIAL_PROJECTILE_TICKS;
                prayer = p.id == PROJ_ARCANE_SCIMITAR ? PRAY_MELEE : p.id == PROJ_WHITE_ARROW ? PRAY_MISSILES : PRAY_MAGIC;
            } else {
                continue;
            }
            queuedProjectiles.add(p);
            if (p.t1 == lastStartCycle) {
                continue;
            }
            lastStartCycle = p.t1;
            attackProjectiles.add(new AttackProjectile(prayer, ticks, 0));
        }
    }

    private static void clearAttackProjectiles() {
        attackProjectiles.clear();
        queuedProjectiles.clear();
    }

    /** InterfaceManager, after all interfaces (OverlayLayer.ABOVE_WIDGETS): PrayerWidgetOverlay.render. */
    static void drawPrayerWidget() {
        try {
            if (!on() || Toolkit.active == null || !prayerIndicator()) {
                return;
            }
            int book = RspsPrayerFilters.book();
            int[] slots = PROTECT_SLOT[Math.max(0, Math.min(2, book))];
            if (!cellSeen[slots[0]]) {
                return; // isPrayerTabClosed
            }
            boolean queued = zebak != null || wardenP2 || wardenP3 != null; // Akkha: getAttackProjectileQueue() == null
            String mode = get("prayerMode");
            if ("ALL".equals(mode) || "WIDGET".equals(mode)) {
                int prayer = nextPrayer();
                if (prayer >= 0) {
                    int[] c = cells[slots[prayer]];
                    // OverlayUtil.renderPolygon(rectangle, colour): stroke 2 in the prayer colour, fill black at alpha 50
                    Toolkit.active.fillRect(c[0], c[1], c[2], c[3], 0x32000000);
                    Toolkit.active.outlineRect(c[0], c[1], c[2], c[3], PRAYER_COLOUR[prayer]);
                    Toolkit.active.outlineRect(c[0] + 1, c[1] + 1, c[2] - 2, c[3] - 2, PRAYER_COLOUR[prayer]);
                    if (queued && !attackProjectiles.isEmpty()) {
                        // ticks of the first attack: Arial bold 16 with shadow at (centre x - 3, centre y + 6), red at <= 1
                        int ticks = attackProjectiles.peek().ticks;
                        textLeft(Integer.toString(ticks), c[0] + c[2] / 2 - 3, c[1] + c[3] / 2 + 6, ticks <= 1 ? 0xFFFF0000 : 0xFFFFFFFF, 16, Font.BOLD);
                    }
                }
            }
            if (queued && bool("prayerDescendingBoxes")) {
                renderDescendingBoxes(slots);
            }
        } finally {
            java.util.Arrays.fill(cellSeen, false);
        }
    }

    /** isPrayerWidgetEnabled / isPrayerInfoboxEnabled of the boss whose room is active. */
    private static boolean prayerIndicator() {
        return zebak != null ? bool("zebakPrayerIndicator") : akkha != null ? bool("akkhaPrayerIndicator")
            : wardenP2 ? bool("wardenP2PrayerIndicator") : wardenP3 != null ? bool("wardenP3PrayerIndicator")
            : akkhaRoom() && bool("akkhaPrayerIndicator"); // Akkha invisible (Memory Blast): the room is still Akkha's
    }

    /** getNextPrayer: Zebak = the first queued attack; Akkha.getPrayer = magic / missiles while that attack plays, else its style. */
    private static int nextPrayer() {
        if (wardenP3 != null && akkhaPrayerP3 >= 0) {
            return akkhaPrayerP3; // WardenP3 getNextPrayer: Akkha's phantom first
        }
        if (zebak != null || wardenP2 || wardenP3 != null) {
            AttackProjectile next = attackProjectiles.peek();
            return next == null ? -1 : next.prayer;
        }
        if (akkha != null) {
            int anim = akkha.actionAnimator.getAnimationId();
            if (anim == SEQ_AKKHA_MAGIC) {
                return PRAY_MAGIC;
            }
            if (anim == SEQ_AKKHA_MISSILES) {
                return PRAY_MISSILES;
            }
            return akkhaStyle == STYLE_MAGE ? PRAY_MAGIC : akkhaStyle == STYLE_RANGE ? PRAY_MISSILES : PRAY_MELEE;
        }
        if (akkhaRoom()) {
            // Akkha.getPrayer with npc == null: attackStyle.getPrayer()
            return akkhaStyle == STYLE_MAGE ? PRAY_MAGIC : akkhaStyle == STYLE_RANGE ? PRAY_MISSILES : PRAY_MELEE;
        }
        return -1;
    }

    private static void renderDescendingBoxes(int[] slots) {
        // getTickPriorityMap: per tick the attack with the lowest priority value (the first one met wins a tie)
        Map<Integer, AttackProjectile> byTick = new HashMap<Integer, AttackProjectile>();
        for (AttackProjectile a : attackProjectiles) {
            AttackProjectile cur = byTick.get(a.ticks);
            if (cur == null || a.priority < cur.priority) {
                byTick.put(a.ticks, a);
            }
        }
        int box = RspsHudConfig.colour(ID + ".prayerBoxColor");
        int warn = RspsHudConfig.colour(ID + ".prayerBoxWarnColor");
        boolean nonPriority = bool("prayerNonPriority");
        long now = System.currentTimeMillis();
        for (AttackProjectile a : attackProjectiles) {
            int[] c = cells[slots[a.prayer]];
            int colour = a.ticks == 1 ? warn : box;
            int baseX = c[0] + c[2] / 2 - BOX_W / 2;
            int baseY = c[1] - a.ticks * TICK_PIXEL_SIZE - BOX_H;
            baseY += (int) (TICK_PIXEL_SIZE - ((lastTickTime + 600 - now) / 600.0 * TICK_PIXEL_SIZE));
            if (a.prayer == byTick.get(a.ticks).prayer) {
                Toolkit.active.fillRect(baseX, baseY, BOX_W, BOX_H, colour); // renderFilledPolygon
            } else if (nonPriority) {
                Toolkit.active.outlineRect(baseX, baseY, BOX_W, BOX_H, colour); // renderOutlinePolygon
            }
        }
    }

    /** ToaUtils.renderTextLocation at a fixed point (x = left edge, y = baseline), Arial in the given size/style, shadow +1,+1. */
    private static void textLeft(String text, int x, int baseline, int colour, int size, int awtStyle) {
        Sprite sprite = textSprite(text, size, awtStyle, colour);
        sprite.render(x, baseline - textAscent);
    }

    // ---- PrayerInfoboxOverlay (boss/PrayerInfoboxOverlay.java) -------------------------------------------------------------

    /** OSRS SpriteID.Prayeron._12/_13/_14 = 127/128/129 (cache2686, resources/osrs/prayer-protect-*.png). */
    private static final String[] PRAYER_SPRITE = {"osrs/prayer-protect-magic", "osrs/prayer-protect-missiles", "osrs/prayer-protect-melee"};
    /** Varbit that is 1 while the protect prayer is on: normal 5954/5955/5956, curses deflects 6827/6828/6829, Ruinous 9931 + slot. */
    private static final int[][] PROTECT_VARBIT = {{5954, 5955, 5956}, {6827, 6828, 6829}, {9937, 9938, 9939}};
    private static final int INFOBOX = 30; // DIMENSION 30 x 30
    /** ComponentConstants.STANDARD_BACKGROUND_COLOR (RspsHud.PANEL_BACKGROUND) and COLOR_OFF (150, 0, 0, 150). */
    private static final int COLOR_OFF = 0x96960000;
    private static final Sprite[] prayerSprites = new Sprite[3];
    private static Toolkit prayerSpritesFor;

    /** scaleSprite: the icon scaled to fit 25 x 25 (DIMENSION - 5), keeping its aspect ratio. */
    private static Sprite prayerSprite(int prayer) {
        Toolkit toolkit = Toolkit.active;
        if (toolkit != prayerSpritesFor) {
            java.util.Arrays.fill(prayerSprites, null);
            prayerSpritesFor = toolkit;
        }
        if (prayerSprites[prayer] == null) {
            try (java.io.InputStream in = RspsToaExtended.class.getResourceAsStream("/" + PRAYER_SPRITE[prayer] + ".png")) {
                if (in == null) {
                    return null;
                }
                BufferedImage png = javax.imageio.ImageIO.read(in);
                double scale = Math.min((INFOBOX - 5) / (double) png.getWidth(), (INFOBOX - 5) / (double) png.getHeight());
                int w = (int) (png.getWidth() * scale);
                int h = (int) (png.getHeight() * scale);
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.drawImage(png, 0, 0, w, h, null);
                g.dispose();
                int[] pixels = scaled.getRGB(0, 0, w, h, null, 0, w);
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = (pixels[i] >>> 24) < 128 ? 0 : pixels[i] | 0xFF000000;
                }
                prayerSprites[prayer] = toolkit.createSprite(w, w, h, pixels);
            } catch (Exception e) {
                return null;
            }
        }
        return prayerSprites[prayer];
    }

    /** BackgroundComponent strokes: RGB x 0.8 outside, x 1.2 inside, alpha x 1.4 (as RspsHud.box). */
    private static int stroke(int argb, double rgb) {
        int a = Math.min(255, (int) (((argb >>> 24) & 0xFF) * 1.4));
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * rgb));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * rgb));
        int b = Math.min(255, (int) ((argb & 0xFF) * rgb));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** PrayerInfoboxOverlay.render at OverlayPosition.BOTTOM_RIGHT of the game view. */
    private static void renderPrayerInfobox(int screenX, int screenY, int width, int height) {
        String mode = get("prayerMode");
        if (!prayerIndicator() || !("ALL".equals(mode) || "INFO_BOX".equals(mode))) {
            return;
        }
        int prayer = nextPrayer();
        if (prayer < 0) {
            return;
        }
        int book = Math.max(0, Math.min(2, RspsPrayerFilters.book()));
        boolean active = RspsPrayerFilters.varbit(PROTECT_VARBIT[book][prayer]) == 1;
        int bg = active ? RspsHud.PANEL_BACKGROUND : COLOR_OFF;
        // OverlayRenderer BOTTOM_RIGHT: the game view's bottom-right corner (BORDER 5); in resizable above the inventory panel
        java.awt.Rectangle side = DeadmanSkullHud.sidePanelBounds();
        int bx = screenX + width - 5 - INFOBOX;
        int by = (side != null ? Math.min(screenY + height, side.y) : screenY + height) - 5 - INFOBOX;
        Toolkit.active.fillRect(bx, by, INFOBOX, INFOBOX, bg);
        Toolkit.active.outlineRect(bx, by, INFOBOX, INFOBOX, stroke(bg, 0.8));
        Toolkit.active.outlineRect(bx + 1, by + 1, INFOBOX - 2, INFOBOX - 2, stroke(bg, 1.2));
        Sprite icon = prayerSprite(prayer);
        if (icon != null) {
            icon.render(bx + (INFOBOX - icon.getWidth()) / 2, by + (INFOBOX - icon.getHeight()) / 2);
        }
    }

    // ---- AkkhaMemoryBlast (boss/akkha/AkkhaMemoryBlast.java) ----------------------------------------------------------------

    // GAME_OBJECT_ID_FIRE/LIGHTNING/STAR/DEATH_SYMBOL 45868..45871 = local locs 66079..66082 (glowing quadrant symbols)
    private static final int LOC_FIRE_SYMBOL = 66079;
    private static final int LOC_LIGHTNING_SYMBOL = 66080;
    private static final int LOC_STAR_SYMBOL = 66081;
    private static final int LOC_DEATH_SYMBOL = 66082;
    /** GRAPHICS_OBJECT_IDS_ELEMENTS 2256, 2257, 2258, 2259. */
    private static final int[] GFX_ELEMENTS = {3331, 3332, 3250, 3333};
    private static final Map<Integer, Long> symbolTile = new HashMap<Integer, Long>();
    private static final java.util.LinkedList<Long> memoryTiles = new java.util.LinkedList<Long>();
    private static final java.util.Set<SpotAnimation> seenElements =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());
    private static int memoryLastTick = -1;
    private static int tickCount;
    private static int memoryLevel;
    /** Akkha's room: the boss may be invisible during Memory Blast, so the room stays 'active' 60 s after Akkha was last seen. */
    private static long akkhaSeenAt;

    private static boolean akkhaRoom() {
        return System.currentTimeMillis() - akkhaSeenAt < 60_000L;
    }

    private static void resetMemoryBlast() {
        symbolTile.clear();
        memoryTiles.clear();
    }

    /** ServerConnectionReader LOC_ADD_CHANGE: AkkhaMemoryBlast.onGameObjectSpawned (a glowing symbol). */
    static void locAdded(int id, int x, int z, int level) {
        if ((id == LOC_SEQUENCE_DISPLAY || id == LOC_SEQUENCE_STEPPED) && on()) {
            sequenceLoc(id, x, z, level);
            return;
        }
        if (id < LOC_FIRE_SYMBOL || id > LOC_DEATH_SYMBOL || !on() || !akkhaRoom()) {
            return;
        }
        Long tile = symbolTile.get(id);
        if (tile == null) {
            // death -1,-1; fire +1,-1; lightning -1,+1; star +1,+1 (from the symbol's tile)
            int dx = id == LOC_DEATH_SYMBOL || id == LOC_LIGHTNING_SYMBOL ? -1 : 1;
            int dz = id == LOC_DEATH_SYMBOL || id == LOC_FIRE_SYMBOL ? -1 : 1;
            tile = key(x + dx, z + dz);
            symbolTile.put(id, tile);
        }
        memoryLevel = level;
        memoryTiles.addLast(tile);
    }

    /** ChatHistory.add (game messages): AkkhaMemoryBlast / AkkhaFinalStand / Akkha.onChatMessage (Text.standardize = lower case). */
    static void chatMessage(int type, String message) {
        if (type != 0 || message == null || !on()) {
            return;
        }
        String m = message.replaceAll("<[^>]*>", "").trim().toLowerCase();
        if (m.equals("sections of the room start to glow...") || m.equals("challenge started: akkha.") || m.endsWith("your party failed")) {
            resetMemoryBlast();
        }
        hetChat(message);
        scabarasChat(message);
        apmekenChat(m);
        if (m.equals("challenge started: akkha.")) {
            akkhaStyle = STYLE_MELEE; // Akkha.onChatMessage: attackStyle = MELEE
        }
    }

    /** AkkhaMemoryBlast.onGraphicsObjectCreated: an element erupts -> the first tile is done (once per tick). */
    private static void scanMemoryElements() {
        java.util.Set<SpotAnimation> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());
        for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
            SpotAnimation s = n.spotAnimation;
            for (int g : GFX_ELEMENTS) {
                if (s.id == g) {
                    now.add(s);
                    if (!seenElements.contains(s) && !memoryTiles.isEmpty() && memoryLastTick != tickCount) {
                        memoryLastTick = tickCount;
                        memoryTiles.removeFirst();
                    }
                }
            }
        }
        seenElements.retainAll(now);
        seenElements.addAll(now);
    }

    /** AkkhaSceneOverlay.renderMemoryBlast: the next safe tile green, the one after in the danger outline colour (fill alpha 20). */
    private static void renderMemoryBlast(int screenX, int screenY) {
        if (!bool("akkhaMemoryBlastTracker") || memoryTiles.isEmpty()) {
            return;
        }
        long first = memoryTiles.getFirst();
        tile(memoryLevel, (int) (first >> 32) * 512 + 256, (int) first * 512 + 256, 1, 0xFF00FF00, 0x1400FF00, screenX, screenY);
        if (memoryTiles.size() > 1) {
            long second = memoryTiles.get(1);
            int c = argb("dangerOutlineColor");
            tile(memoryLevel, (int) (second >> 32) * 512 + 256, (int) second * 512 + 256, 1, c, (c & 0xFFFFFF) | 0x14000000, screenX, screenY);
        }
    }

    // ---- challenge/het/Het.java + HetOverlay.java ---------------------------------------------------------------------------

    private static final int NPC_SEAL_PROTECTED = 15158; // HETS_SEAL_PROTECTED 11706
    private static final int NPC_SEAL_WEAKENED = 15159; // HETS_SEAL_WEAKENED 11707
    private static final int NPC_ORB_OF_DARKNESS = 15160; // ORB_OF_DARKNESS 11708
    private static final int LOC_CASTER_STATUE = 66006; // ObjectID.CASTER_STATUE 45486
    private static final int GFX_ORB_DESPAWN = 379; // GRAPHICS_OBJECT_ID_ORB_DESPAWN (same id in our cache)
    private static final int[] GFX_BEAM = {3381, 3391, 3256}; // BEAM_GRAPHICS_OBJECT_IDS 2114 horizontal, 2064 vertical, 2120 crash
    private static final int BEAM_FIRE_RATE_TICKS = 9;
    private static boolean hetRoom;
    private static int nextFireTick = -1;
    private static int sealId = -1;
    private static int[] casterStatue; // x, z, level (tile)
    private static int casterScanTick = -100;
    private static final java.util.Set<SpotAnimation> seenBeams =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());

    /** GameObjectSpawned for map locs: the first scenery loc with [id] on [level] (Tile.head -> PositionEntity implements Location). */
    private static int[] findLoc(int id, int level) {
        int width = Static720.mapWidth;
        int length = Static501.mapLength;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                Tile tile = Static347.getTile(level, x, z);
                if (tile == null) {
                    continue;
                }
                for (PositionEntityNode n = tile.head; n != null; n = n.node) {
                    if (n.entity instanceof com.jagex.game.Location && ((com.jagex.game.Location) n.entity).getId() == id) {
                        return new int[]{x, z, level};
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- Scabaras (challenge/scabaras)
    /** ScabarasMatchingPuzzle UP_TILE_IDS 45365-45373 / DOWN_TILE_IDS 45356-45364 (memorygame_tile1..9 / _button1..9): ours = OSRS + 20577. */
    private static final int LOC_MATCH_DOWN = 65933, LOC_MATCH_UP = 65942;
    private static int scabScanTick = -1;
    private static int scabTickDone = -1;
    private static boolean scabarasRoom;
    /** up tiles per symbol (0-8) seen this tick, and the first down tile per symbol (x, z). */
    private static final int[] matchUp = new int[9];
    private static final int[][] matchDown = new int[9][];

    /** ScabarasAdditionPuzzle.AdditionTile ground objects 45345-45353 (ours +20577) and the selected game objects 45386-45395 (ours +20568). */
    private static final int LOC_ADDITION = 65922, LOC_ADDITION_SELECTED = 65954;
    /** ScabarasLightPuzzle GROUND_OBJECT_LIGHT_BACKGROUND 45344 / GAME_OBJECT_LIGHT_ENABLED 45384. */
    private static final int LOC_LIGHT_OFF = 65921, LOC_LIGHT_ON = 65952;
    private static final int[] additionTopLeft = {-1, -1};
    private static final int[] lightTopLeft = {-1, -1};
    /** ScabarasAdditionPuzzle.OPTIMAL_SOLUTIONS (target 20..45 -> grid cells, row-major from the top-left, rows going south). */
    private static final int[][] ADDITION_SOLUTIONS = {
        {5, 11, 17}, {10, 11, 17}, {10, 11, 12, 18, 24}, {5, 6, 7, 8, 14}, {5, 11, 17, 23}, {10, 11, 12, 13},
        {9, 10, 11, 12, 13}, {5, 6, 7, 8, 4}, {0, 1, 7, 13, 19}, {10, 11, 12, 13, 19}, {10, 11, 12, 13, 14}, {0, 6, 12, 13, 14},
        {2, 3, 4, 6, 10}, {4, 5, 6, 7, 8, 9, 14}, {10, 11, 12, 13, 14, 19}, {9, 10, 11, 12, 13, 14, 19}, {0, 1, 2, 3, 4, 9, 14},
        {10, 11, 12, 13, 14, 19, 24}, {0, 5, 6, 10, 12, 18, 24}, {2, 3, 4, 7, 10, 11, 12}, {4, 9, 10, 11, 12, 13, 14},
        {0, 4, 6, 9, 12, 13, 14}, {0, 5, 9, 10, 11, 12, 13}, {0, 1, 5, 7, 10, 13, 19}, {0, 5, 10, 11, 14, 17, 18}, {0, 1, 2, 3, 4, 5, 10},
    };
    /** ScabarasLightPuzzle.LIGHTS_PUZZLE_XOR_ARRAY. */
    private static final int[] LIGHTS_XOR = {0x75, 0xBA, 0xCD, 0xCE, 0x73, 0xB3, 0x5D, 0xAE};
    /** ScabarasSequencePuzzle GROUND_OBJECT_ID 45340 / DISPLAY 45341 / STEPPED 45342 (ours 65918-65920), GRAPHICS_OBJECT_RESET 302 (ours 3387). */
    private static final int LOC_SEQUENCE_GROUND = 65918, LOC_SEQUENCE_DISPLAY = 65919, LOC_SEQUENCE_STEPPED = 65920, GFX_SEQUENCE_RESET = 3387;
    /** ScabarasObeliskPuzzle GAME_OBJECT_ID_ENTRANCE 45135 (ours 64590): the north / south barrier; entranceTile = the one to outline. */
    private static final int LOC_SCABARAS_ENTRANCE = 64590;
    private static int entranceX = -1, entranceZ = -1;
    /** SCENE_COORD_STARTS of the light / addition grid in OSRS scene coordinates: (36|53, 56|44); SCENE_X 46 / SCENE_Y 51 split them. */
    private static final int[][] PLUGIN_STARTS = {{36, 56}, {36, 44}, {53, 56}, {53, 44}};

    /**
     * ScabarasObeliskPuzzle.onNpcSpawned (OBELISK_ID_INACTIVE): entranceTile = sceneY >= 51 ? (sceneX >= 46 ? north : south)
     * : (sceneX >= 46 ? south : north). The plugin uses OSRS scene coordinates; ours differ by one offset for the whole room, found from
     * the light and addition grids (always two different SCENE_COORD_STARTS quadrants).
     */
    private static void scabarasEntrance(int level) {
        entranceX = entranceZ = -1;
        if (lightTopLeft[0] < 0 || additionTopLeft[0] < 0) {
            return;
        }
        int dx = Integer.MIN_VALUE, dz = 0;
        for (int[] l : PLUGIN_STARTS) {
            for (int[] a : PLUGIN_STARTS) {
                if (l != a && additionTopLeft[0] - lightTopLeft[0] == a[0] - l[0] && additionTopLeft[1] - lightTopLeft[1] == a[1] - l[1]) {
                    dx = lightTopLeft[0] - l[0];
                    dz = lightTopLeft[1] - l[1];
                }
            }
        }
        if (dx == Integer.MIN_VALUE) {
            return;
        }
        NPCEntity obelisk = null;
        for (int i = 0; i < NPCList.size && obelisk == null; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            int id = node == null ? -1 : typeId(node.npc);
            if (id == NPC_OBELISK_INACTIVE || id == NPC_OBELISK_ACTIVE) {
                obelisk = node.npc;
            }
        }
        if (obelisk == null) {
            return;
        }
        boolean north = (obelisk.pathZ[0] - dz >= 51) == (obelisk.pathX[0] - dx >= 46);
        for (int x = 0; x < Static720.mapWidth; x++) {
            for (int z = 0; z < Static501.mapLength; z++) {
                if (hasLoc(level, x, z, LOC_SCABARAS_ENTRANCE, 1) && (z - dz >= 51) == north) {
                    entranceX = x;
                    entranceZ = z;
                    return;
                }
            }
        }
    }

    /** ScabarasObeliskPuzzle OBELISK_ID_INACTIVE 11698 / ACTIVE 11699 (ours 15150 / 15151). */
    private static final int NPC_OBELISK_INACTIVE = 15150, NPC_OBELISK_ACTIVE = 15151;
    /** ScabarasObeliskPuzzle GRAPHICS_OBJECT_ID_FALLING_ROCKS 317 GA_BEAST_ROCK_FALL (ours 3396, OsrsFxImportTool toa-scabaras-rocks). */
    private static final int GFX_FALLING_ROCKS = 3396;
    /** ScabarasSequencePuzzle.points (EvictingQueue of 5 tiles, packed x << 16 | z) + completedTiles / puzzleFinished / lastDisplayTick. */
    private static final java.util.ArrayDeque<Integer> sequencePoints = new java.util.ArrayDeque<Integer>();
    private static int sequenceCompleted;
    private static boolean sequenceFinished;
    private static int sequenceLastDisplayTick;
    private static int sequenceLevel;
    /** ScabarasObeliskPuzzle.obeliskOrder (packed tiles) + activeObelisks; obeliskSeen = last id per obelisk npc (NpcChanged). */
    private static final java.util.ArrayList<Integer> obeliskOrder = new java.util.ArrayList<Integer>();
    private static int activeObelisks;
    private static final java.util.Map<NPCEntity, Integer> obeliskSeen = new java.util.IdentityHashMap<NPCEntity, Integer>();
    private static final java.util.Set<SpotAnimation> seenResetSpots = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());

    /** ScabarasSequencePuzzle.reset. */
    private static void sequenceReset() {
        sequenceFinished = false;
        sequencePoints.clear();
        sequenceCompleted = 0;
        sequenceLastDisplayTick = 0;
    }

    /** ScabarasSequencePuzzle.onGameObjectSpawned. */
    private static void sequenceLoc(int id, int x, int z, int level) {
        if (sequenceFinished) {
            return;
        }
        if (id == LOC_SEQUENCE_DISPLAY) {
            // two displays on the same tick = the whole sequence shown = solved
            if (sequenceLastDisplayTick == (sequenceLastDisplayTick = tickCount)) {
                sequenceReset();
                sequenceFinished = true;
                return;
            }
            if (sequencePoints.size() == 5) {
                sequencePoints.removeFirst();
            }
            sequencePoints.addLast((x << 16) | z);
            sequenceLevel = level;
            sequenceCompleted = 0;
        } else {
            sequenceCompleted++;
        }
    }

    /** onGraphicsObjectCreated (sequence reset 302 on a sequence tile) + onNpcChanged (obelisks), once per tick. */
    private static void scabarasTick(int level) {
        java.util.Set<SpotAnimation> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());
        for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
            SpotAnimation sp = n.spotAnimation;
            if (sp.id != GFX_SEQUENCE_RESET) {
                continue;
            }
            now.add(sp);
            if (seenResetSpots.contains(sp)) {
                continue;
            }
            Tile t = Static347.getTile(sp.level, sp.x >> 9, sp.z >> 9);
            if (t != null && t.groundDecor instanceof com.jagex.game.Location && ((com.jagex.game.Location) t.groundDecor).getId() == LOC_SEQUENCE_GROUND) {
                sequenceReset();
            }
        }
        seenResetSpots.retainAll(now);
        seenResetSpots.addAll(now);
        java.util.Set<NPCEntity> loaded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<NPCEntity, Boolean>());
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            int id = node == null ? -1 : typeId(node.npc);
            if (id != NPC_OBELISK_ACTIVE && id != NPC_OBELISK_INACTIVE) {
                continue;
            }
            loaded.add(node.npc);
            Integer before = obeliskSeen.put(node.npc, id);
            if (before == null || before == id) {
                if (before == null) {
                    AvTrace.log("[toaext] obelisk gezien id=" + id + " tegel=" + node.npc.pathX[0] + "," + node.npc.pathZ[0]);
                }
                continue;
            }
            AvTrace.log("[toaext] obelisk " + before + " -> " + id + " volgorde=" + obeliskOrder.size() + " actief=" + activeObelisks);
            if (id == NPC_OBELISK_ACTIVE) {
                int tile = (node.npc.pathX[0] << 16) | node.npc.pathZ[0];
                if (!obeliskOrder.contains(tile)) {
                    obeliskOrder.add(tile);
                }
                activeObelisks++;
            } else {
                activeObelisks = 0;
            }
        }
        obeliskSeen.keySet().retainAll(loaded);
    }

    /** ScabarasOverlay.renderLocalSequence: done = gray, else colorLerp(CYAN, BLUE, ix / 5.0); number in the tile, gray once done. */
    private static void renderSequence(int level, java.util.Collection<Integer> points, int progress, int screenX, int screenY) {
        int ix = 0;
        for (int packed : points) {
            int tx = packed >>> 16;
            int tz = packed & 0xFFFF;
            int c;
            if (ix < progress) {
                c = 0xFF808080; // Color.gray
            } else {
                double t = ix / 5.0; // ColorUtil.colorLerp(CYAN 0,255,255 -> BLUE 0,0,255)
                c = 0xFF000000 | (int) Math.round(255 - 255 * t) << 8 | 0xFF;
            }
            overlayTile(level, tx, tz, c, screenX, screenY);
            ix++;
            int[] centre = project(level, tx * 512 + 256, tz * 512 + 256, screenX, screenY);
            if (centre != null) {
                String text = Integer.toString(ix);
                int w = RspsHud.width(RspsHud.metrics(), text);
                RspsHud.text(RspsHud.font(), text, centre[0] - w / 2, centre[1], ix <= progress ? 0xFF808080 : 0xFFFFFFFF, false);
            }
        }
    }

    /** ScabarasAdditionPuzzle.TARGET_NUMBER_PATTERN. */
    private static final java.util.regex.Pattern TARGET_NUMBER = java.util.regex.Pattern.compile("The number (\\d+) has been hastily chipped into the stone.");
    /** ScabarasAdditionPuzzle.targetNumber (0 = unknown / party failed). */
    private static int additionTarget;

    private static boolean hasLoc(int level, int x, int z, int from, int count) {
        Tile tile = Static347.getTile(level, x, z);
        if (tile == null) {
            return false;
        }
        for (PositionEntityNode n = tile.head; n != null; n = n.node) {
            if (n.entity instanceof com.jagex.game.Location) {
                int id = ((com.jagex.game.Location) n.entity).getId();
                if (id >= from && id < from + count) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ScabarasAdditionPuzzle.onChatMessage (Text.removeTags). */
    private static void scabarasChat(String message) {
        String m = message.replaceAll("<[^>]*>", "");
        if (m.startsWith("Your party failed to complete the challenge")) {
            additionTarget = 0;
            activeObelisks = 0;
            obeliskOrder.clear();
            sequenceReset();
            return;
        }
        java.util.regex.Matcher matcher = TARGET_NUMBER.matcher(m);
        if (matcher.matches()) {
            additionTarget = Integer.parseInt(matcher.group(1));
        }
    }

    /** OverlayUtil.renderPolygon(graphics, tilePoly, colour): stroke 2 in the colour, fill black at alpha 50. */
    private static void overlayTile(int level, int tx, int tz, int colour, int screenX, int screenY) {
        int cx = tx * 512 + 256;
        int cz = tz * 512 + 256;
        tile(level, cx, cz, 1, colour, 0x32000000, screenX, screenY);
        tile(level, cx + 4, cz + 4, 1, colour, 0, screenX, screenY);
    }

    /** GroundObjectSpawned / Despawned, once per tick: the matching-puzzle tiles currently in the scene (ground decorations). */
    private static void scanScabaras(int level) {
        if (scabScanTick == tickCount) {
            return;
        }
        scabScanTick = tickCount;
        java.util.Arrays.fill(matchUp, 0);
        java.util.Arrays.fill(matchDown, null);
        additionTopLeft[0] = additionTopLeft[1] = lightTopLeft[0] = lightTopLeft[1] = -1;
        boolean found = false;
        for (int x = 0; x < Static720.mapWidth; x++) {
            for (int z = 0; z < Static501.mapLength; z++) {
                Tile tile = Static347.getTile(level, x, z);
                if (tile == null || !(tile.groundDecor instanceof com.jagex.game.Location)) {
                    continue;
                }
                int id = ((com.jagex.game.Location) tile.groundDecor).getId();
                if (id >= LOC_ADDITION && id < LOC_ADDITION + 9 || id == LOC_LIGHT_OFF) {
                    // the top-left (north-west) tile of the 5x5 number grid / the 3x3 light grid (findStartTile)
                    int[] tl = id == LOC_LIGHT_OFF ? lightTopLeft : additionTopLeft;
                    if (tl[0] < 0 || x < tl[0] || x == tl[0] && z > tl[1]) {
                        tl[0] = x;
                        tl[1] = z;
                    }
                    found = true;
                } else if (id >= LOC_MATCH_UP && id < LOC_MATCH_UP + 9) {
                    matchUp[id - LOC_MATCH_UP]++;
                    found = true;
                } else if (id >= LOC_MATCH_DOWN && id < LOC_MATCH_DOWN + 9) {
                    if (matchDown[id - LOC_MATCH_DOWN] == null) {
                        matchDown[id - LOC_MATCH_DOWN] = new int[] {x, z};
                    }
                    found = true;
                }
            }
        }
        if (found != scabarasRoom) {
            AvTrace.log("[toaext] scabaras matching tegels=" + found);
        }
        scabarasRoom = found;
        // trace (C:\RSPS\trace.on): what the puzzles read, once per change
        StringBuilder sig = new StringBuilder("up=");
        for (int i = 0; i < 9; i++) {
            sig.append(matchUp[i]);
        }
        sig.append(" down=");
        for (int i = 0; i < 9; i++) {
            sig.append(matchDown[i] == null ? '-' : '+');
        }
        sig.append(" add=").append(additionTopLeft[0]).append(',').append(additionTopLeft[1]).append(" target=").append(additionTarget)
            .append(" light=").append(lightTopLeft[0]).append(',').append(lightTopLeft[1]);
        if (found && !sig.toString().equals(scabSig)) {
            scabSig = sig.toString();
            AvTrace.log("[toaext] scabaras " + scabSig);
        }
    }

    private static String scabSig = "";

    /** ScabarasOverlay.renderMatchingTiles: for every up symbol without its pair, the down tile of that symbol in RED (1 px, no fill). */
    private static void renderScabaras(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        boolean addition = bool("scabarasAdditionPuzzle");
        boolean lights = bool("scabarasLightPuzzle");
        boolean matching = bool("scabarasMatchingPuzzle");
        boolean sequence = bool("scabarasSequencePuzzle");
        boolean obelisk = bool("scabarasObeliskPuzzle");
        if (!addition && !lights && !matching && !sequence && !obelisk) {
            return;
        }
        scanScabaras(level);
        if (!scabarasRoom) {
            return;
        }
        if (scabTickDone != tickCount) {
            scabTickDone = tickCount;
            scabarasTick(level);
            scabarasEntrance(level);
        }
        if (obelisk && bool("scabarasObeliskFallingRocksHighlight")) {
            // ScabarasObeliskPuzzle.fallingRocks (GraphicsObjectCreated 317, ours 3396, until finished) -> ScabarasOverlay.renderFallingRocks:
            // drawOutlineAndFill(dangerOutlineColor, dangerFillColor, 1) on the rock's tile
            int outline = argb("dangerOutlineColor");
            int fill = argb("dangerFillColor");
            for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
                SpotAnimation sp = n.spotAnimation;
                if (sp.id == GFX_FALLING_ROCKS && sp.level == level) {
                    tile(level, (sp.x >> 9) * 512 + 256, (sp.z >> 9) * 512 + 256, 1, outline, fill, screenX, screenY);
                }
            }
        }
        if (obelisk) {
            renderSequence(level, obeliskOrder, activeObelisks, screenX, screenY);
        }
        if (sequence && sequenceLevel == level) {
            renderSequence(level, sequencePoints, sequenceCompleted, screenX, screenY);
        }
        int danger = argb("dangerOutlineColor");
        if (addition && additionTarget >= 20 && additionTarget <= 45 && additionTopLeft[0] >= 0) {
            // ScabarasAdditionPuzzle.solve: flips = OPTIMAL_SOLUTIONS[target] minus the cells that already carry a selected game object
            for (int cell : ADDITION_SOLUTIONS[additionTarget - 20]) {
                int x = additionTopLeft[0] + cell % 5;
                int z = additionTopLeft[1] - cell / 5;
                if (!hasLoc(level, x, z, LOC_ADDITION_SELECTED, 10)) {
                    overlayTile(level, x, z, danger, screenX, screenY);
                }
            }
        }
        if (lights && lightTopLeft[0] >= 0) {
            // ScabarasLightPuzzle.readTileStates + findSolution: 8 lights (3x3 every 2 tiles, middle skipped), xor of the dark ones
            int xor = 0;
            for (int i = 0; i < 8; i++) {
                int ix = i > 3 ? i + 1 : i;
                if (!hasLoc(level, lightTopLeft[0] + ix % 3 * 2, lightTopLeft[1] - ix / 3 * 2, LOC_LIGHT_ON, 1)) {
                    xor ^= LIGHTS_XOR[i];
                }
            }
            for (int i = 0; i < 8; i++) {
                if ((xor & (1 << i)) != 0) {
                    int ix = i > 3 ? i + 1 : i;
                    overlayTile(level, lightTopLeft[0] + ix % 3 * 2, lightTopLeft[1] - ix / 3 * 2, danger, screenX, screenY);
                }
            }
        }
        if (!matching) {
            return;
        }
        for (int symbol = 0; symbol < 9; symbol++) {
            // upTiles: a symbol is added on its first flip and removed on the second (the pair matched) = exactly one up tile
            if (matchUp[symbol] == 1 && matchDown[symbol] != null) {
                int[] t = matchDown[symbol];
                tile(level, t[0] * 512 + 256, t[1] * 512 + 256, 1, 0xFFFF0000, 0, screenX, screenY);
            }
        }
    }

    /** Het.onChatMessage: the challenge start / complete game messages (exact text, no standardize). */
    private static void hetChat(String message) {
        String m = message.replaceAll("<[^>]*>", "");
        if (m.equals("Challenge started: Path of Het.")) {
            nextFireTick = tickCount + BEAM_FIRE_RATE_TICKS + 1;
        } else if (m.equals("Challenge complete: Path of Het.")) {
            nextFireTick = -1;
        }
    }

    /** Het.onNpcChanged (seal weakened <-> protected) + onGraphicsObjectCreated (a beam fired), once per tick. */
    private static void hetTick() {
        int seal = -1;
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            int id = node == null ? -1 : typeId(node.npc);
            if (id == NPC_SEAL_PROTECTED || id == NPC_SEAL_WEAKENED) {
                seal = id;
            }
        }
        if (sealId == NPC_SEAL_WEAKENED && seal == NPC_SEAL_PROTECTED) {
            nextFireTick = tickCount + BEAM_FIRE_RATE_TICKS + 1;
        } else if (sealId == NPC_SEAL_PROTECTED && seal == NPC_SEAL_WEAKENED) {
            nextFireTick = -1;
        }
        sealId = seal;
        hetRoom = seal != -1;
        java.util.Set<SpotAnimation> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());
        for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
            SpotAnimation s = n.spotAnimation;
            for (int b : GFX_BEAM) {
                if (s.id == b) {
                    now.add(s);
                    if (!seenBeams.contains(s)) {
                        nextFireTick = tickCount + BEAM_FIRE_RATE_TICKS;
                    }
                }
            }
        }
        seenBeams.retainAll(now);
        seenBeams.addAll(now);
        if (!hetRoom) {
            casterStatue = null;
        } else if (casterStatue == null && tickCount - casterScanTick > 5 && PlayerEntity.self != null) {
            casterScanTick = tickCount;
            casterStatue = findLoc(LOC_CASTER_STATUE, PlayerEntity.self.level);
        }
    }

    /** Het.shouldDraw (hetHideOrbsOfDarkness): the orbs and their despawn graphic. */
    private static boolean hetHidden(Object entity) {
        if (!bool("hetHideOrbsOfDarkness")) {
            return false;
        }
        if (entity instanceof NPCEntity) {
            return typeId(entity) == NPC_ORB_OF_DARKNESS;
        }
        return entity instanceof SpotAnimation && ((SpotAnimation) entity).id == GFX_ORB_DESPAWN;
    }

    /** Het.npcHighlight (orb tiles) + HetOverlay (caster statue beam timer: ProgressPieComponent, diameter 25). */
    private static void renderHet(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        if (bool("hetOrbOfDarknessTiles")) {
            for (int i = 0; i < NPCList.size; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                if (node != null && node.npc.level == level && typeId(node.npc) == NPC_ORB_OF_DARKNESS) {
                    npcTile(node.npc, type(node.npc), false, argb("dangerOutlineColor"), argb("dangerFillColor"), screenX, screenY);
                }
            }
        }
        if (bool("hetCasterStatueBeamTimer") && casterStatue != null) {
            double progress = (double) (nextFireTick - tickCount) / BEAM_FIRE_RATE_TICKS;
            if (progress < 0) {
                return;
            }
            int colour = progress == 0 ? 0xFF00FF00 : 0xFF00FFFF; // Color.green / Color.cyan
            // Perspective.localToCanvas(statue local location, plane): its centre at ground height
            Static418.method7860(casterStatue[2], casterStatue[1] * 512 + 256, 0, casterStatue[0] * 512 + 256, 0);
            if (OverlayManager.hitmarkpos[0] < 0) {
                return;
            }
            pie(screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1], 25, 1 - progress, colour);
        }
    }

    /** ProgressPieComponent: Arc2D from 90 degrees, -360 x progress (clockwise), filled, then the circle outline in the border colour. */
    private static void pie(int cx, int cy, int diameter, double progress, int colour) {
        int r = diameter / 2;
        double sweep = 360.0 * progress;
        for (int dy = -r; dy <= r; dy++) {
            int x0 = Integer.MIN_VALUE;
            for (int dx = -r; dx <= r + 1; dx++) {
                boolean in = false;
                if (dx <= r && dx * dx + dy * dy <= r * r) {
                    double a = Math.toDegrees(Math.atan2(-dy, dx)); // screen y down; 0 = east, 90 = north
                    double fromTop = (90.0 - a + 360.0) % 360.0; // clockwise from north
                    in = fromTop <= sweep;
                }
                if (in && x0 == Integer.MIN_VALUE) {
                    x0 = dx;
                } else if (!in && x0 != Integer.MIN_VALUE) {
                    Toolkit.active.fillRect(cx + x0, cy + dy, dx - x0, 1, colour);
                    x0 = Integer.MIN_VALUE;
                }
            }
        }
        // outline: the circle in 64 segments
        int px = cx + r;
        int py = cy;
        for (int i = 1; i <= 64; i++) {
            double t = Math.PI * 2 * i / 64;
            int nx = cx + (int) Math.round(r * Math.cos(t));
            int ny = cy + (int) Math.round(r * Math.sin(t));
            Toolkit.active.line(px, py, nx, ny, colour, 1);
            px = nx;
            py = ny;
        }
    }

    // ---- ModelOutlineRenderer requests (RspsModelOutline) ---------------------------------------------------------------------

    /** KephriSceneOverlay SPOT_ANIM_ID_DUNG 2146 (ours 3341): the player is the target of Kephri's dung attack. */
    private static final int SPOT_DUNG = 3341;

    /** client.getLocalPlayer().hasSpotAnim(SPOT_ANIM_ID_DUNG). */
    private static boolean dungOnSelf() {
        PlayerEntity self = PlayerEntity.self;
        if (self == null || self.spotAnims == null) {
            return false;
        }
        for (EntitySpotAnimation s : self.spotAnims) {
            if (s != null && s.id == SPOT_DUNG) {
                return true;
            }
        }
        return false;
    }

    /** KephriSceneOverlay.renderDungOutline: modelOutlineRenderer.drawOutline(localPlayer, 2, COLOR_SPEC_ATK, 4). */
    static RspsModelOutline.Request outlineForPlayer(PlayerEntity player) {
        if (player != PlayerEntity.self || !on() || kephri == null || !bool("kephriDungOutline") || !dungOnSelf()) {
            return null;
        }
        return new RspsModelOutline.Request(COLOR_SPEC_ATK, 2, 4, 0);
    }

    /** ToaUtils.COLOR_SPEC_ATK (255, 221, 56). */
    private static final int COLOR_SPEC_ATK = 0xFFFFDD38;
    private static final int SEQ_BABA_ROCK_THROW = 16446; // BabaSceneOverlay ANIMATION_ID_ROCK_THROW 9744
    private static final int SEQ_AKKHA_MEMORY_BLAST = 16343; // AkkhaSceneOverlay ANIM_ID_MEMORY_BLAST 9777
    private static final int SEQ_AKKHA_TRAILING_ORBS = 16344; // ANIM_ID_TRAILING_ORBS 9778
    private static final int NPC_JUG = 15163; // JUG 11735
    private static final int NPC_ZEBAK_BOULDER = 15165; // BOULDER_11737
    private static final int NPC_BABA_BABOON = 15117; // BABOON 11781
    private static final int NPC_SOLDIER_SCARAB = 15120; // SOLDIER_SCARAB 11724
    private static final int NPC_SPITTING_SCARAB = 15121; // SPITTING_SCARAB 11725
    private static final int NPC_ARCANE_SCARAB = 15122; // ARCANE_SCARAB 11726

    private static RspsModelOutline.Request outline(int colour, int width, int feather) {
        return new RspsModelOutline.Request(colour, width, feather, 0);
    }

    /** ToaUtils.renderSpecialAttackOutline: outline width 4 feather 4 + the convex hull filled at alpha 60. */
    private static RspsModelOutline.Request special(int colour) {
        return new RspsModelOutline.Request(colour, 4, 4, 0x3C000000 | (colour & 0xFFFFFF));
    }

    private static int anim(NPCEntity npc) {
        return npc.actionAnimations == null ? -1 : npc.actionAnimations[0];
    }

    /**
     * Which outline an npc gets this frame (null = none): HighlightedNpc.outline (borderWidth 1, feather 0) of Zebak / Kephri,
     * BabaSceneOverlay.renderBaboonOutline (1, GREEN, 0), KephriSceneOverlay.renderScarabSwarms (1, WHITE, 0) and the special
     * attack outlines of Ba-Ba (rock throw) and Akkha (memory blast; trailing orbs in black).
     */
    static RspsModelOutline.Request outlineFor(NPCEntity npc) {
        if (!on() || npc == null || !npc.hasType()) {
            return null;
        }
        int id = typeId(npc);
        if (zebak != null) {
            if (id == NPC_JUG && bool("zebakJugOutline")) {
                return outline(0xFF00FFFF, 1, 0); // Color.CYAN
            }
            if (id == NPC_ZEBAK_BOULDER && bool("zebakBoulderOutline")) {
                return outline(0xFF00FF00, 1, 0); // Color.GREEN
            }
            return null;
        }
        if (baba != null) {
            if (npc == baba && bool("babaBabaSpecOutline") && anim(npc) == SEQ_BABA_ROCK_THROW) {
                return special(COLOR_SPEC_ATK);
            }
            if (id == NPC_BABA_BABOON && bool("babaBaboonOutline")) {
                return outline(0xFF00FF00, 1, 0);
            }
            return null;
        }
        if (kephri != null) {
            if (npc == kephri && bool("kephriDungOutline") && dungOnSelf()) {
                return special(COLOR_SPEC_ATK); // KephriSceneOverlay.renderDungOutline: ToaUtils.renderSpecialAttackOutline(kephri)
            }
            if (bool("kephriOverlordOutline")) {
                if (id == NPC_SOLDIER_SCARAB) {
                    return outline(0xFFFF0000, 1, 0);
                }
                if (id == NPC_SPITTING_SCARAB) {
                    return outline(0xFF00FF00, 1, 0);
                }
                if (id == NPC_ARCANE_SCARAB) {
                    return outline(0xFF0000FF, 1, 0);
                }
            }
            if (id == NPC_SCARAB_SWARM && bool("kephriScarabSwarmOutline")) {
                NPCType kt = type(kephri);
                boolean atKephri = kt != null && inMeleeDistance(kephri, kt, npc.pathX[0], npc.pathZ[0]);
                if (!atKephri && anim(npc) != SEQ_SCARAB_SWARM_DEATH) {
                    return outline(0xFFFFFFFF, 1, 0);
                }
            }
            return null;
        }
        if (apmekenRoom && bool("apmekenBaboonOutline")) {
            int colour = apmekenOutlineColour(id);
            return colour == 0 ? null : outline(colour, 1, 0);
        }
        if (akkha != null && npc == akkha && bool("akkhaSpecialAttackOutline")) {
            int a = anim(npc);
            if (a == SEQ_AKKHA_TRAILING_ORBS) {
                return special(0xFF000000); // Color.BLACK
            }
            if (a == SEQ_AKKHA_MEMORY_BLAST) {
                return special(COLOR_SPEC_ATK);
            }
        }
        return null;
    }

    // ---- challenge/apmeken/Apmeken.java + ApmekenOverlay.java (sense / roof-vent outline + Repair swap: still to build) -------

    // BABOON_BRAWLER 11709/11712, BABOON_MAGE 11711/11714, BABOON_THROWER 11710/11713, SHAMAN 11715, VOLATILE 11716, CURSED 11717, THRALL 11718
    private static final int NPC_BRAWLER = 15110, NPC_BRAWLER_2 = 15213, NPC_MAGE = 15112, NPC_MAGE_2 = 15215, NPC_THROWER = 15111,
        NPC_THROWER_2 = 15214, NPC_SHAMAN = 15113, NPC_VOLATILE = 15114, NPC_CURSED = 15115, NPC_THRALL = 15116;
    private static final int SEQ_VOLATILE_BABOON = 16458; // ApmekenOverlay ANIMATION_ID_VOLATILE_BABOON 9756
    private static boolean apmekenRoom;

    private static boolean apmekenBaboon(int id) {
        return id == NPC_BRAWLER || id == NPC_BRAWLER_2 || id == NPC_MAGE || id == NPC_MAGE_2 || id == NPC_THROWER || id == NPC_THROWER_2
            || id == NPC_SHAMAN || id == NPC_VOLATILE || id == NPC_CURSED || id == NPC_THRALL;
    }

    /** ApmekenOverlay.renderBaboonOutline colours (volatile + thrall: none). */
    private static int apmekenOutlineColour(int id) {
        if (id == NPC_BRAWLER || id == NPC_BRAWLER_2) {
            return 0xFFFF0000;
        }
        if (id == NPC_MAGE || id == NPC_MAGE_2) {
            return 0xFF0000FF;
        }
        if (id == NPC_THROWER || id == NPC_THROWER_2) {
            return 0xFF00FF00;
        }
        if (id == NPC_SHAMAN) {
            return 0xFF00FFFF;
        }
        return id == NPC_CURSED ? 0xFFFF00FF : 0;
    }

    /** ApmekenOverlay.renderVolatileBaboonTiles: 3x3 around each volatile baboon, red while it explodes (9756), else orange. */
    private static void renderApmeken(int screenX, int screenY) {
        if (!bool("apmekenVolatileBaboonTiles")) {
            return;
        }
        int level = PlayerEntity.self.level;
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || node.npc.level != level || typeId(node.npc) != NPC_VOLATILE) {
                continue;
            }
            int rgb = anim(node.npc) == SEQ_VOLATILE_BABOON ? 0xFF0000 : 0xFFC800;
            tile(node.npc.level, node.npc.x, node.npc.z, 3, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
        }
    }

    // ---- MenuEntry.setDeprioritized: QuickProceedSwaps + Apmeken 'Repair' (MiniMenu hooks; action + 2000 = below 'Walk here') ----

    /** QuickProceedSwaps.NPC_IDS: Osmumten 11689 / 11690 / 11693. */
    private static final int[] QP_NPCS = {15142, 15143, 15146};
    /** QuickProceedSwaps.OBJECT_IDS: paths, barrier 45135, teleport crystals, room entries. */
    private static final int[] QP_LOCS = {65274, 65272, 65275, 65273, 64590, 64754, 64755, 65041, 64946, 64588, 64686, 64698, 64751, 66126};
    private static final int LOC_BARRIER = 64590; // BARRIER_45135
    /** Apmeken ROOF_SUPPORT 45494 / 45495, STATUE_45496, vents NULL_45499 (ground objects); Het MIRROR_45455 (ours: ids.tsv). */
    private static final int LOC_ROOF_SUPPORT = 64746, LOC_ROOF_SUPPORT_2 = 66013, LOC_APMEKEN_STATUE = 64747, LOC_VENT = 64750, LOC_MIRROR = 65976;

    /**
     * ModelOutlineRenderer.drawOutline(TileObject) of ApmekenOverlay.renderRoofVentOutline (sense VENTS: the vents, ROOF: the roof
     * supports, plus the statue; 2 px MAGENTA feather 4) and HetOverlay (mirrors you can pick up, 1 px MAGENTA). Null = no outline.
     */
    static RspsModelOutline.Request outlineForLoc(int id, int tileX, int tileZ) {
        if (!on()) {
            return null;
        }
        if (id == LOC_SCABARAS_ENTRANCE) {
            // ScabarasOverlay.renderEntranceTile: drawOutline(entranceTile, 1, GREEN, 1)
            return entranceZ >= 0 && tileZ == entranceZ && tileX == entranceX && bool("scabarasHighlightEntrance")
                && bool("scabarasObeliskPuzzle") ? new RspsModelOutline.Request(0xFF00FF00, 1, 1, 0) : null;
        }
        if (id == LOC_MIRROR) {
            return bool("hetMirrorOutline") ? new RspsModelOutline.Request(0xFFFF00FF, 1, 0, 0) : null;
        }
        if (apmekenSense == 0 || !bool("apmekenRoofVentOutline")) {
            return null;
        }
        if (id == LOC_APMEKEN_STATUE || apmekenSense == 1 && id == LOC_VENT
            || apmekenSense == 2 && (id == LOC_ROOF_SUPPORT || id == LOC_ROOF_SUPPORT_2)) {
            return new RspsModelOutline.Request(0xFFFF00FF, 2, 4, 0);
        }
        return null;
    }

    /** The locs ToA Extended can outline: their static models must keep their vertices (StaticLocation / StaticGroundDecor). */
    static boolean keepsVertices(int id) {
        return id == LOC_MIRROR || id == LOC_ROOF_SUPPORT || id == LOC_ROOF_SUPPORT_2 || id == LOC_APMEKEN_STATUE || id == LOC_VENT
            || id == LOC_SCABARAS_ENTRANCE;
    }

    /** Apmeken.sense: 0 NONE, 1 VENTS, 2 ROOF. */
    private static int apmekenSense;

    /** QuickProceedSwaps.shouldDeprioritize + Apmeken.onMenuEntryAdded; [op] = 0 for the FIRST option. */
    static int menuAction(int action, int op, int locId, int npcId, String option, String target) {
        if (!on() || option == null || action >= 2000) {
            return action;
        }
        String qp = get("quickProceedSwaps");
        if (!"OFF".equals(qp) && op == 0) {
            if (npcId >= 0 && option.equals("Talk-to")) {
                for (int id : QP_NPCS) {
                    if (id == npcId) {
                        return action + 2000;
                    }
                }
            }
            if (locId >= 0) {
                if (locId == LOC_BARRIER && option.equals("Pass") && crondisRoom()) {
                    return "SPEEDRUN".equals(qp) ? action : action + 2000;
                }
                if (option.equals("Enter") || option.equals("Use") || option.equals("Pass")) {
                    for (int id : QP_LOCS) {
                        if (id == locId) {
                            return action + 2000;
                        }
                    }
                }
            }
        }
        if (bool("hetDepositPickaxe") && hasPickaxe() && target != null
            && ((option.equals("Take-pickaxe") && target.contains("Statue")) || (option.contains("Enter") && target.contains("Entry")))) {
            return action + 2000; // Het: a pickaxe in the inventory or worn -> deposit / take is no longer the left click
        }
        if (bool("apmekenRepairMenuEntry") && apmekenSense != 2 && option.equals("Repair")) {
            return action + 2000;
        }
        return action;
    }

    /**
     * Het.PICKAXE_IDS mapped to our items: bronze..rune 1265-1275, dragon 15259 / 23807, dragon (or) 23810, 3rd age 23620
     * (infernal and crystal pickaxes do not exist in our cache).
     */
    private static final int[] PICKAXE_IDS = {1265, 1267, 1269, 1271, 1273, 1275, 15259, 23807, 23810, 23620};

    /** containsAny(inventory 93) || containsAny(equipment 94). */
    private static boolean hasPickaxe() {
        for (int id : PICKAXE_IDS) {
            if (ClientInventory.total(93, id, false) > 0 || ClientInventory.total(94, id, false) > 0) {
                return true;
            }
        }
        return false;
    }

    /** RaidRoom.CRONDIS (the barrier sits in the Crondis puzzle): Zebak's water-path room has the crondis puzzle palm. */
    private static boolean crondisRoom() {
        return crondisSeenAt > 0 && System.currentTimeMillis() - crondisSeenAt < 60_000L;
    }

    private static long crondisSeenAt;

    /** Apmeken.onChatMessage (standardized). */
    private static void apmekenChat(String m) {
        if (m.startsWith("challenge complete") || m.startsWith("you have died")) {
            apmekenSense = 0;
        } else if (m.equals("you repair the damaged roof support.") || m.equals("you neutralise the fumes coming from the hole.")
            || m.equals("damaged roof supports cause some debris to fall on you!") || m.equals("the fumes filling the room suddenly ignite!")) {
            apmekenSense = 0;
        } else if (m.equals("you sense some strange fumes coming from holes in the floor.")) {
            apmekenSense = 1;
        } else if (m.equals("you sense an issue with the roof supports.")) {
            apmekenSense = 2;
        }
        if (m.equals("challenge started: path of crondis.")) {
            crondisSeenAt = System.currentTimeMillis();
        } else if (m.equals("challenge complete: path of crondis.")) {
            crondisSeenAt = 0;
        }
    }

    /** Kephri.shouldDraw. */
    private static boolean kephriHidden(Object entity) {
        if (entity instanceof NPCEntity) {
            NPCEntity npc = (NPCEntity) entity;
            int id = typeId(npc);
            if (id == NPC_SCARAB_SWARM) {
                if (!bool("kephriHideUnattackableScarabSwarm")) {
                    return false;
                }
                if (npc.actionAnimations != null && npc.actionAnimations[0] == SEQ_SCARAB_SWARM_DEATH) {
                    return true;
                }
                NPCType kt = type(kephri);
                return kt != null && inMeleeDistance(kephri, kt, npc.pathX[0], npc.pathZ[0]);
            }
            return id == NPC_AGILE_SCARAB && bool("kephriHideAgileScarabNpc");
        }
        if (entity instanceof ProjectileAnimation) {
            int id = ((ProjectileAnimation) entity).id;
            if (id == PROJ_FIREBALL || id == PROJ_PRE_FIREBALL) {
                return bool("kephriHideFireballProjectile");
            }
            if (id == PROJ_AGILE_SCARAB) {
                return bool("kephriHideAgileScarabProjectile");
            }
            return id == PROJ_BOMBER_SCARAB && bool("kephriHideBomberScarabProjectile");
        }
        return false;
    }

    /** KephriSceneOverlay.render (dung / swarm / overlord outlines: model outline, still to build). */
    private static void renderKephri(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        // render() also sees an egg first (NpcSpawned): 14 = RuneLite's value after the GameTick of its spawn tick
        if (bool("kephriEggTiles")) {
            for (int i = 0; i < NPCList.size; i++) {
                NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
                if (node != null && typeId(node.npc) == NPC_EGG && !eggs.containsKey(node.npc)) {
                    eggs.put(node.npc, TICK_COUNT_EGG - 1);
                }
            }
            for (Map.Entry<NPCEntity, Integer> e : eggs.entrySet()) {
                // renderEggs: red <= 2, orange <= 7, yellow; getCanvasTileAreaPoly(localLocation, eggRadius)
                int ticks = e.getValue();
                int rgb = ticks <= 2 ? 0xFF0000 : ticks <= 7 ? 0xFFC800 : 0xFFFF00;
                NPCEntity egg = e.getKey();
                if (egg.level == level) {
                    tile(egg.level, egg.x, egg.z, EGG_RADIUS, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
                }
            }
        }
        String counter = get("kephriAttackCounter");
        if (!"OFF".equals(counter)) {
            // renderAttackCounter: the count in white, "Special" in red at 0
            String text = atkCount > 0 ? Integer.toString(atkCount) : "Special";
            int colour = atkCount > 0 ? 0xFFFFFFFF : 0xFFFF0000;
            Static418.method7860(kephri.level, kephri.z, 0, kephri.x, 0); // getCanvasTextLocation(g, text, 0): zOffset 0 = ground height
            if (OverlayManager.hitmarkpos[0] >= 0) {
                textAt(text, screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1], colour);
            }
        }
        String fireball = get("kephriFireballTiles");
        if (!"OFF".equals(fireball)) {
            // renderFireballs: fireball 2266 + bomber scarab 2147; AERIAL = 3x3 area, DEFAULT = the tile; colour by ticks left
            boolean aerial = "AERIAL".equals(fireball);
            for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
                ProjectileAnimation p = n.projectileAnimation;
                int remaining = p.t2 - TimeUtils.clock;
                if (p.id != PROJ_FIREBALL && p.id != PROJ_BOMBER_SCARAB || remaining <= 0 || p.targetX < 0 || p.level != level) {
                    continue;
                }
                int ticks = remaining / CYCLES_PER_TICK + 1; // ToaUtils.cyclesToTicks
                int rgb = ticks <= 2 ? 0xFF0000 : ticks == 3 ? 0xFFC800 : 0xFFFF00;
                // Perspective.getCanvasTileAreaPoly(lp, 3) is centred on the target point itself; getCanvasTilePoly on its tile
                int cx = aerial ? p.targetX : (p.targetX >> 9) * 512 + 256;
                int cz = aerial ? p.targetZ : (p.targetZ >> 9) * 512 + 256;
                tile(p.level, cx, cz, aerial ? 3 : 1, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
            }
        }
    }

    private static void renderBaba(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        int outline = argb("dangerOutlineColor");
        int fill = argb("dangerFillColor");
        // Baba.onGraphicsObjectCreated / onGameTick: falling boulders live FALLING_BOULDER_TICK_COUNT ticks; the latest shockwave
        SpotAnimation shockwave = null;
        java.util.Set<SpotAnimation> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SpotAnimation, Boolean>());
        for (SpotAnimationNode n = (SpotAnimationNode) Static346.spotAnimations.first(); n != null; n = (SpotAnimationNode) Static346.spotAnimations.next()) {
            SpotAnimation s = n.spotAnimation;
            if (s.id == GFX_FALLING_BOULDER || s.id == GFX_FALLING_BOULDER_2) {
                seen.add(s);
                if (!fallingBoulders.containsKey(s)) {
                    fallingBoulders.put(s, TimeUtils.clock);
                }
            } else if (s.id == GFX_SHOCKWAVE_SMALL || s.id == GFX_SHOCKWAVE_LARGE) {
                if (shockwave == null || s.id == GFX_SHOCKWAVE_LARGE) {
                    shockwave = s;
                }
            }
        }
        // npcHighlight: Ba-Ba tile (tile colours), non-weakened rolling boulders (danger), rubble (green outline, no fill)
        String babaTile = get("babaTile");
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || !node.npc.hasType() || node.npc.level != level) {
                continue;
            }
            NPCType type = type(node.npc);
            if (type == null) {
                continue;
            }
            if (node.npc == baba) {
                if (!"OFF".equals(babaTile)) {
                    npcTile(node.npc, type, "TRUE_TILE".equals(babaTile), argb("tileOutlineColor"), argb("tileFillColor"), screenX, screenY);
                }
            } else if (type.id == NPC_BOULDER && bool("babaNonWeakenedRollingBoulderTiles")) {
                npcTile(node.npc, type, false, outline, fill, screenX, screenY);
            } else if (type.id == NPC_RUBBLE && bool("babaRubbleTiles")) {
                npcTile(node.npc, type, false, 0xFF00FF00, 0, screenX, screenY);
            }
        }
        // BabaSceneOverlay.render (special attack outline + baboon outline: model outline, still to build)
        if (bool("babaHealthCounter")) {
            healthCounter(baba, BABA_HP_PERCENT_BREAKPOINTS, -1, screenX, screenY);
        }
        if (bool("babaShockwaveTiles") && shockwave != null) {
            // renderShockwaveTiles: (2*size+1)^2 around the shockwave + one tile at 'distance' on each axis, as one outlined area
            int size = shockwave.id == GFX_SHOCKWAVE_SMALL ? 1 : 2;
            int distance = size == 1 ? 2 : 3;
            int tx = shockwave.x >> 9;
            int tz = shockwave.z >> 9;
            java.util.Set<Long> area = new java.util.HashSet<Long>();
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    area.add(key(tx + dx, tz + dz));
                }
            }
            area.add(key(tx + distance, tz));
            area.add(key(tx - distance, tz));
            area.add(key(tx, tz + distance));
            area.add(key(tx, tz - distance));
            tileArea(shockwave.level, area, outline, fill, screenX, screenY);
        }
        java.util.Iterator<Map.Entry<SpotAnimation, Integer>> it = fallingBoulders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SpotAnimation, Integer> e = it.next();
            int ticks = FALLING_BOULDER_TICK_COUNT - (TimeUtils.clock - e.getValue()) / CYCLES_PER_TICK;
            if (ticks <= 0 || !seen.contains(e.getKey())) {
                it.remove();
                continue;
            }
            if (bool("babaFallingBoulderTiles") && e.getKey().level == level) {
                // renderFallingBoulderTiles: red <= 2 ticks, orange at 3, yellow before; fill = the same colour at alpha 20
                int rgb = ticks <= 2 ? 0xFF0000 : ticks == 3 ? 0xFFC800 : 0xFFFF00;
                SpotAnimation s = e.getKey();
                tile(s.level, (s.x >> 9) * 512 + 256, (s.z >> 9) * 512 + 256, 1, 0xFF000000 | rgb, 0x14000000 | rgb, screenX, screenY);
            }
        }
        if (bool("babaSarcophagusTiles")) {
            projectileTiles(PROJ_SARCOPHAGUS, -1, -1, level, outline, fill, screenX, screenY);
        }
        // babaBananaPeelTiles: banana peel loc 66073 (ObjectID.BANANA_PEEL 45755) - our Ba-Ba has no banana peels yet.
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** java.awt.geom.Area of tile polygons: every tile filled once, the outline only on the area's border. */
    private static void tileArea(int level, java.util.Set<Long> tiles, int outline, int fill, int screenX, int screenY) {
        for (long t : tiles) {
            int x = (int) (t >> 32);
            int z = (int) t;
            int[][] c = {
                project(level, x * 512, z * 512, screenX, screenY),
                project(level, x * 512 + 512, z * 512, screenX, screenY),
                project(level, x * 512 + 512, z * 512 + 512, screenX, screenY),
                project(level, x * 512, z * 512 + 512, screenX, screenY),
            };
            if (c[0] == null || c[1] == null || c[2] == null || c[3] == null) {
                continue;
            }
            // edges: south (z), east (x+1), north (z+1), west (x)
            long[] neighbour = {key(x, z - 1), key(x + 1, z), key(x, z + 1), key(x - 1, z)};
            if ((outline >>> 24) != 0) {
                for (int k = 0; k < 4; k++) {
                    if (!tiles.contains(neighbour[k])) {
                        Toolkit.active.line(c[k][0], c[k][1], c[(k + 1) % 4][0], c[(k + 1) % 4][1], outline, 1);
                    }
                }
            }
            if ((fill >>> 24) != 0) {
                fillPolygon(c, fill);
            }
        }
    }

    /** Projectile.getTarget() tile of every live projectile with one of the ids (getRemainingCycles() > 0). */
    private static void projectileTiles(int id1, int id2, int id3, int level, int outline, int fill, int screenX, int screenY) {
        for (ProjectileAnimationNode n = (ProjectileAnimationNode) Static505.projectiles.first(); n != null; n = (ProjectileAnimationNode) Static505.projectiles.next()) {
            ProjectileAnimation p = n.projectileAnimation;
            if (p.id != id1 && p.id != id2 && p.id != id3 || p.t2 - TimeUtils.clock <= 0 || p.targetX < 0 || p.level != level) {
                continue;
            }
            tile(p.level, (p.targetX >> 9) * 512 + 256, (p.targetZ >> 9) * 512 + 256, 1, outline, fill, screenX, screenY);
        }
    }

    private static void renderZebak(int screenX, int screenY) {
        int level = PlayerEntity.self.level;
        queueZebakProjectiles(); // Zebak.onProjectileMoved: a rock enters the queue as soon as it is seen
        queuedProjectiles.retainAll(liveProjectiles());
        // Zebak.npcHighlight via NpcOverlayService: tiles under waves / blood clouds
        String waveTile = get("zebakWaveTile");
        String cloudTile = get("zebakBloodCloudTile");
        int outline = argb("dangerOutlineColor");
        int fill = argb("dangerFillColor");
        for (int i = 0; i < NPCList.size; i++) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(NPCList.slots[i]);
            if (node == null || !node.npc.hasType() || node.npc.level != level) {
                continue;
            }
            NPCType type = type(node.npc);
            if (type == null) {
                continue;
            }
            String mode = type.id == NPC_WAVE ? waveTile : type.id == NPC_BLOOD_CLOUD || type.id == NPC_BLOOD_CLOUD_2 ? cloudTile : null;
            if (mode != null && !"OFF".equals(mode)) {
                npcTile(node.npc, type, "TRUE_TILE".equals(mode), outline, fill, screenX, screenY);
            }
        }
        // ZebakSceneOverlay.render: health counter, (blood magic outline - not on our server), projectile tiles
        if (bool("zebakHealthCounter")) {
            healthCounter(zebak, ZEBAK_HP_PERCENT_BREAKPOINTS, NPC_ZEBAK_DEAD, screenX, screenY);
        }
        if (bool("zebakProjectileTiles")) {
            // Zebak.onProjectileMoved (rock / jug / venom) -> ZebakSceneOverlay.renderProjectileTiles
            projectileTiles(PROJ_ROCK, PROJ_JUG, PROJ_VENOM, level, outline, fill, screenX, screenY);
        }
    }

    /** NpcOverlay (HighlightedNpc tile / trueTile, borderWidth 1): the npc's footprint at its drawn or its server position. */
    private static void npcTile(NPCEntity npc, NPCType type, boolean trueTile, int outline, int fill, int screenX, int screenY) {
        int size = Math.max(1, type.size);
        int cx;
        int cz;
        if (trueTile) {
            cx = npc.pathX[0] * 512 + size * 256;
            cz = npc.pathZ[0] * 512 + size * 256;
        } else {
            cx = npc.x;
            cz = npc.z;
        }
        tile(npc.level, cx, cz, size, outline, fill, screenX, screenY);
    }

    /** OverlayUtil / ToaUtils.drawOutlineAndFill: outline, then the (alpha) fill of a size x size area centred on (cx, cz). */
    private static void tile(int level, int cx, int cz, int size, int outline, int fill, int screenX, int screenY) {
        int h = size * 256;
        int[][] c = {
            project(level, cx - h, cz - h, screenX, screenY),
            project(level, cx + h, cz - h, screenX, screenY),
            project(level, cx + h, cz + h, screenX, screenY),
            project(level, cx - h, cz + h, screenX, screenY),
        };
        for (int[] corner : c) {
            if (corner == null) {
                return;
            }
        }
        if ((outline >>> 24) != 0) {
            for (int k = 0; k < 4; k++) {
                int[] a = c[k];
                int[] b = c[(k + 1) % 4];
                Toolkit.active.line(a[0], a[1], b[0], b[1], outline, 1);
            }
        }
        if ((fill >>> 24) != 0) {
            fillPolygon(c, fill);
        }
    }

    /** Scanline fill of a convex quad with Toolkit.fillRect (alpha-blended on every renderer). */
    private static void fillPolygon(int[][] c, int colour) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] p : c) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxY - minY > 2000) {
            return;
        }
        for (int y = minY; y < maxY; y++) {
            double sy = y + 0.5;
            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            for (int k = 0; k < c.length; k++) {
                int[] a = c[k];
                int[] b = c[(k + 1) % c.length];
                if ((a[1] <= sy && b[1] > sy) || (b[1] <= sy && a[1] > sy)) {
                    double x = a[0] + (sy - a[1]) * (b[0] - a[0]) / (double) (b[1] - a[1]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (left < right) {
                int x0 = (int) Math.ceil(left - 0.5);
                int x1 = (int) Math.ceil(right - 0.5);
                if (x1 > x0) {
                    Toolkit.active.fillRect(x0, y, x1 - x0, 1, colour);
                }
            }
        }
    }

    /** <Boss>.onScriptPostFired(HP_HUD_UPDATE) + <Boss>SceneOverlay.renderHealthCounter (Zebak hides it for ZEBAK_11733). */
    private static void healthCounter(NPCEntity npc, double[] percents, int hiddenId, int screenX, int screenY) {
        // Varbits.BOSS_HEALTH_* come from the boss HP HUD ("Requires boss hp bar in-game setting turned on").
        if (npc == null || !RspsHudBossBar.isOpen()) {
            return;
        }
        NPCType type = type(npc);
        if (type == null || type.id == hiddenId) {
            return;
        }
        int maxHp = RspsHudBossBar.maxHp();
        if (breakpoints == null || breakpointsMax != maxHp) {
            if (maxHp <= 1) {
                return;
            }
            breakpoints = new int[percents.length];
            for (int i = 0; i < percents.length; i++) {
                breakpoints[i] = (int) (percents[i] * maxHp);
            }
            breakpointsMax = maxHp;
        }
        int currentHp = RspsHudBossBar.currentHp();
        if (currentHp <= 0) {
            return;
        }
        int hp = hpUntilNextBreakPoint(breakpoints, currentHp);
        if (hp <= 0) {
            return;
        }
        // npc.getCanvasTextLocation(graphics, text, 0): RSActorMixin -> Perspective.getCanvasTextLocation(lp, text, zOffset) with zOffset
        // as the absolute height (the plugin passes getLogicalHeight() + 40 where it wants it higher), centred, baseline at the point.
        Static418.method7860(npc.level, npc.z, 0, npc.x, 0); // getCanvasTextLocation(g, text, 0): zOffset 0 = ground height
        if (OverlayManager.hitmarkpos[0] < 0) {
            return;
        }
        textAt(Integer.toString(hp), screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1], 0xFFFFFFFF);
    }

    /** ToaUtils.getHpUntilNextBreakPoint. */
    static int hpUntilNextBreakPoint(int[] hpBreakpoints, int currentHp) {
        for (int hpBreakpoint : hpBreakpoints) {
            if (currentHp > hpBreakpoint) {
                return currentHp - hpBreakpoint;
            }
        }
        return currentHp;
    }

    /** Ascent of the font used for the last textSprite (the baseline offset of its sprite). */
    private static int textAscent;
    /** Width of the text of the last textSprite (FontMetrics.stringWidth). */
    private static int textWidth;

    /** ToaUtils.renderTextLocation as a sprite: Arial in the given size/style, black shadow at +1,+1, the text colour on top. */
    private static Sprite textSprite(String text, int size, int awtStyle, int colour) {
        Toolkit toolkit = Toolkit.active;
        if (toolkit != textToolkit) {
            TEXT.clear();
            textToolkit = toolkit;
        }
        String key = text + "|" + size + "|" + awtStyle + "|" + Integer.toHexString(colour);
        Font font = new Font("Arial", awtStyle, size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics m = pg.getFontMetrics();
        pg.dispose();
        textAscent = m.getAscent();
        textWidth = m.stringWidth(text);
        Sprite sprite = TEXT.get(key);
        if (sprite == null) {
            if (TEXT.size() > 256) {
                TEXT.clear();
            }
            int width = textWidth + 1;
            int height = m.getAscent() + m.getDescent() + 1;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setFont(font);
            g.setColor(java.awt.Color.BLACK);
            g.drawString(text, 1, m.getAscent() + 1);
            g.setColor(new java.awt.Color(colour, true));
            g.drawString(text, 0, m.getAscent());
            g.dispose();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] >>> 24) < 128 ? 0 : pixels[i] | 0xFF000000;
            }
            sprite = toolkit.createSprite(width, width, height, pixels);
            TEXT.put(key, sprite);
        }
        return sprite;
    }

    /** ToaUtils.renderTextLocation with config fontSize / fontStyle, centred on x (getCanvasTextLocation), baseline y. */
    private static void textAt(String text, int x, int baseline, int colour) {
        int size = Math.max(12, Integer.parseInt(get("fontSize")));
        String style = get("fontStyle");
        int awtStyle = "ITALIC".equals(style) ? Font.ITALIC : "PLAIN".equals(style) ? Font.PLAIN : Font.BOLD;
        Sprite sprite = textSprite(text, size, awtStyle, colour);
        // Perspective.getCanvasTextLocation: x - stringWidth / 2, y = the projected point (the text's baseline).
        sprite.render(x - textWidth / 2, baseline - textAscent);
    }

    /** Same projection as RspsQuestHelperHints / RspsHudGroundItems: a fine local point at ground height, or null. */
    private static int[] project(int level, int fineX, int fineZ, int screenX, int screenY) {
        Static418.method7860(level, fineZ, 0, fineX, 0);
        if (OverlayManager.hitmarkpos[0] < 0) {
            return null;
        }
        return new int[]{screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1]};
    }
}
