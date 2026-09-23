package com.jagex.game.runetek6.config.npctype;

/**
 * Bounded roster regression for the provisional QC3 familiar audio bridge.
 * Run with: ./gradlew :runescape:test --tests
 * com.jagex.game.runetek6.config.npctype.SummoningNpcAudioRegressionTest
 */
public final class SummoningNpcAudioRegressionTest {

    private static final int[] FAMILIAR_NPCS = {
        6829, 6825, 6841, 6806, 6796, 7331, 6831, 6837, 7361, 6847,
        6994, 6871, 7353, 6835, 6845, 6808, 7370, 7367, 7333, 7351,
        6853, 6867, 6851, 6833, 6875, 6877, 6879, 6881, 6883, 6885,
        6887, 6855, 7377, 6824, 6843, 6794, 6818, 6992, 6991, 6857,
        7363, 7365, 7337, 6809, 6865, 6820, 6802, 6827, 6889, 6859,
        6815, 6813, 6817, 7372, 6839, 8575, 7345, 6849, 6798, 7335,
        6861, 7347, 6800, 7355, 7359, 7357, 6811, 6804, 7341, 7329,
        6863, 6822, 7339, 6869, 7349, 7375, 6873, 7343
    };

    public static void main(String[] args) {
        if (FAMILIAR_NPCS.length != 78) {
            throw new AssertionError("Expected 78 familiar NPC IDs, got " + FAMILIAR_NPCS.length);
        }

        for (int npcId : FAMILIAR_NPCS) {
            if (SummoningNpcAudioOverrides.soundFor(npcId) == -1) {
                throw new AssertionError("Missing provisional c3x sound for NPC " + npcId);
            }

            NPCType type = new NPCType();
            type.id = npcId;
            SummoningNpcAudioOverrides.apply(type);

            if (type.walkSound == -1 || type.runSound == -1 || type.crawlSound == -1) {
                throw new AssertionError("Incomplete movement sound fields for NPC " + npcId);
            }
            if (npcId != 6837 && type.readySound != -1) {
                throw new AssertionError("Provisional familiar received an idle sound for NPC " + npcId);
            }
            if (type.soundRangeMax != 5) {
                throw new AssertionError("Unexpected sound range for NPC " + npcId + ": " + type.soundRangeMax);
            }
            if (npcId != 6837 && type.soundVolume != 61) {
                throw new AssertionError("Unexpected provisional loop volume for NPC " + npcId + ": " + type.soundVolume);
            }
        }

        NPCType provenScorpion = new NPCType();
        provenScorpion.id = 6837;
        provenScorpion.readySound = 2901;
        provenScorpion.walkSound = 4334;
        provenScorpion.runSound = 4334;
        provenScorpion.crawlSound = 4334;
        provenScorpion.soundRangeMax = 5;
        SummoningNpcAudioOverrides.apply(provenScorpion);
        if (provenScorpion.readySound != 2901 || provenScorpion.walkSound != 4334) {
            throw new AssertionError("Proven Spirit scorpion cache sounds were overwritten");
        }

        System.out.println("SummoningNpcAudioRegressionTest: PASS (78 familiar IDs covered)");
    }
}
