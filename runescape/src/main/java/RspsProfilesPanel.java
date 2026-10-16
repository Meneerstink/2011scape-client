import rsps.runelite.ColorScheme;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.*;

/** Six compact local profiles. Construct and interact on the EDT. */
final class RspsProfilesPanel extends JPanel {
    private static final Color BLUE = new Color(0x1e6fd9), EMPTY = new Color(0x2a3a55);
    private final JTextField notes = RspsScriptTools.field();
    private final JButton[] loads = new JButton[RspsProfiles.COUNT];
    private final JPanel[] cells = new JPanel[RspsProfiles.COUNT];
    private final JTextField[] names = new JTextField[RspsProfiles.COUNT];
    private final String[] nameBases = new String[RspsProfiles.COUNT];
    private final boolean[] renaming = new boolean[RspsProfiles.COUNT];
    private final Timer expiry = new Timer(3000, e -> cancelConfirmation());
    private boolean refreshing, dirtyNotes;
    private String notesBase = "";
    private int armedSave;
    private long deadline, armedRevision;

    RspsProfilesPanel(Runnable openWiki) {
        RspsScriptTools.column(this);
        setName("scripts.profiles");
        JPanel presets = new JPanel(new GridLayout(1, RspsProfiles.COUNT, 2, 0));
        presets.setOpaque(false);
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        presets.setMinimumSize(new Dimension(0, 22));
        presets.setPreferredSize(new Dimension(200, 22));
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        for (int i = 0; i < RspsProfiles.COUNT; i++) {
            final int index = i, n = i + 1;
            JButton load = RspsScriptTools.button(Integer.toString(n));
            load.setName("profiles.load" + n);
            load.setMargin(new Insets(1, 1, 1, 1));
            load.setOpaque(true);
            load.addActionListener(e -> {
                if (refreshing) return;
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) { saveProfile(n); return; }
                if (!saveNotes()) return;
                clearConfirmation();
                boolean exists = RspsProfiles.exists(n);
                boolean loaded = exists && RspsProfiles.load(n);
                refresh();
                if (!loaded) load.setToolTipText(exists ? "Preset " + n + " could not be loaded." : emptyHint(n));
            });
            load.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) beginRename(index);
                }
            });
            loads[i] = load;
            JPanel cell = new JPanel(new BorderLayout());
            cell.setOpaque(false);
            cell.setMinimumSize(new Dimension(0, 22));
            cell.add(load, BorderLayout.CENTER);
            cells[i] = cell;
            JTextField field = RspsScriptTools.field();
            field.setName("profiles.name" + n);
            field.addActionListener(e -> saveName(index));
            field.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { saveName(index); } });
            field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRename");
            field.getActionMap().put("cancelRename", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { endRename(index); refresh(); }
            });
            names[i] = field;
            presets.add(cell);
        }
        add(presets);
        JLabel hint = RspsScriptTools.label("Click load \u00b7 Shift+click save \u00b7 Right-click name");
        hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hint.setPreferredSize(new Dimension(180, 16));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        hint.setToolTipText(RspsScriptTools.tooltip(hint.getText()));
        add(hint);
        add(Box.createVerticalStrut(3));
        add(RspsScriptTools.label("Notes"));
        notes.setName("profiles.notes");
        notes.setToolTipText("Personal notes. Enter or leaving the field saves; Esc reloads the saved notes.");
        notes.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { if (!refreshing) { dirtyNotes = true; notes.setForeground(Color.WHITE); } }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        notes.addActionListener(e -> saveNotes());
        notes.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { saveNotes(); } });
        notes.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "reloadNotes");
        notes.getActionMap().put("reloadNotes", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dirtyNotes = false; refresh(); }
        });
        add(notes);
        add(Box.createVerticalStrut(3));
        JButton wiki = RspsScriptTools.button("Open Wiki (cheat sheet)");
        wiki.setName("profiles.wiki");
        wiki.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wiki.setForeground(new Color(0xe8d44d));
        wiki.setOpaque(true);
        wiki.setToolTipText("Open the script and hotkey cheat sheet.");
        wiki.setEnabled(openWiki != null);
        wiki.addActionListener(e -> { if (!refreshing && openWiki != null) openWiki.run(); });
        add(wiki);
        expiry.setRepeats(false);
        RspsScriptTools.watch(this, true);
        refresh();
    }

    private static String emptyHint(int n) { return "Preset " + n + " is empty - Shift+click saves"; }

    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        refreshing = true;
        try {
            if (!dirtyNotes) {
                notesBase = RspsProfiles.notes();
                if (!notesBase.equals(notes.getText())) notes.setText(notesBase);
                notes.setForeground(Color.WHITE);
                notes.setToolTipText("Personal notes. Enter or leaving the field saves; Esc reloads the saved notes.");
            }
            if (armedSave > 0 && (System.nanoTime() >= deadline || armedRevision != RspsScriptTools.profileRevision() || !RspsProfiles.exists(armedSave))) clearConfirmation();
            for (int i = 0; i < loads.length; i++) {
                int n = i + 1;
                String name = RspsProfiles.name(n), basic = "Preset " + n;
                String text = basic.equals(name) || name.isEmpty() ? Integer.toString(n) : name;
                loads[i].setText(armedSave == n ? "Sure?" : text);
                boolean exists = RspsProfiles.exists(n);
                loads[i].setEnabled(true);
                loads[i].setBackground(exists ? BLUE : EMPTY);
                loads[i].setForeground(exists ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                loads[i].setToolTipText(RspsScriptTools.tooltip(name + ". " + (exists
                    ? armedSave == n ? "Shift+click again within 3 seconds to replace it; ordinary click loads it."
                    : "Click to load; Shift+click twice within 3 seconds to replace; right-click to rename."
                    : emptyHint(n) + ". Right-click to rename.")));
            }
        } finally { refreshing = false; }
        RspsScriptTools.fit(this);
    }

    private boolean saveNotes() {
        if (refreshing || !dirtyNotes) return true;
        if (!notesBase.equals(RspsProfiles.notes())) {
            notes.setForeground(ColorScheme.BRAND_ORANGE);
            notes.setToolTipText("Notes changed elsewhere; draft not saved. Esc reloads them before loading or saving a preset.");
            return false;
        }
        String value = notes.getText();
        dirtyNotes = false;
        if (!value.equals(notesBase)) RspsProfiles.setNotes(value);
        notesBase = RspsProfiles.notes();
        return true;
    }

    private void saveProfile(int n) {
        if (refreshing || !saveNotes()) return;
        boolean confirmed = armedSave == n && System.nanoTime() < deadline && armedRevision == RspsScriptTools.profileRevision();
        if (RspsProfiles.exists(n) && !confirmed) {
            armedSave = n;
            armedRevision = RspsScriptTools.profileRevision();
            deadline = System.nanoTime() + 3000000000L;
            expiry.restart();
            refresh();
            return;
        }
        clearConfirmation();
        RspsProfiles.save(n);
        refresh();
    }
    private void clearConfirmation() { armedSave = 0; expiry.stop(); }
    private void cancelConfirmation() { clearConfirmation(); refresh(); }

    private void beginRename(int i) {
        if (refreshing || renaming[i]) return;
        clearConfirmation();
        renaming[i] = true;
        names[i].setToolTipText("Enter or leaving the field saves; Esc cancels. Blank restores the default name.");
        nameBases[i] = RspsProfiles.name(i + 1);
        names[i].setText(nameBases[i].equals("Preset " + (i + 1)) ? "" : nameBases[i]);
        cells[i].remove(loads[i]);
        cells[i].add(names[i], BorderLayout.CENTER);
        cells[i].revalidate(); cells[i].repaint();
        names[i].requestFocusInWindow(); names[i].selectAll();
        refresh();
    }
    private void saveName(int i) {
        if (refreshing || !renaming[i]) return;
        if (!nameBases[i].equals(RspsProfiles.name(i + 1))) {
            names[i].setToolTipText("Name changed elsewhere; draft not saved. Esc reloads it.");
            return;
        }
        String value = names[i].getText();
        endRename(i);
        if (!value.equals(nameBases[i]) && !(value.trim().isEmpty() && nameBases[i].equals("Preset " + (i + 1)))) RspsProfiles.setName(i + 1, value);
        refresh();
    }
    private void endRename(int i) {
        renaming[i] = false;
        cells[i].remove(names[i]); cells[i].add(loads[i], BorderLayout.CENTER);
        cells[i].revalidate(); cells[i].repaint();
    }
    @Override public void removeNotify() { clearConfirmation(); super.removeNotify(); }
    @Override public Dimension getPreferredSize() { return new Dimension(200, super.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
}



