/**
 * 78Legacy PK hotkeys v2: spell and prayer tables (generated from the server's SpellbookData.kt, prayer.kt and
 * AncientCurse.kt by Claude, 2026-10-11 - regenerate with the same script instead of editing by hand).
 * Option ids must match ClientHudConfig.kt on the server (it validates every value the sidebar sends).
 */
final class RspsPkData {

    /** Spell ids (setting values), display names, spellbook interface and component. */
    static final String[] SPELL_IDS = {"ICE_RUSH", "ICE_BLITZ", "ICE_BURST", "ICE_BARRAGE", "BLOOD_RUSH", "BLOOD_BLITZ", "BLOOD_BURST", "BLOOD_BARRAGE", "SMOKE_RUSH", "SMOKE_BLITZ", "SMOKE_BURST", "SMOKE_BARRAGE", "SHADOW_RUSH", "SHADOW_BLITZ", "SHADOW_BURST", "SHADOW_BARRAGE", "MIASMIC_RUSH", "MIASMIC_BLITZ", "MIASMIC_BURST", "MIASMIC_BARRAGE", "BIND", "SNARE", "ENTANGLE", "TELEPORT_BLOCK", "WIND_SURGE", "WATER_SURGE", "EARTH_SURGE", "FIRE_SURGE", "VENGEANCE_OTHER", "HEAL_OTHER", "STAT_SPY"};
    static final String[] SPELL_NAMES = {"Ice Rush", "Ice Blitz", "Ice Burst", "Ice Barrage", "Blood Rush", "Blood Blitz", "Blood Burst", "Blood Barrage", "Smoke Rush", "Smoke Blitz", "Smoke Burst", "Smoke Barrage", "Shadow Rush", "Shadow Blitz", "Shadow Burst", "Shadow Barrage", "Miasmic Rush", "Miasmic Blitz", "Miasmic Burst", "Miasmic Barrage", "Bind", "Snare", "Entangle", "Teleport Block", "Wind Surge", "Water Surge", "Earth Surge", "Fire Surge", "Vengeance Other", "Heal Other", "Stat Spy"};
    static final int[] SPELL_BOOK = {193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 193, 192, 192, 192, 192, 192, 192, 192, 192, 430, 430, 430};
    static final int[] SPELL_COMPONENT = {20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 36, 55, 81, 86, 84, 87, 89, 91, 42, 52, 31};

    /** Prayer ids: N_ = normal book, C_ = curses; book 0/1, slot in the 271:8 grid, the client varbit that is 1 while it is on. */
    static final String[] PRAYER_IDS = {"N_THICK_SKIN", "N_BURST_OF_STRENGTH", "N_CLARITY_OF_THOUGHT", "N_SHARP_EYE", "N_MYSTIC_WILL", "N_ROCK_SKIN", "N_SUPERHUMAN_STRENGTH", "N_IMPROVED_REFLEXES", "N_RAPID_RESTORE", "N_RAPID_HEAL", "N_PROTECT_ITEM", "N_HAWK_EYE", "N_MYSTIC_LORE", "N_STEEL_SKIN", "N_ULTIMATE_STRENGTH", "N_INCREDIBLE_REFLEXES", "N_PROTECT_FROM_SUMMONING", "N_PROTECT_FROM_MAGIC", "N_PROTECT_FROM_MISSILES", "N_PROTECT_FROM_MELEE", "N_EAGLE_EYE", "N_MYSTIC_MIGHT", "N_RETRIBUTION", "N_REDEMPTION", "N_SMITE", "N_CHIVALRY", "N_RAPID_RENEWAL", "N_PIETY", "N_RIGOUR", "N_AUGURY", "C_PROTECT_ITEM", "C_SAP_WARRIOR", "C_SAP_RANGER", "C_SAP_MAGE", "C_SAP_SPIRIT", "C_BERSERKER", "C_DEFLECT_SUMMONING", "C_DEFLECT_MAGIC", "C_DEFLECT_MISSILES", "C_DEFLECT_MELEE", "C_LEECH_ATTACK", "C_LEECH_RANGED", "C_LEECH_MAGIC", "C_LEECH_DEFENCE", "C_LEECH_STRENGTH", "C_LEECH_ENERGY", "C_LEECH_SPECIAL_ATTACK", "C_WRATH", "C_SOUL_SPLIT", "C_TURMOIL"};
    static final String[] PRAYER_NAMES = {"Thick Skin", "Burst of Strength", "Clarity of Thought", "Sharp Eye", "Mystic Will", "Rock Skin", "Superhuman Strength", "Improved Reflexes", "Rapid Restore", "Rapid Heal", "Protect Item", "Hawk Eye", "Mystic Lore", "Steel Skin", "Ultimate Strength", "Incredible Reflexes", "Protect Summoning", "Protect Magic", "Protect Missiles", "Protect Melee", "Eagle Eye", "Mystic Might", "Retribution", "Redemption", "Smite", "Chivalry", "Rapid Renewal", "Piety", "Rigour", "Augury", "Protect Item (C)", "Sap Warrior (C)", "Sap Ranger (C)", "Sap Mage (C)", "Sap Spirit (C)", "Berserker (C)", "Deflect Summoning (C)", "Deflect Magic (C)", "Deflect Missiles (C)", "Deflect Melee (C)", "Leech Attack (C)", "Leech Ranged (C)", "Leech Magic (C)", "Leech Defence (C)", "Leech Strength (C)", "Leech Energy (C)", "Leech Special (C)", "Wrath (C)", "Soul Split (C)", "Turmoil (C)"};
    static final int[] PRAYER_BOOK = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    static final int[] PRAYER_SLOT = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
    static final int[] PRAYER_VARBIT = {5942, 5943, 5944, 5960, 5961, 5945, 5946, 5947, 5948, 5949, 5950, 5962, 5963, 5951, 5952, 5953, 5966, 5954, 5955, 5956, 5964, 5965, 5957, 5958, 5959, 5967, 7768, 5968, 7381, 7769, 6820, 6821, 6822, 6823, 6824, 6825, 6826, 6827, 6828, 6829, 6830, 6831, 6832, 6833, 6834, 6835, 6836, 6837, 6838, 6839};

    private RspsPkData() {
    }

    static int spell(String id) {
        for (int i = 0; i < SPELL_IDS.length; i++) if (SPELL_IDS[i].equals(id)) return i;
        return -1;
    }

    static int prayer(String id) {
        for (int i = 0; i < PRAYER_IDS.length; i++) if (PRAYER_IDS[i].equals(id)) return i;
        return -1;
    }

    /** [ids] with "NONE" in front (for the preset choices). */
    static String[] withNone(String[] ids, String none) {
        String[] out = new String[ids.length + 1];
        out[0] = none;
        System.arraycopy(ids, 0, out, 1, ids.length);
        return out;
    }
}
