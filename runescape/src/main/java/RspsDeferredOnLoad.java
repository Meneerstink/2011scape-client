import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * RSPS interface "hang" (owner bugs 2026-10-04 032111 / 192652: "nothing open but all icons are invisible"). The 667 client
 * runs an interface's onLoad scripts once, inside openSubInterface, and only when its cache group is already loaded
 * (ScriptRunner.executeOnLoad returns at once on InterfaceList.load == false). A group whose disk copy is stale - every cache
 * transaction of ours changes groups under a running client - is fetched over js5 asynchronously, so the screen mounted
 * without the scripts that un-hide and fill it: the slot is taken (tabs hidden by the gameframe's sub-change hook) but
 * nothing visible is drawn. This keeps such an interface and runs its onLoad as soon as the group arrives, while it is
 * still open. Every case is traced as a PROBLEM line.
 */
public final class RspsDeferredOnLoad {

    private static final LinkedHashSet<Integer> pending = new LinkedHashSet<Integer>();

    private RspsDeferredOnLoad() {
    }

    static void skipped(int id) {
        if (pending.add(id)) {
            AvTrace.log("PROBLEM interface " + id + " opened before its cache group was loaded - its onLoad scripts did not run"
                + " (screen mounted but empty); running them once the group arrives");
        }
    }

    /** Called once per client cycle on the game thread. */
    static void tick() {
        if (pending.isEmpty()) {
            return;
        }
        for (Integer id : new ArrayList<Integer>(pending)) {
            if (!isOpen(id)) {
                pending.remove(id);
                AvTrace.log("interface " + id + " closed before its cache group loaded - deferred onLoad dropped");
            } else if (InterfaceList.load(id)) {
                pending.remove(id);
                AvTrace.log("interface " + id + " loaded - running its deferred onLoad scripts");
                ScriptRunner.executeOnLoad(id);
            }
        }
    }

    private static boolean isOpen(int id) {
        if (InterfaceManager.topLevelInterface == id) {
            return true;
        }
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id == id) {
                return true;
            }
        }
        return false;
    }
}
