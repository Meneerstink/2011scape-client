package rs2.client.loading.library;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * K-03: SHA-256 pins for the native libraries the client downloads from the JS5 DLLS archive (jaclib, jaggl, jagdx,
 * jagmisc, sw3d, hw3d, jagtheora). JS5 itself only checks CRC32 (Whirlpool only when the index carries hashes), so a
 * man-in-the-middle on the update server connection could otherwise hand the client native code to run.
 *
 * <p>Key: the JS5 path the client requests, e.g. {@code windows/x86_64/jaclib.dll}. Value: lowercase hex SHA-256 of
 * the file exactly as served (the bytes written to the native cache folder).</p>
 *
 * <ul>
 * <li>Pinned and matching: loaded.</li>
 * <li>Pinned and different: always refused.</li>
 * <li>Not pinned: refused when {@code -Drsps.natives.strict=true}; otherwise loaded with one warning per file that
 * prints the actual hash, so the owner can copy it into {@link #PINS} after checking it against a trusted cache.</li>
 * </ul>
 *
 * <p>Extra or replacement pins can be passed without a rebuild:
 * {@code -Drsps.natives.pins=windows/x86_64/jaclib.dll=<sha256>;windows/x86_64/jaggl.dll=<sha256>}.</p>
 */
public final class NativeLibraryPins {

    public static final String STRICT_PROPERTY = "rsps.natives.strict";

    public static final String PINS_PROPERTY = "rsps.natives.pins";

    private static final Map<String, String> PINS = new HashMap<>();

    private static final Set<String> warned = new HashSet<>();

    static {
        // TODO(owner): fill in from the trusted cache (the "[natives] no pin" warning prints each hash). Example:
        // PINS.put("windows/x86_64/jaclib.dll", "<64 hex chars>");

        String extra = System.getProperty(PINS_PROPERTY);
        if (extra != null) {
            for (String entry : extra.split(";")) {
                int eq = entry.lastIndexOf('=');
                if (eq <= 0 || eq == entry.length() - 1) {
                    continue;
                }
                PINS.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private NativeLibraryPins() {
        /* empty */
    }

    public static boolean strict() {
        return Boolean.getBoolean(STRICT_PROPERTY);
    }

    /** Returns whether {@code data}, downloaded for JS5 path {@code path}, may be written and loaded. */
    public static boolean verify(String path, byte[] data) {
        String actual = sha256Hex(data);
        if (actual == null) {
            System.out.println("[natives] refused " + path + ": SHA-256 unavailable");
            return false;
        }

        String expected = PINS.get(path);
        if (expected == null) {
            if (strict()) {
                System.out.println("[natives] refused " + path + ": no pinned hash (sha256=" + actual + ", " + STRICT_PROPERTY + "=true)");
                return false;
            }
            synchronized (warned) {
                if (warned.add(path)) {
                    System.out.println("[natives] WARNING no pin for " + path + " sha256=" + actual + " (add it to NativeLibraryPins)");
                }
            }
            return true;
        }

        if (!expected.equals(actual)) {
            System.out.println("[natives] refused " + path + ": sha256=" + actual + " does not match pinned " + expected);
            return false;
        }
        return true;
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return null;
        }
    }
}
