/**
 * Guards A33 (owner 2026-10-11): the Chatbox plugin's "Friend login/logout messages" (on = the 667 client's own lines) and
 * "Notify on private message" (RuneLite notifyOnPM, default off, never while the game window is active).
 */
public final class ChatNoticesRegressionTest {

    public static void main(String[] args) {
        RspsHudConfig.resetToDefaults();
        require(RspsChatNotices.friendLines(), "friend login/logout lines on by default (what the client always did)");
        require(!RspsHudConfig.bool(RspsChatNotices.NOTIFY_PM_KEY), "private-message notification off by default (RuneLite)");

        RspsHudConfig.put(RspsChatNotices.FRIEND_LOGIN_KEY, "false");
        require(!RspsChatNotices.friendLines(), "friend login/logout lines off");
        RspsHudConfig.put(RspsChatNotices.FRIEND_LOGIN_KEY, "true");
        require(RspsChatNotices.friendLines(), "friend login/logout lines on again");

        RspsChatNotices.lastNotification = null;
        RspsChatNotices.privateMessage("<img=0>Anudd", "hi there");
        require(RspsChatNotices.lastNotification == null, "notification off: nothing");

        RspsHudConfig.put(RspsChatNotices.NOTIFY_PM_KEY, "true");
        RspsChatNotices.privateMessage("<img=0>Anudd", "hi <col=ff0000>there</col>");
        require("From Anudd: hi there".equals(RspsChatNotices.lastNotification), "no window active (headless): notified, tags removed (" + RspsChatNotices.lastNotification + ")");

        // Chat timestamps: only on the chatbox's script-created line components, only while on, before the whole line.
        Component chatLine = new Component();
        chatLine.slot = RspsChatTimestamps.CHAT_LAYER;
        chatLine.id = 3;
        ChatLine line = new ChatLine(2, 0, "Anudd", "Anudd", "Anudd", null, -1, "hi");
        line.time = 1_700_000_000_000L;
        RspsChatTimestamps.reading(line);
        require("Anudd: hi".equals(RspsChatTimestamps.line(chatLine, "Anudd: hi")), "timestamps off by default");
        RspsHudConfig.put(RspsChatTimestamps.KEY, "true");
        String stamped = RspsChatTimestamps.line(chatLine, "Anudd: hi");
        require(stamped.equals(RspsChatTimestamps.format(line.time) + " Anudd: hi") && stamped.matches("\\[\\d\\d:\\d\\d\\] Anudd: hi"), "[HH:mm] in front (" + stamped + ")");
        Component cacheText = new Component();
        cacheText.slot = RspsChatTimestamps.CHAT_LAYER;
        cacheText.id = -1;
        require("x".equals(RspsChatTimestamps.line(cacheText, "x")), "the chat layer's own cache component is not a line");
        Component elsewhere = new Component();
        elsewhere.slot = (754 << 16) | 3;
        elsewhere.id = 0;
        require("x".equals(RspsChatTimestamps.line(elsewhere, "x")), "other interfaces untouched");
        require("".equals(RspsChatTimestamps.line(chatLine, "")), "an empty line stays empty");

        // The settings live in the Chatbox plugin of the sidebar.
        require(RspsHudConfig.setting(RspsChatNotices.FRIEND_LOGIN_KEY) != null && RspsHudConfig.setting(RspsChatNotices.NOTIFY_PM_KEY) != null, "both settings are on the Chatbox page");
        System.out.println("ChatNoticesRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
