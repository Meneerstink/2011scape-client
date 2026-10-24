import com.jagex.Entity;
import com.jagex.game.runetek6.config.npctype.SummoningNpcAudioOverrides;
import com.jagex.game.runetek6.config.seqtype.SeqType;
import com.jagex.sound.Sound;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class Static431 {

    @OriginalMember(owner = "client!nk", name = "v", descriptor = "Lclient!uf;")
    public static final Class370 aClass370_5 = new Class370();

    /**
     * Ancient Curses graphic sequences that own the curse frame sounds (rev-667 cache, see the server's
     * AncientCurses audio model): Protect Item 12568, Sap 12570, Turmoil 12566, Deflect 12574,
     * Leech Energy/Special 12576, Wrath 12581, Berserker 12590. Owner mix decision (2026-09-18):
     * every prayerbook sound is 10% quieter; server-sent prayer sounds carry the same gain.
     */
    private static final java.util.Set<Integer> PRAYER_SEQUENCES = new java.util.HashSet<>(java.util.Arrays.asList(12566, 12568, 12570, 12574, 12576, 12581, 12590));

    /**
     * N333 (owner 2026-10-24, "noon pet ... je hoort em continu best wel hard"): a pet's own stand/walk frame sounds (Noon's wing
     * flaps, seq 15897) play the whole time it follows, so they are as soft as the pets' walking sounds (server PetVoices.STEP_VOLUME).
     */
    private static final int PET_FRAME_VOLUME = 70;

    /** A pet follower: an npc with a "Pick-up" option (every 667 and OSRS pet here; familiars and monsters have none). */
    private static boolean isPet(com.jagex.game.runetek6.config.npctype.NPCType type) {
        if (type == null || type.op == null) return false;
        for (String op : type.op) {
            if ("Pick-up".equalsIgnoreCase(op)) return true;
        }
        return false;
    }

    @OriginalMember(owner = "client!nk", name = "a", descriptor = "(Lclient!cka;ILclient!eo;B)V")
    public static void method5827(@OriginalArg(0) SeqType arg0, @OriginalArg(1) int arg1, @OriginalArg(2) Entity arg2) {
        if (SoundManager.count >= 50 || (arg0 == null || arg0.soundInfo == null || arg0.soundInfo.length <= arg1 || arg0.soundInfo[arg1] == null)) {
            if (SoundManager.count >= 50 && arg0 != null && arg0.soundInfo != null && arg0.soundInfo.length > arg1 && arg0.soundInfo[arg1] != null) {
                AvTrace.log("PROBLEM frame sound dropped: queue full seq=" + arg0.id + " frame=" + arg1);
            }
            return;
        }
        @Pc(51) int local51 = arg0.soundInfo[arg1][0];
        @Pc(55) int local55 = local51 >> 8;
        @Pc(61) int local61 = local51 >> 5 & 0x7;
        @Pc(80) int local80;
        if (arg0.soundInfo[arg1].length > 1) {
            local80 = (int) (Math.random() * (double) arg0.soundInfo[arg1].length);
            if (local80 > 0) {
                local55 = arg0.soundInfo[arg1][local80];
            }
        }
        @Pc(93) int local93 = local51 & 0x1F;
        local80 = 256;
        if (arg0.anIntArray154 != null && arg0.anIntArray155 != null) {
            local80 = arg0.anIntArray154[arg1] + (int) ((double) (arg0.anIntArray155[arg1] - arg0.anIntArray154[arg1]) * Math.random());
        }
        @Pc(134) int local134 = arg0.anIntArray156 == null ? 255 : arg0.anIntArray156[arg1];
        // Familiar sequence cues are real cache audio, but their authored sequence volume is
        // commonly 255 and was reported as too loud beside the familiar server cues.
        if (arg2 instanceof NPCEntity && ((NPCEntity) arg2).type != null
            && SummoningNpcAudioOverrides.soundFor(((NPCEntity) arg2).type.id) != -1) {
            local134 = Math.min(local134, 122);
        }
        if (arg2 instanceof NPCEntity && isPet(((NPCEntity) arg2).type)) {
            local134 = Math.min(local134, PET_FRAME_VOLUME);
        }
        if (PRAYER_SEQUENCES.contains(arg0.id)) {
            local134 = local134 * 9 / 10;
        }
        if (local93 == 0) {
            if (arg2 == PlayerEntity.self) {
                if (!arg0.vorbisSound) {
                    SoundManager.playSynthSound(local55, local61, 0, local134, local80);
                    return;
                }
                SoundManager.playVorbisSound(local55, local61, 0, local134, local80, false);
            } else {
                AvTrace.log("PROBLEM frame sound ignored: radius 0 on another entity seq=" + arg0.id + " frame=" + arg1 + " id=" + local55);
            }
        } else if (arg2 == PlayerEntity.self ? ClientOptions.instance.soundVolume.getValue() != 0 : ClientOptions.instance.backgroundSoundVolume.getValue() != 0) {
            // The local player's own cues are queued with coord 0, which AudioRenderer mixes on the
            // sound-effects channel; gating them on the area/ambient option silenced own weapon
            // sounds (e.g. Abyssal whip) whenever ambient sound was off.
            @Pc(184) int local184 = arg2.x - 256 >> 9;
            @Pc(191) int local191 = arg2.z - 256 >> 9;
            @Pc(212) int local212 = arg2 == PlayerEntity.self ? 0 : local93 + (local191 << 8) + (local184 << 16) + (arg2.level << 24);
            SoundManager.sounds[SoundManager.count++] = new Sound((byte) (arg0.vorbisSound ? 2 : 1), local55, local61, 0, local134, local80, local212, arg2);
            AvTrace.log("frame sound queued seq=" + arg0.id + " frame=" + arg1 + " id=" + local55 + " radius=" + local93 + " self=" + (arg2 == PlayerEntity.self) + " coord=" + local212);
        } else if (local93 != 0) {
            AvTrace.log("frame sound dropped: background option 0 (player setting) seq=" + arg0.id + " frame=" + arg1 + " id=" + local55);
        }
    }
}
