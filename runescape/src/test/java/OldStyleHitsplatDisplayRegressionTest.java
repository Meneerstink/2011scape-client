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

        System.out.println("Old-style hitsplat display regression passed: " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
