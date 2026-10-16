import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 78Legacy v3 - the sidebar's key binding button (RuneLite's HotkeyButton, owner request 2026-10-11).
 *
 * Shows the binding ("Shift+F6", "Q", "None"). Click it, then press a key (with Ctrl/Alt/Shift if you want): that becomes
 * the binding. Escape, Backspace or Delete while it waits = None; a right-click = None; clicking elsewhere cancels.
 * Keys the game client cannot read (e.g. some numpad keys) are refused with a short note on the button.
 */
final class RspsKeybindButton extends JButton {

    /** Gets the new binding ("CTRL+SHIFT+F6" / "NONE"). */
    interface Listener {
        void changed(String bind);
    }

    private String value;
    private boolean waiting;
    private final Listener listener;

    RspsKeybindButton(String bind, Listener listener) {
        this.listener = listener;
        this.value = bind == null || bind.isEmpty() ? RspsKeyBind.NONE : bind;
        setFont(FontManager.getRunescapeSmallFont());
        setFocusPainted(false);
        setMargin(new Insets(2, 6, 2, 6));
        setFocusTraversalKeysEnabled(false); // Tab can be a binding too
        setToolTipText("<html>Click, then press a key (Ctrl/Alt/Shift allowed).<br>Esc / Backspace or right-click = None.</html>");
        addActionListener(e -> {
            if (!waiting) {
                waiting = true;
                show("Press a key…", ColorScheme.BRAND_ORANGE);
                requestFocusInWindow();
                listenForClicksElsewhere();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    waiting = false;
                    commit(RspsKeyBind.NONE);
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!waiting) {
                    return;
                }
                e.consume();
                int vk = e.getKeyCode();
                int mods = RspsKeyBind.modifiersOf(e);
                if (RspsKeyBind.isModifierKey(vk)) {
                    show(RspsKeyBind.displayModifiers(mods) + "…", ColorScheme.BRAND_ORANGE);
                    return;
                }
                waiting = false;
                if (mods == 0 && (vk == KeyEvent.VK_ESCAPE || vk == KeyEvent.VK_BACK_SPACE || vk == KeyEvent.VK_DELETE)) {
                    commit(RspsKeyBind.NONE);
                    return;
                }
                boolean console = vk == KeyEvent.VK_BACK_QUOTE || vk == KeyEvent.VK_DEAD_GRAVE;
                if (!RspsKeyBind.supported(vk) || console) {
                    show(console ? "Console key" : "Key not usable", ColorScheme.PROGRESS_ERROR_COLOR);
                    stopListening();
                    javax.swing.Timer t = new javax.swing.Timer(1500, ev -> showValue());
                    t.setRepeats(false);
                    t.start();
                    return;
                }
                commit(RspsKeyBind.format(vk, mods));
            }

            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                stopListening();
                if (waiting) {
                    waiting = false;
                    showValue();
                }
            }
        });
        showValue();
    }

    /** While waiting for a key: a mouse press anywhere else (even on a label that takes no focus) cancels (v3 test). */
    private java.awt.event.AWTEventListener clickElsewhere;

    private void listenForClicksElsewhere() {
        stopListening();
        final java.awt.event.AWTEventListener[] self = new java.awt.event.AWTEventListener[1];
        self[0] = event -> {
            if (event.getID() == MouseEvent.MOUSE_PRESSED && event.getSource() != RspsKeybindButton.this && waiting) {
                waiting = false;
                showValue();
                // Remove exactly this listener, never a newer one installed meanwhile (Codex review v3 #3, finding 2).
                SwingUtilities.invokeLater(() -> stopListening(self[0]));
            }
        };
        clickElsewhere = self[0];
        try {
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(clickElsewhere, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        } catch (SecurityException ignored) {
            clickElsewhere = null; // focus loss still cancels
        }
    }

    private void stopListening() {
        stopListening(clickElsewhere);
    }

    private void stopListening(java.awt.event.AWTEventListener listener) {
        if (listener != null) {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            if (clickElsewhere == listener) {
                clickElsewhere = null;
            }
        }
    }

    /** The server's push (or a reset) changed the binding: show it, unless the player is busy choosing a key. */
    void setValue(String bind) {
        String v = bind == null || bind.isEmpty() ? RspsKeyBind.NONE : bind;
        if (!v.equals(value)) {
            value = v;
            if (!waiting) {
                showValue();
            }
        }
    }

    String getValue() {
        return value;
    }

    private void commit(String bind) {
        stopListening();
        value = bind;
        showValue();
        if (listener != null) {
            listener.changed(bind);
        }
    }

    private void showValue() {
        show(RspsKeyBind.display(value), RspsKeyBind.parse(value) == null ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
    }

    private void show(String text, Color colour) {
        setText(text);
        setForeground(colour);
        setPreferredSize(null);
        Dimension d = getPreferredSize();
        setPreferredSize(new Dimension(Math.max(84, d.width), 25));
        revalidate();
    }
}
