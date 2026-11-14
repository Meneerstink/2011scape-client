import com.jagex.graphics.Matrix;
import com.jagex.graphics.Model;
import com.jagex.graphics.Toolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * N610 ToA Extended: RuneLite ModelOutlineRenderer.drawOutline(npc, width, colour, feather) for our 667 client.
 *
 * While an npc is drawn (NPCEntity, after its models rendered with the camera of this frame) Model.picked(x, y, matrix, false, 0)
 * is called once with a point inside the model: the software model (JavaModel) and the GL model (Model_Sub2) then project every
 * vertex to the screen into their picking buffers. Those triangles are filled into a silhouette mask and the pixels OUTSIDE the
 * silhouette within [width] (plus [feather] fading pixels) become the outline, like RuneLite's outline (no outline over the model
 * itself). The spans are drawn after the scene (RspsToaExtended.render -> drawAll), in the same frame.
 */
final class RspsModelOutline {

    /** One requested outline: colour (argb), width and feather in pixels. */
    static final class Request {
        final int colour;
        final int width;
        final int feather;
        /** ToaUtils.renderSpecialAttackOutline: also fill npc.getConvexHull() in this argb (0 = no fill). */
        final int hull;

        Request(int colour, int width, int feather, int hull) {
            this.colour = colour;
            this.width = width;
            this.feather = feather;
            this.hull = hull;
        }
    }

    /** y, x0, x1 (exclusive), argb. */
    private static final List<int[]> spans = new ArrayList<int[]>();

    private RspsModelOutline() {
    }

    private static long diagAt;

    /** Trace (C:\RSPS\trace.on), at most once per 2 s: why an outline did or did not come out. */
    private static void diag(String what) {
        long now = System.currentTimeMillis();
        if (now - diagAt > 2000) {
            diagAt = now;
            AvTrace.log("[toaext-outline] " + what);
        }
    }

    /** StaticLocation / DynamicLocation / StaticGroundDecor render hook (Apmeken vents + roof supports + statue, Het mirrors). */
    static void locRendered(int id, int tileX, int tileZ, Model model, Matrix matrix) {
        Request request = RspsToaExtended.outlineForLoc(id, tileX, tileZ);
        if (request == null || model == null || matrix == null || Toolkit.active == null) {
            return;
        }
        outline(model, matrix, 0, 0, request);
    }

    /** PlayerEntity render hook (Kephri dung outline on the local player). */
    static void playerRendered(PlayerEntity player, Model[] models, Matrix matrix) {
        Request request = RspsToaExtended.outlineForPlayer(player);
        if (request == null || models == null || matrix == null || Toolkit.active == null) {
            return;
        }
        int[] p = new int[3];
        Toolkit.active.da(player.x, player.y - player.getY() / 2, player.z, p);
        for (Model model : models) {
            if (model != null) {
                outline(model, matrix, p[0], p[1], request);
            }
        }
    }

    /** NPCEntity render hook: the npc's models were just drawn with [matrix]. */
    static void npcRendered(NPCEntity npc, Model[] models, Matrix matrix) {
        Request request = RspsToaExtended.outlineFor(npc);
        if (request == null || models == null || matrix == null || Toolkit.active == null) {
            return;
        }
        // a point inside the projected model: the npc's position at half its height
        int[] p = new int[3];
        Toolkit.active.da(npc.x, npc.y - npc.getY() / 2, npc.z, p);
        if (p[0] < 0 && p[1] < 0) {
            diag("npc " + npc.type.id + " centre off screen " + p[0] + "," + p[1]);
            return;
        }
        for (Model model : models) {
            if (model != null) {
                outline(model, matrix, p[0], p[1], request);
            }
        }
    }

    /** One captured model of this frame: world-space vertices (per face index) + faces, projected at HUD time. */
    private static final class Pending {
        final int[][] world;
        final short[] fa;
        final short[] fb;
        final short[] fc;
        final int faces;
        final Request request;

        Pending(int[][] world, short[] fa, short[] fb, short[] fc, int faces, Request request) {
            this.world = world;
            this.fa = fa;
            this.fb = fb;
            this.fc = fc;
            this.faces = faces;
            this.request = request;
        }
    }

    private static final List<Pending> pending = new ArrayList<Pending>();

