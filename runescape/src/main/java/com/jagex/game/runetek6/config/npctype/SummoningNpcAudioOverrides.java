package com.jagex.game.runetek6.config.npctype;

import java.io.File;

/**
 * Revision-667 client bridge for the Summoning familiar presentation sounds.
 *
 * <p>The server does not emit movement audio.  NPCType is the owner of the
 * ready/walk/run/crawl sound fields, so the override is applied after the
 * cache definition has been decoded and before the type is cached.</p>
 *
 * <p>Source policy: the QC3 c3x list is the provisional source selected by
 * the owner.  Explicit general/voice/creature/presentation cues are preferred;
 * familiars without one get the list's generic lore_summon cue (4376).  This
 * keeps every current familiar covered without promoting rev-530 base-creature
 * candidates or hit/death cues into movement audio.</p>
 */
public final class SummoningNpcAudioOverrides {

    private static final int GENERIC_SUMMON_SOUND = 4376;
    private static final int SOUND_RANGE_MAX = 5;
    // Owner retest: provisional familiar movement cues were still about 5% too loud.
    private static final int PROVISIONAL_LOOP_VOLUME = 61;

    private SummoningNpcAudioOverrides() {
    }

    /**
     * Applies the provisional c3x presentation cue to a familiar definition.
     * Existing cache-proven sound fields are preserved; only missing fields
     * are filled so Spirit scorpion's proven 667 sequence remains authoritative.
     */
    public static void apply(NPCType type) {
        int sound = soundFor(type.id);
        if (sound == -1) {
            return;
        }

        // These QC3 entries are short presentation cues, not authored idle
        // ambience.  Keep readySound untouched so a familiar cannot loop its
        // presentation cue while standing still.
        if (type.walkSound == -1) {
            type.walkSound = sound;
        }
        if (type.runSound == -1) {
            type.runSound = sound;
        }
        if (type.crawlSound == -1) {
            type.crawlSound = sound;
        }
        if (type.soundRangeMax == 0) {
            type.soundRangeMax = SOUND_RANGE_MAX;
        }
        if (type.id != 6837 && type.soundVolume == 255) {
            type.soundVolume = PROVISIONAL_LOOP_VOLUME;
        }

        if (new File("C:/RSPS/avtrace.on").exists()) {
            System.out.println("[avtrace " + System.currentTimeMillis() + "] summon-audio type=" + type.id
                + " cue=" + sound + " ready=" + type.readySound + " walk=" + type.walkSound
                + " run=" + type.runSound + " crawl=" + type.crawlSound
                + " range=" + type.soundRangeMax + " volume=" + type.soundVolume);
        }
    }

