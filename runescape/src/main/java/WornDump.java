import com.jagex.game.PlayerModel;
import com.jagex.game.runetek6.config.bastype.BASTypeList;
import com.jagex.game.runetek6.config.defaults.WearposDefaults;
import com.jagex.game.runetek6.config.idktype.IDKTypeList;
import com.jagex.game.runetek6.config.npctype.NPCTypeList;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import com.jagex.game.runetek6.config.seqtype.SeqTypeList;
import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.graphics.Matrix;
import com.jagex.graphics.Model;
import com.jagex.graphics.Toolkit;
import com.jagex.graphics.ToolkitType;
import com.jagex.js5.js5;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * N537 item audit stap 2 (worn look): 'worn <file> <outdir>' draws a player wearing a set of items, offscreen, with the game's own
 * player-model builder ({@link PlayerModel#bodyModel}) and writes <outdir>/<name>.png = front, side, back, other side next to each
 * other. A line of the file is '<name> <female 0|1> <basId> v0 .. vN' where v = the appearance values the server sends for the
 * non-hidden wearpos slots (0 = nothing, 0x100 + kit, 0x8000 + appearance rank - PlayerUpdateBlockSegment), decoded exactly like
 * {@link PlayerEntity} does, so a wrong appearance_id in items.yml shows up as the wrong item. Test tool only (TestHook).
 */
public final class WornDump {
    private static final int W = 160, H = 240;
    private static final int[] YAWS = {0, 4096, 8192, 12288}; // Trig1 units (16384 = full turn)
    private static final ConcurrentLinkedQueue<String[]> queue = new ConcurrentLinkedQueue<>();
    private static volatile java.io.File dir;
    private static volatile int written, failed, waiting;
    private static Toolkit toolkit;
    private static String[] current;
    private static int currentTries;
    private static int yOffset = Integer.MIN_VALUE;
    static volatile int lastBas;
    private static final StringBuilder basLog = new StringBuilder();

    public static String start(String file, String outDir) throws java.io.IOException {
        dir = new java.io.File(outDir);
        dir.mkdirs();
        int n = 0;
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(file))) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                queue.add(line.split("\\s+"));
                n++;
            }
        }
        written = 0;
        failed = 0;
        basLog.setLength(0);
        return "ok worn queued " + n;
    }

    public static String status() {
        return "ok worn pending " + (queue.size() + (current != null ? 1 : 0)) + " written " + written + " failed " + failed;
    }

    /** Game thread, once per frame (next to RspsLootTracker.renderIcons). */
    static void render() {
        if (current == null) {
            current = queue.poll();
            currentTries = 0;
        }
        if (current == null || ObjTypeList.instance == null || PlayerEntity.wornObjIds == null) {
            return;
        }
        for (int i = 0; i < 4 && current != null; i++) {
            String[] line = current;
            try {
                BufferedImage image = draw(line);
                if (image == null) {
                    if (++currentTries < 400) {
                        return; // models still loading: same line next frame
                    }
                    fail(line, "models not loaded");
                } else {
                    javax.imageio.ImageIO.write(image, "png", new java.io.File(dir, line[0] + ".png"));
                    basLog.append(line[0]).append('\t').append(lastBas).append('\n');
                    if (queue.isEmpty()) {
                        java.nio.file.Files.write(new java.io.File(dir, "bas.tsv").toPath(), basLog.toString().getBytes("UTF-8"));
                    }
                    written++;
                }
            } catch (Exception e) {
                fail(line, String.valueOf(e));
            }
            current = queue.poll();
            currentTries = 0;
        }
    }

    private static void fail(String[] line, String why) {
        failed++;
        try {
            java.nio.file.Files.write(new java.io.File(dir, line[0] + ".err").toPath(), why.getBytes("UTF-8"));
        } catch (java.io.IOException ignored) {
        }
    }

    private static BufferedImage draw(String[] line) {
        if (toolkit == null) {
            java.awt.Canvas canvas = new java.awt.Canvas();
            canvas.setSize(W, H);
            toolkit = Static255.create(ToolkitType.JAVA, js5.SHADERS, canvas, Js5TextureSource.instance, 0);
        }
        boolean female = line[1].equals("1");
        int[] hidden = WearposDefaults.instance.hidden;
        int[] identikit = new int[hidden.length];
        int v = 3;
        for (int i = 0; i < hidden.length; i++) {
            if (hidden[i] == 1) {
                continue;
            }
            String token = v < line.length ? line[v++] : "0";
            if (token.startsWith("H")) {
                // Hair under a head item: Appearance.lookupHairStyle (enum 2339/2342 -> struct 790 / 791 with param 625 face mask).
                int style = Integer.parseInt(token.substring(1));
                int head = identikit[0];
                boolean mask = (head & 0x40000000) != 0 && ObjTypeList.instance.list(head & 0x3FFFFFFF).param(625, 0) == 1;
                int slot = com.jagex.game.runetek6.config.enumtype.EnumTypeList.instance.list(female ? 2342 : 2339).getInt(style);
                int struct = com.jagex.game.runetek6.config.enumtype.EnumTypeList.instance.list(female ? 2341 : 2338).getInt(slot);
                int kit = com.jagex.game.runetek6.config.structtype.StructTypeList.instance.list(struct).param(-1, mask ? 791 : 790);
                identikit[i] = kit | Integer.MIN_VALUE;
                continue;
            }
            if (token.startsWith("o")) {
                identikit[i] = Integer.parseInt(token.substring(1)) | 0x40000000; // direct obj id (check of the appearance rank)
                continue;
            }
            int id = Integer.decode(token);
            if (id == 0) {
                identikit[i] = 0;
            } else if (id >= 32768) {
                identikit[i] = PlayerEntity.wornObjIds[id - 32768] | 0x40000000;
            } else {
                identikit[i] = (id - 256) | Integer.MIN_VALUE;
            }
        }
        // BAS: a number, or 'W' = what the server sends for the weapon without a RenderAnimations override (cache param 644, else 1426).
        // Optional '/<walk|run|seq id>/<frame>' after the BAS token (N537 stap 2: N549/N550 lopend): that animation frame instead of the stand.
        String[] basToken = line[2].split("/");
        int basId;
        if (basToken[0].equals("W")) {
            int weapon = identikit[3];
            basId = (weapon & 0x40000000) != 0 ? ObjTypeList.instance.list(weapon & 0x3FFFFFFF).param(644, 1426) : 1426;
        } else {
            basId = Integer.parseInt(basToken[0]);
        }
        lastBas = basId;
        com.jagex.game.runetek6.config.bastype.BASType bas = BASTypeList.instance.list(basId);
        int stand = bas == null ? -1 : bas.ready != -1 ? bas.ready : bas.ready();
        int frame = 0;
        if (basToken.length >= 2 && bas != null) {
            stand = basToken[1].equals("walk") ? bas.walk : basToken[1].equals("run") ? bas.run : Integer.parseInt(basToken[1]);
            frame = basToken.length >= 3 ? Integer.parseInt(basToken[2]) : 0;
        }
        if (frame == 0 && basToken.length < 2 && stand != -1 && !bas.isReady(stand)) {
            return null; // stand frames still loading (isReady only knows the BAS's own stand ids, not walk/run)
        }
        ComponentAnimator standAnimator = null;
        if (stand != -1) {
            standAnimator = new ComponentAnimator();
            standAnimator.update(stand, 0, 0, false);
            if (frame > 0 && standAnimator.animation != null) {
                int frames = standAnimator.animation.frames.length;
                standAnimator.currentFrame = frame % frames;
                standAnimator.nextFrame = (frame + 1) % frames;
                standAnimator.frameOffset = 0;
            }
            if (basToken.length >= 2 && standAnimator.animation != null && !standAnimator.method9111()) {
                return null; // walk/run frames still loading: same line next frame
            }
        }
        int[] palette = new int[10];
        PlayerModel model = new PlayerModel();
        model.update(palette, identikit, null, -1, basId, female);
        model.hash ^= 0x4E3537L << 40; // never share a cached body model with a real player (other toolkit)
        Model body = model.bodyModel(ObjTypeList.instance, null, BASTypeList.instance, SeqTypeList.instance, 2048, null, WearposDefaults.instance,
            IDKTypeList.instance, toolkit, NPCTypeList.instance, null, 0, standAnimator, TimedVarDomain.instance);
        if (body == null || !body.loadedTextures()) {
            return null;
        }
        if (line[0].equals("base_m") || yOffset == Integer.MIN_VALUE) {
            yOffset = -body.fa() / 2 - 20; // the bare body sets the camera, so a tall hat does not move the player in the picture
        }
        BufferedImage out = new BufferedImage(W * YAWS.length, H, BufferedImage.TYPE_INT_ARGB);
        for (int view = 0; view < YAWS.length; view++) {
            toolkit.DA(W / 2, H / 2, 512, 512);
            Matrix camera = toolkit.createMatrix();
            camera.makeIdentity();
            toolkit.setCamera(camera);
            toolkit.xa(1.1F);
            toolkit.ZA(0xFFFFFF, 1.0F, 1.0F, -50.0F, -10.0F, -50.0F);
            Matrix m = toolkit.scratchMatrix();
            m.makeIdentity();
            m.rotateAxisY(YAWS[view]);
            m.translate(0, yOffset, 2100);
            int zNear = toolkit.i();
            int zFar = toolkit.XA();
            toolkit.f(50, Integer.MAX_VALUE);
            toolkit.ya();
            toolkit.la();
            toolkit.aa(0, 0, W, H, 0, 0);
            body.render(m, null, 1);
            toolkit.f(zNear, zFar);
            int[] px = toolkit.na(0, 0, W, H);
            for (int p = 0; p < W * H && p < px.length; p++) {
                if ((px[p] & 0xFFFFFF) != 0) {
                    out.setRGB(view * W + p % W, p / W, 0xFF000000 | px[p]);
                }
            }
        }
        return out;
    }
}
