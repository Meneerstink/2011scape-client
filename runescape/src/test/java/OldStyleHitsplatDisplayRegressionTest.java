public final class OldStyleHitsplatDisplayRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        check(OldStyleHitsplatDisplay.value(200) == 20, "200 must render as 20");
        check(OldStyleHitsplatDisplay.value(10) == 1, "10 must render as 1");
        check(OldStyleHitsplatDisplay.value(9) == 1, "positive sub-ten damage must remain visible");
        check(OldStyleHitsplatDisplay.value(1) == 1, "one raw damage must remain visible");
        check(OldStyleHitsplatDisplay.value(0) == 0, "zero must remain zero");
        check(OldStyleHitsplatDisplay.value(-1) == -1, "negative sentinel must remain unchanged");
        check(OldStyleHitsplatDisplay.value(Integer.MAX_VALUE) == 214748365,
                "maximum integer conversion must not overflow");

        int raw = 200;
        OldStyleHitsplatDisplay.value(raw);
        check(raw == 200, "display conversion must not mutate raw damage");

        check(OldStyleStatDisplay.value(OldStyleStatDisplay.HITPOINTS, 990) == 99,
                "990 hitpoints must render as 99");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, 990) == 99,
                "990 prayer points must render as 99");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.HITPOINTS, 500) == 50,
                "500 hitpoints must render as 50");
        check(OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, 1) == 1,
                "positive prayer must remain visible");
        check(OldStyleStatDisplay.value(0, 99) == 99,
                "attack and other skills must remain unchanged");

        int rawPrayer = 990;
        OldStyleStatDisplay.value(OldStyleStatDisplay.PRAYER, rawPrayer);
        check(rawPrayer == 990, "display conversion must not mutate raw prayer");

        System.out.println("Old-style hitsplat display regression passed: " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
