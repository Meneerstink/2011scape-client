/**
 * RSPS (owner buglist E7, the bank must work like OSRS): an "Enter amount" box takes OSRS's suffixes. The 667 key script (CS2 112)
 * already turns K into 000 and M into 000000 before RESUME_COUNTDIALOG; OSRS also accepts B for billions ("1b"), and an amount
 * past the largest whole number is taken as the largest (OSRS caps "All"/huge X amounts at 2,147,483,647).
 */
public final class RspsCountInput {
    private RspsCountInput() {
    }

    public static String expand(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim().replace("B", "000000000").replace("b", "000000000");
        if (text.isEmpty()) {
            return text;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return text;
            }
        }
        int start = 0;
        while (start < text.length() - 1 && text.charAt(start) == '0') {
            start++;
        }
        String digits = text.substring(start);
        if (digits.length() > 10 || Long.parseLong(digits) > Integer.MAX_VALUE) {
            return Integer.toString(Integer.MAX_VALUE);
        }
        return digits;
    }
}