    /**
     * 08:4x: the scene is drawn into a smaller viewport and scaled (Stretched / scaling), so projecting with the camera of the scene
     * pass put the outline at 2/3 of the model's screen position. Capture world coordinates now ([matrix] = model -> world) and
     * project them in drawAll with the camera the tile overlays use (Static418.method7860: Static460.aMatrix_10 + Toolkit.da).
     */
    private static void outline(Model model, Matrix matrix, int px, int py, Request request) {
        int[][] world;
        short[] fa;
        short[] fb;
        short[] fc;
        int faces;
        if (model instanceof JavaModel) {
            JavaModel m = (JavaModel) model;
            if (m.vertexX == null || m.vertexY == null || m.vertexZ == null || m.vertexCount <= 0) {
                diag("software model without vertices");
                return;
            }
            world = new int[3][m.vertexCount];
            int[] d = new int[3];
            for (int v = 0; v < m.vertexCount; v++) {
                matrix.project(m.vertexX[v], m.vertexY[v], m.vertexZ[v], d);
                world[0][v] = d[0];
                world[1][v] = d[1];
                world[2][v] = d[2];
            }
            fa = m.faceA;
            fb = m.faceB;
            fc = m.faceC;
            faces = m.faceCount;
        } else if (model instanceof Model_Sub2) {
            Model_Sub2 m = (Model_Sub2) model;
            world = m.rspsWorldVertices(matrix);
            if (world == null) {
                diag("GL model without vertex data");
                return;
            }
            fa = m.aShortArray67;
            fb = m.aShortArray71;
            fc = m.aShortArray76;
            faces = m.anInt5560;
        } else {
            diag("model type " + model.getClass().getName() + " not supported");
            return; // Model_Sub1 (native toolkit): not supported yet
        }
        if (fa == null || fb == null || fc == null || faces <= 0) {
            diag("no faces (" + model.getClass().getName() + ")");
            return;
        }
        pending.add(new Pending(world, fa, fb, fc, faces, request));
    }

