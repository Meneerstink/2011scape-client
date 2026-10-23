import com.jagex.graphics.awt.DesktopDisplay;

import java.awt.Frame;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;

/**
 * A33 (owner 2026-10-11: chat settings findable + friend login/logout messages + a private-message notification), in the 78
 * Plugins sidebar's Chatbox entry:
 * <ul>
 *   <li>"Friend login/logout messages" (OSRS Settings > Chat name): the 667 client's own "Name has logged in." / "... logged
 *       out." lines (FriendNotification, 5 s after the change) on or off; on is what the client always did.</li>
 *   <li>"Notify on private message" (RuneLite Chat Notifications notifyOnPM, default off): while the game window is not the
 *       active window, a received private message shows a Windows notification with the sender and flashes the taskbar
 *       button (RuneLite Notifier: tray message + request focus, never while the window is focused).</li>
 * </ul>
 */
final class RspsChatNotices {

    static final String FRIEND_LOGIN_KEY = "chatbox.friendLogin";
    static final String NOTIFY_PM_KEY = "chatbox.notifyOnPM";

    /** Last notification text (tests). */
    static String lastNotification;

    private static TrayIcon tray;

    private RspsChatNotices() {
    }

    /** The two FriendNotification loops (client / MainLogicManager): print the login/logout line? */
    static boolean friendLines() {
        return RspsHudConfig.bool(FRIEND_LOGIN_KEY);
    }

    /** ServerConnectionReader: a private message (or quick chat) from [name] arrived and was added to the chat. */
    static void privateMessage(String name, String message) {
        if (!RspsHudConfig.bool(NOTIFY_PM_KEY)) {
            return;
        }
        Frame window = DesktopDisplay.window();
        if (window != null && window.isFocused()) {
            return;
        }
        String clean = name == null ? "" : name.replaceAll("<[^>]*>", "");
        String text = message == null ? "" : message.replaceAll("<[^>]*>", "");
        lastNotification = "From " + clean + ": " + text;
        java.awt.EventQueue.invokeLater(() -> show(window, "78 - private message", lastNotification));
    }

    private static void show(Frame window, String title, String text) {
        try {
            if (SystemTray.isSupported()) {
                if (tray == null) {
                    java.awt.Image icon = window != null && window.getIconImage() != null ? window.getIconImage()
                        : new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    tray = new TrayIcon(icon, "78");
                    tray.setImageAutoSize(true);
                    SystemTray.getSystemTray().add(tray);
                }
                tray.displayMessage(title, text, TrayIcon.MessageType.NONE);
            }
        } catch (Exception | Error noTray) {
            // No tray on this desktop: the taskbar flash below still tells the player.
        }
        try {
            if (window != null && Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                Taskbar.getTaskbar().requestWindowUserAttention(window);
            }
        } catch (Exception | Error noTaskbar) {
            // Headless or unsupported: nothing more to do.
        }
    }
}
