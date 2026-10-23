import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A33 (owner 2026-10-11: "chat-timestamps"): the Chatbox plugin's "Chat timestamps" - RuneLite's Timestamp plugin (format
 * "[HH:mm]", local time, before the whole line). The 667 chatbox script (clientscript 84) builds every line as one text component
 * under the chat layer 137:58: it reads the line's type (CHAT_GETHISTORY_BYUID) and then sets the composed "name: message" text
 * (CC_IF_SETTEXT). The time of the line read last is put in front of that text, so names, colours and the right-click menu (set
 * from the name separately) are unchanged.
 */
final class RspsChatTimestamps {

    static final String KEY = "chatbox.timestamps";
    /** The chatbox's line layer (clientscript 84 creates the lines in 8978490). */
    static final int CHAT_LAYER = (137 << 16) | 58;

    private static long readTime;

    private RspsChatTimestamps() {
    }

    /** CHAT_GETHISTORY_BYUID: the chatbox script starts building this line. */
    static void reading(ChatLine line) {
        readTime = line == null ? 0L : line.time;
    }

    /** CC_IF_SETTEXT on [component]: a chat line gets its time in front when the setting is on. */
    static String line(Component component, String text) {
        if (component == null || component.id == -1 || component.slot != CHAT_LAYER || readTime <= 0L || text == null || text.isEmpty()) {
            return text;
        }
        if (!RspsHudConfig.bool(KEY)) {
            return text;
        }
        return format(readTime) + " " + text;
    }

    static String format(long time) {
        return new SimpleDateFormat("[HH:mm]").format(new Date(time));
    }
}
