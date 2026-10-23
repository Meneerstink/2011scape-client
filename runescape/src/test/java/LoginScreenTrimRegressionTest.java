/**
 * Guards N1 / N120 (owner decision PWD: "Recover Password" stays hidden): the login box 596 loads without the Jagex account
 * links (Recover Your Password, Facebook, Create Account Now) and ends under the Log In button.
 */
public final class LoginScreenTrimRegressionTest {

    public static void main(String[] args) {
        for (int slot : new int[] {47, 48, 49, 50, 51, 52, 53, 54, 61}) {
            Component c = new Component();
            LoginScreenTrim.apply(596, slot, c);
            require(c.hidden, "596:" + slot + " hidden");
        }
        for (int slot : new int[] {36, 39, 40, 41, 42, 45}) {
            Component c = new Component();
            LoginScreenTrim.apply(596, slot, c);
            require(!c.hidden, "596:" + slot + " (title, username, password, Log In) stays");
        }
        for (int slot : new int[] {4, 5}) {
            Component c = new Component();
            c.originalHeight = 305;
            LoginScreenTrim.apply(596, slot, c);
            require(c.originalHeight == 192, "596:" + slot + " box ends under Log In (" + c.originalHeight + ")");
        }
        Component backdrop = new Component();
        backdrop.originalHeight = 326;
        LoginScreenTrim.apply(596, 1, backdrop);
        require(backdrop.originalHeight == 213, "backdrop 596:1 shrinks with the box (" + backdrop.originalHeight + ")");
        // The Log In button (596:45 at y 149, 26 high) still fits with room under it.
        require(149 + 26 + 17 == LoginScreenTrim.HEIGHT, "17 px under the Log In button");
        // Other interfaces are never touched.
        Component other = new Component();
        other.originalHeight = 305;
        LoginScreenTrim.apply(597, 47, other);
        LoginScreenTrim.apply(597, 4, other);
        require(!other.hidden && other.originalHeight == 305, "other interfaces unchanged");
        System.out.println("LoginScreenTrimRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
