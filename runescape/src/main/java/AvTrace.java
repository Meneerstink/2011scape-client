import java.io.File;

/**
 * RSPS RCV-005 client-boundary trace for spot animations and sound effects. Enabled only while the
 * file C:/RSPS/avtrace.on exists at client start; every line goes to stdout (the launcher's
 * client-*.out.log). It records what the client actually decoded and why it ignored something, so a
 * missing graphic or sound can be traced to the server, the packet, or the client.
 */
public final class AvTrace {

    /** On with C:/RSPS/avtrace.on or the debug toolkit's all-traces file C:/RSPS/trace.on (the server's `traces` command writes it). */
    public static final boolean ENABLED = new File("C:/RSPS/avtrace.on").exists() || new File("C:/RSPS/trace.on").exists();

    private AvTrace() {
    }

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[avtrace " + System.currentTimeMillis() + "] " + message);
        }
    }
}
