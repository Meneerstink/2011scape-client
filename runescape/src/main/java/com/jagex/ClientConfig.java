package com.jagex;

import java.math.BigInteger;

public class ClientConfig {
    public static boolean SKIP_HOST_CHECK = true;
    public static boolean DISABLE_LOBBY = true;
    public static final int RENDER_DISTANCE_MULTIPLIER = 2;
    public static final int FOG_DISTANCE_MULTIPLIER = 2;
    /** JS5 cache signature key. {@code -Drsps.js5Modulus=<hex>} overrides it for a LOCAL test file server only (default = live). */
    public static final BigInteger JS5_MODULUS = modulusProperty("rsps.js5Modulus", "d6808be939bbfd2ec4e96b1581ce3e1144b526e7643a72e3c64fbb902724fbfcf14ab601da6d6f8dbb57d1c369d080d9fc392abeb7886e0076d07f2aea5810e540d2817fd1967e35b39cc95cf7c9170b5fb55f5bf95524b60e938f0d64614bc365b87d66963a8cc8664e32875366099ef297180d01c7c3842162865e11d92299");
    /** Login RSA key. {@code -Drsps.loginModulus=<hex>} overrides it for a LOCAL test server only (default = live). */
    public static BigInteger LOGIN_MODULUS = modulusProperty("rsps.loginModulus", "a1129cdb79665ed39b10d09e8dcfbc373f7e0b8097b98b1ddc4762149734ce8ea6ce59f3b9d76b1bbe10ad30ff67f87232477d7d98cacff3bd0c309e49000452947bb04d5122fd5cb113ce197393b28ef8d27841e0f3584baf9cb3848ba3be11826457c73545953ee00b3dbcd456774392055ac1382b6e4e49e6aa2bf026a28d165e4a59e907e229c5e372f3e486d2044c030f6efcdaa9d5893ab0609ecd41d56aca5af36845158031ed98a1bcb68bea4f7eaf823e00402fc05571dc1c83d9417f0489ec0f986761a793ad2bc493281b917d572400588a4634503e20ba6518c7b9367d959ef7fa6249a11f032d1707ed84bf2cda0ca8960e97295234d65cc2e1");
    public static BigInteger RSA_EXPONENT = new BigInteger("10001", 16);

    /**
     * K-01/K-02: developer mode. Off by default, so a player build is LIVE: no debug console, no client-side QA
     * commands, no orthographic camera. Only an explicit {@code -Drsps.dev=true} (the owner's Launch-RSPS.ps1 passes
     * it through {@code gradlew :runescape:run -Prsps.dev=true}) turns it on. This is deliberately separate from
     * {@code Client.modeWhere}: the RSPS connection topology (game 50015 / JS5 50016) is wired to ModeWhere.LOCAL, and
     * switching modeWhere to LIVE would make the client connect to the applet code base instead.
     */
    public static final boolean DEV_MODE = Boolean.getBoolean("rsps.dev");

    /**
     * Owner 2026-10-11 ("soes cant open the command window", choice "Console voor staff"): the command console opens in dev mode
     * and for staff (moderator rank and up, {@link Client#staffModLevel} from the login response). The server still checks every
     * command's rank; the client-side QA commands stay dev-only.
     */
    public static boolean consoleAllowed() {
        return DEV_MODE || Client.staffModLevel >= 1;
    }

    /**
     * K-05: game/JS5 host. Players (or a remote test box) launch with {@code -Drsps.host=<server ip or name>}; without
     * the property the client stays on the local machine.
     */
    public static final String HOST = hostProperty();

    public static String HOST_ADDRESS = "http://" + HOST;

    /**
     * Owner 2026-10-02: game/login port. The live server listens on 50015; an agent's test server
     * (tools/live/Start-TestServer.ps1) on another port, reached by a test client launched with {@code -Drsps.gamePort=<port>}
     * (gradle {@code -Prsps.gamePort}). JS5 stays on the one read-only file server (50016).
     */
    public static final int GAME_PORT = portProperty("rsps.gamePort", 50015);

    /** K-05: Ctrl+Shift (and world map Ctrl) teleport clicks; the server only accepts {@code tele} from admins. */
    public static final int TELEPORT_MIN_STAFF_LEVEL = 2;

    private static int portProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port < 65536 ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Local-test override of an RSA modulus (hex); falls back to the built-in live key when unset or invalid. */
    private static BigInteger modulusProperty(String name, String fallbackHex) {
        String value = System.getProperty(name);
        if (value != null) {
            try {
                return new BigInteger(value.trim(), 16);
            } catch (NumberFormatException ignored) {
                System.err.println("[ClientConfig] ignoring invalid -D" + name);
            }
        }
        return new BigInteger(fallbackHex, 16);
    }

    private static String hostProperty() {
        String host = System.getProperty("rsps.host");
        if (host == null) {
            return "127.0.0.1";
        }
        host = host.trim();
        return host.isEmpty() ? "127.0.0.1" : host;
    }
}
