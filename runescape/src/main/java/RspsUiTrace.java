import com.jagex.core.util.RspsTrace;

import java.util.Map;
import java.util.TreeMap;

/**
 * N606 12-11 (owner: "alle grafische dingen loggen tracen"): where every interface really draws on screen. Each drawn component with
 * content (rectangle, text, graphic, model) grows its interface's box; every 3 s one "[ui] iface=<id> rect=x,y,w,h" line per interface
 * that drew something (only with traces on). work/claude-scripts/effect_report.py reports interfaces whose boxes overlap (e.g. the
 * opponent box over the ToA HUD).
 */
public final class RspsUiTrace {

    private static final Map<Integer, int[]> boxes = new TreeMap<>();
    private static long last;

    private RspsUiTrace() {
    }

    public static void drawn(Component child, int screenX, int screenY) {
        if (!RspsTrace.ENABLED || child == null || child.width <= 0 || child.height <= 0) {
            return;
        }
        boolean content = child.type == 3 || child.type == 5 || child.type == 6 || (child.type == 4 && child.text != null && !child.text.isEmpty());
        if (!content) {
            return;
        }
        int hash = child.slot != -1 ? child.slot : child.id;
        if (hash < 0) {
            return;
        }
        int iface = hash >>> 16;
        int[] b = boxes.get(iface);
        if (b == null) {
            boxes.put(iface, new int[] {screenX, screenY, screenX + child.width, screenY + child.height});
        } else {
            b[0] = Math.min(b[0], screenX);
            b[1] = Math.min(b[1], screenY);
            b[2] = Math.max(b[2], screenX + child.width);
            b[3] = Math.max(b[3], screenY + child.height);
        }
        long now = System.currentTimeMillis();
        if (now - last >= 3000) {
            last = now;
            StringBuilder sb = new StringBuilder("[ui] frame");
            for (Map.Entry<Integer, int[]> e : boxes.entrySet()) {
                int[] r = e.getValue();
                sb.append(" iface=").append(e.getKey()).append(" rect=").append(r[0]).append(',').append(r[1]).append(',')
                    .append(r[2] - r[0]).append(',').append(r[3] - r[1]).append(';');
            }
            AvTrace.log(sb.toString());
            boxes.clear();
        }
    }
}
