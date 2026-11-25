import com.jagex.core.io.Packet;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class InterfaceList {

    @OriginalMember(owner = "client!of", name = "z", descriptor = "[[Lclient!hda;")
    public static Component[][] interfaces;

    @OriginalMember(owner = "client!el", name = "R", descriptor = "[[Lclient!hda;")
    public static Component[][] cache;

    @OriginalMember(owner = "client!ec", name = "G", descriptor = "[Z")
    public static boolean[] loaded;

    @OriginalMember(owner = "client!eja", name = "a", descriptor = "(BI)Lclient!hda;")
    public static Component list(@OriginalArg(1) int idAndSlot) {
        @Pc(16) int id = idAndSlot >> 16;
        @Pc(20) int slot = idAndSlot & 0xFFFF;

        if (interfaces[id] == null || interfaces[id][slot] == null) {
            @Pc(38) boolean loaded = load(id);

            if (!loaded) {
                return null;
            }
        }

        return interfaces[id][slot];
    }

    @OriginalMember(owner = "client!rw", name = "d", descriptor = "(II)Z")
    public static boolean load(@OriginalArg(1) int id) {
        if (loaded[id]) {
            return true;
        } else if (Component.interfacesJs5.requestgroupdownload(id)) {
            @Pc(25) int slots = Component.interfacesJs5.fileLimit(id);
            if (slots == 0) {
                loaded[id] = true;
                return true;
            }

            if (interfaces[id] == null) {
                interfaces[id] = new Component[slots];
            }

            for (@Pc(53) int slot = 0; slot < slots; slot++) {
                if (interfaces[id][slot] == null) {
                    @Pc(66) byte[] data = Component.interfacesJs5.getfile(slot, id);

                    if (data != null) {
                        @Pc(78) Component component = interfaces[id][slot] = new Component();
                        component.slot = slot + (id << 16);

                        if (data[0] != -1) {
                            throw new IllegalStateException("if1");
                        }

                        component.decode(new Packet(data));
                        EquipmentInterfaceLayout.apply(id, slot, component);
                        ItemDialogLayout.apply(id, slot, component); // CONFIRM-ICOON-KLEIN: 94:9 at item-sprite size
                        LoginScreenTrim.apply(id, slot, component); // N1: no Jagex account links on the login box
                        DungeoneeringRemoved.apply(id, slot, component); // DGN: no Dungeoneering cell in the stats tab

                        // RSPS Deadman HUD: the Wilderness overlay's two text components
                        // (381:2 and 381:5) carry a baked onTimer hook (client script 54) that
                        // every tick recomputes "Level: (z-3520)/8+1" from the player's own
                        // coordinate and overwrites the text - the source of the "-2"/"-3"
                        // readings outside the Wilderness and of any server text being clobbered.
                        // The server (DeadmanHud) now owns this text (level / Guarded / Dangerous /
                        // skull timer), so the client-side recomputation is dropped.
                        if (id == 381 && (slot == 2 || slot == 5)) {
                            component.onTimer = null;
                        }
                    }
                }
            }

            loaded[id] = true;
            return true;
        } else {
            return false;
        }
    }

    @OriginalMember(owner = "client!aia", name = "a", descriptor = "(III)Lclient!hda;")
    public static Component getComponent(@OriginalArg(2) int idAndSlot, @OriginalArg(1) int component) {
        @Pc(21) Component inter = list(idAndSlot);

        if (component == -1) {
            return inter;
        } else if (inter != null && inter.staticComponents != null && component < inter.staticComponents.length) {
            return inter.staticComponents[component];
        } else {
            return null;
        }
    }

    @OriginalMember(owner = "client!up", name = "c", descriptor = "(Z)V")
    public static void reset() {
        interfaces = new Component[Component.interfacesJs5.groupSize()][];
        cache = new Component[Component.interfacesJs5.groupSize()][];
        loaded = new boolean[Component.interfacesJs5.groupSize()];
    }

    private InterfaceList() {
        /* empty */
    }
}