    /** Silhouette of the projected faces -> outline spans (pixels outside the silhouette within width + feather). */
    private static void raster(int[] xs, int[] ys, short[] fa, short[] fb, short[] fc, int faces, Request request) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int f = 0; f < faces; f++) {
            for (int v : new int[]{fa[f], fb[f], fc[f]}) {
                if (v < 0 || v >= xs.length || xs[v] == -999999) {
                    continue;
                }
                minX = Math.min(minX, xs[v]);
                maxX = Math.max(maxX, xs[v]);
                minY = Math.min(minY, ys[v]);
                maxY = Math.max(maxY, ys[v]);
            }
        }
        if (minX > maxX || maxX - minX > 2000 || maxY - minY > 2000) {
            diag("bbox empty/too big " + minX + ".." + maxX + " x " + minY + ".." + maxY);
            return;
        }
        diag("ok bbox " + minX + ".." + maxX + " x " + minY + ".." + maxY + " faces " + faces);
        if (request.hull != 0) {
            hull(xs, ys, fa, fb, fc, faces, request.hull);
        }
        int pad = request.width + request.feather + 1;
        int ox = minX - pad;
        int oy = minY - pad;
        int w = maxX - minX + 1 + pad * 2;
        int h = maxY - minY + 1 + pad * 2;
        boolean[] mask = new boolean[w * h];
        for (int f = 0; f < faces; f++) {
            int a = fa[f], b = fb[f], c = fc[f];
            if (a < 0 || b < 0 || c < 0 || a >= xs.length || b >= xs.length || c >= xs.length
                || xs[a] == -999999 || xs[b] == -999999 || xs[c] == -999999) {
                continue;
            }
            fill(mask, w, h, xs[a] - ox, ys[a] - oy, xs[b] - ox, ys[b] - oy, xs[c] - ox, ys[c] - oy);
        }
        // distance (in pixels, square neighbourhood) from the silhouette for every pixel outside it, up to width + feather
        int reach = request.width + request.feather;
        int[] dist = new int[w * h];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = mask[i] ? 0 : Integer.MAX_VALUE;
        }
        for (int step = 1; step <= reach; step++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (dist[i] != Integer.MAX_VALUE) {
                        continue;
                    }
                    if (near(dist, w, h, x, y, step - 1)) {
                        dist[i] = step;
                    }
                }
            }
        }
        int rgb = request.colour & 0xFFFFFF;
        int alpha = request.colour >>> 24;
        for (int y = 0; y < h; y++) {
            int runStart = -1;
            int runColour = 0;
            for (int x = 0; x <= w; x++) {
                int colour = 0;
                if (x < w) {
                    int d = dist[y * w + x];
                    if (d >= 1 && d <= reach) {
                        int a = d <= request.width ? alpha : alpha * (reach - d + 1) / (request.feather + 1);
                        colour = a << 24 | rgb;
                    }
                }
                if (colour != runColour) {
                    if (runColour != 0) {
                        spans.add(new int[]{oy + y, ox + runStart, ox + x, runColour});
                    }
                    runStart = x;
                    runColour = colour;
                }
            }
        }
    }

    /** Convex hull (monotone chain) of all projected vertices of the faces, filled as spans. */
    private static void hull(int[] xs, int[] ys, short[] fa, short[] fb, short[] fc, int faces, int colour) {
        java.util.TreeSet<Long> pts = new java.util.TreeSet<Long>();
        for (int f = 0; f < faces; f++) {
            for (int v : new int[]{fa[f], fb[f], fc[f]}) {
                if (v >= 0 && v < xs.length && xs[v] != -999999) {
                    pts.add(((long) (xs[v] + 100000) << 32) | (ys[v] + 100000));
                }
            }
        }
        if (pts.size() < 3) {
            return;
        }
        long[] p = new long[pts.size()];
        int n = 0;
        for (long v : pts) {
            p[n++] = v;
        }
        long[] h = new long[2 * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
                k--;
            }
            h[k++] = p[i];
        }
        for (int i = n - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
                k--;
            }
            h[k++] = p[i];
        }
        int m = k - 1;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < m; i++) {
            minY = Math.min(minY, py(h[i]));
            maxY = Math.max(maxY, py(h[i]));
        }
        for (int y = minY; y < maxY; y++) {
            double sy = y + 0.5;
            double l = Double.MAX_VALUE, rr = -Double.MAX_VALUE;
            for (int i = 0; i < m; i++) {
                int ax = px(h[i]), ay = py(h[i]), bx = px(h[(i + 1) % m]), by = py(h[(i + 1) % m]);
                if ((ay <= sy && by > sy) || (by <= sy && ay > sy)) {
                    double x = ax + (sy - ay) * (bx - ax) / (double) (by - ay);
                    l = Math.min(l, x);
                    rr = Math.max(rr, x);
                }
            }
            if (l < rr) {
                spans.add(new int[]{y, (int) Math.ceil(l - 0.5), (int) Math.ceil(rr - 0.5), colour});
            }
        }
    }

    private static int px(long v) {
        return (int) (v >> 32) - 100000;
    }

    private static int py(long v) {
        return (int) v - 100000;
    }

    private static long cross(long o, long a, long b) {
        return (long) (px(a) - px(o)) * (py(b) - py(o)) - (long) (py(a) - py(o)) * (px(b) - px(o));
    }

    private static boolean near(int[] dist, int w, int h, int x, int y, int value) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && dist[ny * w + nx] == value) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Triangle fill (half-open spans) into the mask. */
    private static void fill(boolean[] mask, int w, int h, int x0, int y0, int x1, int y1, int x2, int y2) {
        int minY = Math.max(0, Math.min(y0, Math.min(y1, y2)));
        int maxY = Math.min(h - 1, Math.max(y0, Math.max(y1, y2)));
        for (int y = minY; y <= maxY; y++) {
            double sy = y + 0.5;
            double l = Double.MAX_VALUE;
            double r = -Double.MAX_VALUE;
            int[][] e = {{x0, y0, x1, y1}, {x1, y1, x2, y2}, {x2, y2, x0, y0}};
            for (int[] k : e) {
                if ((k[1] <= sy && k[3] > sy) || (k[3] <= sy && k[1] > sy)) {
                    double x = k[0] + (sy - k[1]) * (k[2] - k[0]) / (double) (k[3] - k[1]);
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                }
            }
            if (l <= r) {
                int xs = Math.max(0, (int) Math.ceil(l - 0.5));
                int xe = Math.min(w - 1, (int) Math.ceil(r - 0.5) - 1);
                for (int x = xs; x <= xe; x++) {
                    mask[y * w + x] = true;
                }
            }
        }
    }

    /** After the scene: every outline of this frame, then the list is empty again. */
    static void drawAll(int screenX, int screenY) {
        if (Toolkit.active != null && !pending.isEmpty() && !OrthoMode.enabled && Static460.aMatrix_10 != null) {
            Toolkit.active.setCamera(Static460.aMatrix_10); // as Static418.method7860 (the tile overlays' projection)
            int[] out = new int[3];
            for (Pending p : pending) {
                int n = p.world[0].length;
                int[] xs = new int[n];
                int[] ys = new int[n];
                for (int i = 0; i < n; i++) {
                    Toolkit.active.da(p.world[0][i], p.world[1][i], p.world[2][i], out);
                    if (out[0] == -1 && out[1] == -1 && out[2] == -1) {
                        xs[i] = -999999; // behind the camera or outside the viewport
                    } else {
                        xs[i] = screenX + out[0];
                        ys[i] = screenY + out[1];
                    }
                }
                int valid = 0;
                for (int i = 0; i < n; i++) {
                    if (xs[i] != -999999) {
                        valid++;
                    }
                }
                if (valid == 0 && n > 0) {
                    // trace: why the HUD projection lost the whole model (world coords vs the local player's, same camera)
                    PlayerEntity me = PlayerEntity.self;
                    int[] o2 = new int[3];
                    if (me != null) {
                        Toolkit.active.da(me.x, me.y, me.z, o2);
                    }
                    diag("all off-screen: w0=" + p.world[0][0] + "," + p.world[1][0] + "," + p.world[2][0] + " self=" + (me == null ? "-"
                        : me.x + "," + me.y + "," + me.z + " -> " + o2[0] + "," + o2[1] + "," + o2[2]));
                }
                raster(xs, ys, p.fa, p.fb, p.fc, p.faces, p.request);
            }
        }
        pending.clear();
        if (Toolkit.active != null) {
            for (int[] s : spans) {
                Toolkit.active.fillRect(s[1], s[0], s[2] - s[1], 1, s[3]); // spans are already in screen space (raster adds screenX/Y)
            }
        }
        spans.clear();
    }
}
