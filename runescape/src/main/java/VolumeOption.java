import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;

@OriginalClass("client!rba")
public final class VolumeOption extends Option {

    @OriginalMember(owner = "client!rba", name = "<init>", descriptor = "(ILclient!kv;)V")
    public VolumeOption(@OriginalArg(0) int value, @OriginalArg(1) ClientOptions options) {
        super(value, options);
    }

    /**
     * Highest value of this slider. The Audio Options scripts decide it (cs2 1215 writes, 1159 / 1216-1218 read back): sound
     * effects and area sounds scale their 5-step slider to 0..127, music and login music to 0..255 ({@link #MUSIC_MAX}). One
     * shared 0..127 clamp reset every saved music volume above 127 to 127 - half - on the next start (owner 2026-09-30:
     * "music cannot be adjusted to its proper maximum", ambient sounded too loud next to half-volume music).
     */
    public static final int EFFECT_MAX = 127;
    public static final int MUSIC_MAX = 255;

    /** 0 until a range constructor sets it (Option's own constructor asks for the default before this class's fields exist). */
    private int max;

    public VolumeOption(int value, ClientOptions options, int max) {
        super(value, options);
        this.max = max;
        this.validate();
    }

    public VolumeOption(ClientOptions options, int max) {
        super(options);
        this.max = max;
        super.value = this.getDefaultValue();
    }

    public int max() {
        return this.max == 0 ? EFFECT_MAX : this.max;
    }

    @OriginalMember(owner = "client!rba", name = "<init>", descriptor = "(Lclient!kv;)V")
    public VolumeOption(@OriginalArg(0) ClientOptions options) {
        super(options);
    }

    @OriginalMember(owner = "client!rba", name = "a", descriptor = "(ZI)V")
    @Override
    protected void setValue(@OriginalArg(1) int value) {
        super.value = value;
    }

    @OriginalMember(owner = "client!rba", name = "a", descriptor = "(B)V")
    @Override
    public void validate() {
        if (super.value < 0 || super.value > this.max()) {
            super.value = this.getDefaultValue();
        }
    }

    @OriginalMember(owner = "client!rba", name = "a", descriptor = "(IB)I")
    @Override
    public int canSet(@OriginalArg(0) int value) {
        return 1;
    }

    @OriginalMember(owner = "client!rba", name = "a", descriptor = "(Z)I")
    public int getValue() {
        return super.value;
    }

    @OriginalMember(owner = "client!rba", name = "a", descriptor = "(I)I")
    @Override
    protected int getDefaultValue() {
        // Every slider starts at its maximum (127 for effects / area, 255 for music).
        return this.max();
    }
}