    /**
     * Returns the c3x presentation cue selected for a current familiar NPC.
     * A value of -1 means the NPC is outside the current Summoning roster.
     */
    public static int soundFor(int npcId) {
        switch (npcId) {
            case 6829: // Spirit wolf: lore_wolf_growl
                return 4141;
            case 6825: // Dreadfowl: lore_dreadfowl (general/voice)
                return 4197;
            case 6841: // Spirit spider: lore_spider (general)
                return 4375;
            case 6806: // Thorny snail: lore_snail
                return 4314;
            case 6796: // Granite crab: lore_rockcrab (general)
                return 4377;
            case 7331: // Spirit mosquito: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 6831: // Desert wyrm: lore_desert_worm_burrow_down
                return 4297;
            case 6837: // Spirit scorpion: preserve cache-proven 667 values
                return 4315;
            case 7361: // Spirit Tz-Kih: lore_tz-kih_spawn
                return 4677;
            case 6847: // Albino rat: lore_giant_rat (general)
                return 4266;
            case 6994: // Spirit kalphite: lore_kalphite (general)
                return 4178;
            case 6871: // Compost mound: lore_compost
                return 4155;
            case 7353: // Giant chinchompa: lore_chinchompa
                return 4601;
            case 6835: // Vampire bat: lore_vampire_bat
                return 4245;
            case 6845: // Honey badger: lore_badger
                return 4165;
            case 6808: // Beaver: lore_beaver
                return 4316;
            case 7370: // Void ravager: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 7367: // Void shifter: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 7333: // Void spinner: lore_spinner_spin
                return 4685;
            case 7351: // Void torcher: lore_torcher_spawn
                return 4694;
            case 6853: // Bronze minotaur: shared lore_minotaur
            case 6855: // Iron minotaur
            case 6857: // Steel minotaur
            case 6859: // Mithril minotaur
            case 6861: // Adamant minotaur
            case 6863: // Rune minotaur
                return 4339;
            case 6867: // Bull ant: lore_giant_ant (general)
                return 4264;
            case 6851: // Macaw: lore_macaw (general)
                return 4241;
            case 6833: // Evil turnip: lore_evilturnip
                return 4312;
            case 6875: // Spirit cockatrice: variant mapping unresolved in c3x
            case 6877: // Spirit guthatrice
            case 6879: // Spirit saratrice
            case 6881: // Spirit zamatrice
            case 6883: // Spirit pengatrice
            case 6885: // Spirit coraxatrice
            case 6887: // Spirit vulatrice
                return GENERIC_SUMMON_SOUND;
            case 7377: // Pyrelord: lore_pyrefiend_spawn
                return 4620;
            case 6824: // Magpie: lore_magpie (general)
                return 4199;
            case 6843: // Bloated leech: no general cue; base candidates excluded
                return GENERIC_SUMMON_SOUND;
            case 6794: // Spirit terrorbird: base candidates excluded
                return GENERIC_SUMMON_SOUND;
            case 6818: // Abyssal parasite: no exact presentation cue
                return GENERIC_SUMMON_SOUND;
            case 6992: // Spirit jelly: no general cue
                return GENERIC_SUMMON_SOUND;
            case 6991: // Ibis: no exact familiar voice cue
                return GENERIC_SUMMON_SOUND;
            case 7363: // Spirit graahk: lore_graahk
                return 4671;
            case 7365: // Spirit kyatt: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 7337: // Spirit larupia: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 6809: // Karamthulhu overlord: lore_karamthulhu_spawn
                return 4254;
            case 6865: // Smoke devil: lore_dust_devil
                return 4293;
            case 6820: // Abyssal lurker: lore_abyssal_lurker (general)
                return 4247;
            case 6802: // Spirit cobra: snake general/death alias unresolved
                return GENERIC_SUMMON_SOUND;
            case 6827: // Stranger plant: general
                return 4143;
            case 6889: // Barker toad: lore_barker_toad (general)
                return 4329;
            case 6815: // War tortoise: lore_war_turtle
                return 4333;
            case 6813: // Bunyip: lore_bunyip
                return 4183;
            case 6817: // Fruit bat: lore_fruit_bat
                return 4245;
            case 7372: // Ravenous locust: lore_locust (general)
                return 4231;
            case 6839: // Arctic bear: lore_polar_bear
                return 4211;
            case 8575: // Phoenix: c3x sound identity unresolved
                return GENERIC_SUMMON_SOUND;
            case 7345: // Obsidian golem: lore_obsidian_golem_spawn
                return 4682;
            case 6849: // Granite lobster: lore_lobster
                return 4150;
            case 6798: // Praying mantis: lore_preying_mantis (general/action)
                return 4288;
            case 7335: // Forge regent: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 7347: // Talon beast: lore_nailbeast
                return 4613;
            case 6800: // Giant ent: lore_ent
                return 4322;
            case 7355: // Fire titan: lore_fire_titan_spawn
                return 4699;
            case 7359: // Ice titan: lore_ice_titan_spawn
                return 4706;
            case 7357: // Moss titan: lore_moss_titan_spawn
                return 4626;
            case 6811: // Hydra: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 6804: // Spirit dagannoth: no general cue in c3x
                return GENERIC_SUMMON_SOUND;
            case 7341: // Lava titan: lore_lava_titan_spawn
                return 4647;
            case 7329: // Swamp titan: lore_swamp_titan_spawn
                return 4683;
            case 6822: // Unicorn stallion: lore_unicorn
                return 4366;
            case 7339: // Geyser titan: lore_geyser_titan_spawn
                return 4659;
            case 6869: // Wolpertinger: lore_wolpertinger
                return 4272;
            case 7349: // Abyssal titan: lore_abyssal_titan_spawn
                return 4656;
            case 7375: // Iron titan: lore_iron_titan_spawn
                return 4646;
            case 6873: // Pack yak: lore_pack_yak
                return 4253;
            case 7343: // Steel titan: lore_steel_titan_spawn
                return 4638;
            default:
                return -1;
        }
    }

    /**
     * The QC3 presentation cues are short synth samples rather than cache-authored loop tracks.
     * They are therefore played as movement one-shots by the NPC sound path. Existing Spirit
     * scorpion cache cues are excluded.
     */
    public static boolean isProvisionalPresentationSound(int npcId, int soundId) {
        return npcId != 6837 && soundFor(npcId) == soundId;
    }

    /** Returns whether the client must suppress an idle/ready cue for this familiar. */
    public static boolean suppressIdleSound(int npcId) {
        return isProvisionalFamiliar(npcId);
    }

    private static boolean isProvisionalFamiliar(int npcId) {
        return npcId != 6837 && soundFor(npcId) != -1;
    }
}
