public final class OldStyleHitsplatDisplayRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        check(OldStyleHitsplatDisplay.value(200) == 200, "200 must remain 200");
        check(OldStyleHitsplatDisplay.value(10) == 10, "10 must remain 10");
        check(OldStyleHitsplatDisplay.value(9) == 9, "single-digit damage must remain visible");
        check(OldStyleHitsplatDisplay.value(1) == 1, "one raw damage must remain visible");
        check(OldStyleHitsplatDisplay.value(0) == 0, "zero must remain zero");
        check(OldStyleHitsplatDisplay.value(-1) == -1, "negative sentinel must remain unchanged");
        check(OldStyleHitsplatDisplay.value(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "maximum integer must remain unchanged");

        int raw = 200;
        OldStyleHitsplatDisplay.value(raw);
        check(raw == 200, "display conversion must not mutate raw damage");

        check(OldStyleStatDisplay.value(OldStyleStatDisplay.HITPOINTS, 99) == 99,
                "99 hitpoints must remain 99");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, 99) == 99,
                "99 prayer points must remain 99");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.HITPOINTS, 50) == 50,
                "50 hitpoints must remain 50");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, 1) == 1,
                "positive prayer must remain visible");
        check(OldStyleStatDisplay.value(0, 99) == 99,
                "attack and other skills must remain unchanged");

        int rawPrayer = 99;
        OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, rawPrayer);
        check(rawPrayer == 99, "display hook must not mutate raw prayer");

        // Orb text boundary. The orbs read already-normalized varbits 7198/9816.
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.HITPOINTS_ORB_TEXT, "99").equals("99"),
                "hitpoints orb must keep 99 as 99");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.PRAYER_ORB_TEXT, "99").equals("99"),
                "prayer orb must keep 99 as 99");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.HITPOINTS_ORB_TEXT, "1").equals("1"),
                "a single remaining lifepoint must stay visible");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.HITPOINTS_ORB_TEXT, "0").equals("0"),
                "zero lifepoints must render as zero");
        check(OldStyleOrbDisplay.text(-1, (750 << 16) | 5, "99").equals("99"),
                "other interface components must not be rescaled");
        check(OldStyleOrbDisplay.text(3, OldStyleOrbDisplay.HITPOINTS_ORB_TEXT, "99").equals("99"),
                "dynamic children sharing the orb slot must not be rescaled");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.PRAYER_ORB_TEXT, "").equals(""),
                "empty orb text must be left alone");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.PRAYER_ORB_TEXT, "-1").equals("-1"),
                "non-numeric orb text must be left alone");
        check(OldStyleOrbDisplay.text(-1, OldStyleOrbDisplay.PRAYER_ORB_TEXT, "9999999999")
                        .equals("9999999999"),
                "orb text too long to be a stat total must be left alone");

        // RCV-010 A6: hitsplat parts are drawn 10% bigger, rounded to whole pixels, offsets keep their sign.
        check(OldStyleHitsplatDisplay.SIZE_PERCENT == 122, "hitsplats must be 122% of the cache size");
        check(OldStyleHitsplatDisplay.size(20) == 24, "a 20px part must become 24px");
        check(OldStyleHitsplatDisplay.size(12) == 15, "the 12px anchor must become 15px");
        check(OldStyleHitsplatDisplay.size(15) == 18, "15px must round to 18px");
        check(OldStyleHitsplatDisplay.size(0) == 0, "zero must stay zero");
        check(OldStyleHitsplatDisplay.size(-5) == -6, "negative offsets must scale symmetrically");
        check(OldStyleHitsplatDisplay.offsetY(new int[] {0, 20, 40, 60}, 0) == 0,
                "the first visible hitsplat stays on the anchor");
        check(OldStyleHitsplatDisplay.offsetY(new int[] {0, 20, 40, 60}, 1) == -26,
                "the second visible hitsplat uses one scaled stack step");
        check(OldStyleHitsplatDisplay.offsetY(new int[] {0, 20, 40, 60}, 2) == -52,
                "the third visible hitsplat uses two scaled stack steps");

        RecordingSprite part = new RecordingSprite(20, 24);
        com.jagex.graphics.Sprite scaled = ScaledHitsplatSprite.wrap(part);
        check(ScaledHitsplatSprite.wrap(null) == null, "a missing hitsplat part must stay missing");
        check(scaled.getWidth() == 24 && scaled.getHeight() == 29, "wrapped part must report scaled width/height");
        int[] offsets = new int[] {0, 0, 0, 0};
        scaled.projectOffsets(offsets);
        check(offsets[0] == 2 && offsets[1] == -4, "projected offsets must be scaled");
        scaled.render(100, 50);
        check(part.lastWidth == 24 && part.lastHeight == 29 && part.lastX == 100 && part.lastY == 50,
                "opaque draw must use the scaled blit at the same origin");
        scaled.render(7, 8, 0, 0x80FFFFFF, 1);
        check(part.lastWidth == 24 && part.lastColour == 0x80FFFFFF && part.lastMode == 1,
                "fading draw must use the scaled blit with the same colour and mode");

        System.out.println("Old-style hitsplat display regression passed: " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
