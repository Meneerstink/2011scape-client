import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.lang.ref.WeakReference;
import java.awt.image.BufferedImage;

/** One editable script; construct, refresh and handle user actions on the EDT. */
final class RspsScriptBox extends JPanel {
    private static final Map<Integer, Boolean> FOLDED = new HashMap<Integer, Boolean>();
    private static final Set<Integer> REVEALED = new HashSet<Integer>();
    private static final List<WeakReference<RspsScriptBox>> BOXES = new ArrayList<WeakReference<RspsScriptBox>>();
    private static boolean runListening;
    private boolean flashing;
    private final Timer flashTimer = new Timer(400, e -> { flashing = false; updateBorder(); });

    private final int number;
    private final JButton fold = RspsScriptTools.button("");
    private final JCheckBox on = new JCheckBox();
    private final JLabel title = RspsScriptTools.label("");
    private final RspsKeybindButton key;
    private final JLabel shared = RspsScriptTools.label("");
    private final JTextField rename = RspsScriptTools.field();
    private final JPanel titleArea = new JPanel(new BorderLayout());
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final JPanel body = new JPanel();
    private final JPanel box = new JPanel(new BorderLayout());
    private final DefaultListModel<RspsScripts.Step> lines = new DefaultListModel<RspsScripts.Step>();
    private final JList<RspsScripts.Step> list = new JList<RspsScripts.Step>(lines) {
        @Override public Dimension getPreferredSize() { return new Dimension(180, Math.max(1, lines.size()) * 18); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override public String getToolTipText(MouseEvent e) {
            int index = locationToIndex(e.getPoint());
            Rectangle bounds = index < 0 ? null : getCellBounds(index, index);
            return bounds != null && bounds.contains(e.getPoint()) ? RspsScriptTools.tooltip(lines.get(index).label()) : null;
        }
    };
    private final JPanel edits = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    private final JButton up = RspsScriptTools.button("Up"), down = RspsScriptTools.button("Down"), remove = RspsScriptTools.button("x");
    private final JLabel warning = RspsScriptTools.label("");
    private final JLabel error = RspsScriptTools.label("");
    private final JComboBox<String> type = RspsScriptTools.combo(typeNames());
    private final JPanel addRow = RspsScriptTools.row();
    private final JPanel argument = new JPanel(new BorderLayout(2, 0));
    private final JTextField item = RspsScriptTools.field();
    private final JButton addStep = RspsScriptTools.button("Add");
    private JComboBox<String> choice;
    private String[] choiceValues;
    private List<RspsScripts.Step> displayed = Collections.emptyList();
    private boolean refreshing, renaming;
    private String renameBase;

    RspsScriptBox(int n) {
        if (n < 1 || n > RspsScripts.COUNT) throw new IllegalArgumentException("Invalid script: " + n);
        number = n;
        key = new RspsKeybindButton(RspsHudConfig.get(bindingKey()), bind -> RspsSidebar.set(bindingKey(), bind));
        // The shared key button resets its preferred size when capturing/displaying a key.
        // Keep its existing behavior while fitting this header, including long bindings.
        key.addPropertyChangeListener("preferredSize", e -> {
            Dimension size = key.getPreferredSize();
            if (size.width != 52 || size.height != 20) key.setPreferredSize(new Dimension(52, 20));
        });
        key.setPreferredSize(new Dimension(52, 20));
        key.setMinimumSize(new Dimension(52, 20));
        key.setMargin(new Insets(1, 2, 1, 2));
        key.setFont(FontManager.getRunescapeSmallFont());
        flashTimer.setRepeats(false);
        updateBorder();
        Iterator<WeakReference<RspsScriptBox>> oldBoxes = BOXES.iterator();
        while (oldBoxes.hasNext()) if (oldBoxes.next().get() == null) oldBoxes.remove();
        BOXES.add(new WeakReference<RspsScriptBox>(this));
        if (!runListening) {
            runListening = true;
            RspsScriptRunner.addRunListener(script -> SwingUtilities.invokeLater(() -> flash(script)));
        }
        RspsScriptTools.column(this);
        setName("scripts.script" + n);
        if (!FOLDED.containsKey(n)) FOLDED.put(n, true);
        JPanel header = RspsScriptTools.row();
        JPanel left = new JPanel(new BorderLayout(2, 0));
        left.setOpaque(false);
        RspsSidebar.undecorate(fold);
        fold.setPreferredSize(new Dimension(14, 20));
        fold.setMinimumSize(new Dimension(14, 20));
        fold.addActionListener(e -> { if (!refreshing) { FOLDED.put(number, !folded()); refresh(); } });
        on.setOpaque(false);
        on.setMargin(new Insets(0, 0, 0, 0));
        on.setToolTipText("Enable this script. Its steps stay editable when off.");
        on.addActionListener(e -> {
            if (!refreshing && on.isSelected() != RspsScripts.enabled(number)) RspsScripts.setEnabled(number, on.isSelected());
            refresh();
        });
        left.add(fold, BorderLayout.WEST);
        left.add(on, BorderLayout.CENTER);
        header.add(left, BorderLayout.WEST);
        titleArea.setOpaque(false);
        titleArea.setMinimumSize(new Dimension(0, 22));
        titleArea.add(title, BorderLayout.CENTER);
        header.add(titleArea, BorderLayout.CENTER);
        header.add(key, BorderLayout.EAST);
        MouseAdapter toggle = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || refreshing) return;
                if (e.getSource() == title && e.getClickCount() == 2) beginRename();
                else if (e.getClickCount() == 1 && !renaming) { FOLDED.put(number, !folded()); refresh(); }
            }
        };
        header.addMouseListener(toggle);
        left.addMouseListener(toggle);
        titleArea.addMouseListener(toggle);
        title.addMouseListener(toggle);
        chips.addMouseListener(toggle);
        chips.putClientProperty("toggleListener", toggle);
        rename.addActionListener(e -> saveName());
        rename.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { saveName(); } });
        rename.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRename");
        rename.getActionMap().put("cancelRename", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { endRename(); refresh(); }
        });
        add(header);
        shared.setForeground(ColorScheme.BRAND_ORANGE);
        shared.setPreferredSize(new Dimension(180, 16));
        shared.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        shared.setToolTipText("The first one that fits your prayer book / spellbook runs.");
        shared.addMouseListener(toggle);
        add(shared);
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setPreferredSize(new Dimension(180, 20));
        chips.setMinimumSize(new Dimension(0, 20));
        chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        chips.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { rebuildChips(); }
        });
        add(chips);
        RspsScriptTools.column(body);
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(2, 3, 2, 3)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.setFont(FontManager.getRunescapeSmallFont());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(18);
        list.setToolTipText("");
        list.setCellRenderer(new StepRenderer());
        MouseAdapter drag = new MouseAdapter() {
            private int start = -1;
            private boolean moved;
            private List<RspsScripts.Step> before;
            private int rowAt(Point p) {
                int i = list.locationToIndex(p);
                Rectangle r = i < 0 ? null : list.getCellBounds(i, i);
                return r != null && r.contains(p) ? i : -1;
            }
            @Override public void mousePressed(MouseEvent e) {
                start = SwingUtilities.isLeftMouseButton(e) ? rowAt(e.getPoint()) : -1;
                moved = false;
                before = new ArrayList<RspsScripts.Step>(displayed);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (start < 0) return;
                moved = true;
                int end = rowAt(e.getPoint());
                if (end >= 0) list.setSelectedIndex(end);
            }
            @Override public void mouseReleased(MouseEvent e) {
                int end = rowAt(e.getPoint());
                if (moved && start >= 0 && start < before.size() && end >= 0 && end < before.size() && end != start) {
                    List<RspsScripts.Step> updated = new ArrayList<RspsScripts.Step>(before);
                    updated.add(end, updated.remove(start));
                    replaceSteps(before, updated, end);
                }
                start = -1;
                before = null;
            }
        };
        list.addMouseListener(drag);
        list.addMouseMotionListener(drag);
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !refreshing) { updateEdits(); fit(); } });
        box.add(list, BorderLayout.CENTER);
        edits.setOpaque(false);
        edits.setPreferredSize(new Dimension(100, 22));
        edits.add(up); edits.add(down); edits.add(remove);
        up.setToolTipText("Move the selected step up.");
        down.setToolTipText("Move the selected step down.");
        remove.setToolTipText("Remove the selected step.");
        up.addActionListener(e -> edit(-1));
        down.addActionListener(e -> edit(1));
        remove.addActionListener(e -> edit(0));
        box.add(edits, BorderLayout.SOUTH);
        body.add(box);
        warning.setForeground(ColorScheme.BRAND_ORANGE);
        warning.setPreferredSize(new Dimension(180, 16));
        warning.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        body.add(warning);
        body.add(Box.createVerticalStrut(3));
        argument.setOpaque(false);
        argument.setMinimumSize(new Dimension(0, 22));
        argument.setPreferredSize(new Dimension(70, 22));
        addRow.add(type, BorderLayout.CENTER);
        addRow.add(argument, BorderLayout.EAST);
        type.setName("scripts.script" + n + ".addType");
        type.setToolTipText(RspsScriptTools.tooltip("Add a step at the end of this script. A script can contain up to " + RspsScripts.MAX_STEPS + " steps."));
        type.addActionListener(e -> { if (!refreshing) chooseType(); });
        item.setToolTipText("Item ID (0-65535). Enter or Add appends it.");
        item.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { if (!refreshing) updateItemTooltip(); }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        item.addActionListener(e -> append());
        addStep.addActionListener(e -> append());
        body.add(addRow);
        error.setForeground(ColorScheme.BRAND_ORANGE);
        error.setPreferredSize(new Dimension(180, 16));
        error.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        error.setVisible(false);
        body.add(error);
        add(body);
        argument.setVisible(false);
        RspsScriptTools.watch(this, false);
        refresh();
    }

    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        refreshing = true;
        try {
            List<RspsScripts.Step> current = RspsScripts.steps(number);
            if (!current.equals(displayed)) {
                list.clearSelection();
                lines.clear();
                for (RspsScripts.Step step : current) lines.addElement(step);
                displayed = new ArrayList<RspsScripts.Step>(current);
            }
            on.setSelected(RspsScripts.enabled(number));
            String custom = RspsScripts.customName(number);
            String text = "Script " + number + (custom.isEmpty() ? "" : " \u00b7 " + custom);
            title.setText(text);
            title.setForeground(!on.isSelected() || current.isEmpty() ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
            title.setToolTipText(RspsScriptTools.tooltip(text + ". Double-click to rename; Enter or leaving the field saves, Esc cancels."));
            String binding = RspsHudConfig.get(bindingKey());
            key.setValue(binding);
            key.setToolTipText(RspsScriptTools.tooltip("Binding: " + RspsKeyBind.display(binding) + ". Click, then press a key (Ctrl/Alt/Shift allowed). Esc / Backspace / right-click = None."));
            String same = RspsSidebar.sharedWithKey(bindingKey());
            shared.setText("Same key: " + same);
            shared.setVisible(!same.isEmpty());
            setVisible(!current.isEmpty() || REVEALED.contains(number) || selectedScript() == number);
            rebuildChips();
            chips.setVisible(folded());
            updateBorder();
            body.setVisible(!folded());
            fold.setIcon(folded() ? RspsSidebar.SECTION_EXPAND_ICON : RspsSidebar.SECTION_RETRACT_ICON);
            fold.setToolTipText(folded() ? "Expand script" : "Fold script");
            warning.setText(current.size() + " steps: may not fit in one game tick");
            warning.setToolTipText(RspsScriptTools.tooltip(warning.getText()));
            warning.setVisible(current.size() > RspsScripts.WARN_STEPS);
            updateChoices();
            updateEdits();
            list.repaint();
        } finally { refreshing = false; }
        fit();
    }

    private boolean folded() { return Boolean.TRUE.equals(FOLDED.get(number)); }
    private void beginRename() {
        if (renaming) return;
        renaming = true;
        renameBase = RspsScripts.customName(number);
        rename.setText(renameBase);
        rename.setToolTipText("Enter or leaving the field saves; Esc cancels. Blank restores the default name.");
        titleArea.remove(title);
        titleArea.add(rename, BorderLayout.CENTER);
        titleArea.revalidate();
        titleArea.repaint();
        rename.requestFocusInWindow();
        rename.selectAll();
    }
    private void saveName() {
        if (!renaming || refreshing) return;
        if (!renameBase.equals(RspsScripts.customName(number))) {
            rename.setToolTipText("Name changed elsewhere; draft not saved. Esc reloads it.");
            return;
        }
        String value = rename.getText();
        endRename();
        if (!value.equals(renameBase)) RspsScripts.setName(number, value);
        refresh();
    }
    private void endRename() {
        renaming = false;
        titleArea.remove(rename);
        titleArea.add(title, BorderLayout.CENTER);
        titleArea.revalidate();
        titleArea.repaint();
    }

    private void edit(int direction) {
        if (refreshing) return;
        int index = list.getSelectedIndex();
        if (index < 0 || index >= displayed.size()) return;
        List<RspsScripts.Step> updated = new ArrayList<RspsScripts.Step>(displayed);
        int next = index;
        if (direction == 0) updated.remove(index);
        else {
            next = index + direction;
            if (next < 0 || next >= updated.size()) return;
            Collections.swap(updated, index, next);
        }
        replaceSteps(displayed, updated, next);
    }

    private void replaceSteps(List<RspsScripts.Step> expected, List<RspsScripts.Step> updated, int next) {
        if (!RspsScripts.replace(number, expected, updated)) {
            showError("Script changed; select the step again.");
            refresh();
            return;
        }
        error.setVisible(false);
        refresh();
        if (!updated.isEmpty()) list.setSelectedIndex(Math.min(next, updated.size() - 1));
    }

    private void updateEdits() {
        int index = list.getSelectedIndex();
        edits.setVisible(index >= 0);
        up.setEnabled(index > 0);
        down.setEnabled(index >= 0 && index < displayed.size() - 1);
        remove.setEnabled(index >= 0);
    }

    private String selectedType() {
        int index = type.getSelectedIndex() - 1;
        return index >= 0 && index < RspsScripts.TYPES.length ? RspsScripts.TYPES[index] : "";
    }
    private void chooseType() {
        argument.removeAll();
        choice = null; choiceValues = null;
        String selected = selectedType(), kind = RspsScripts.argKind(selected);
        error.setVisible(false);
        if (selected.isEmpty()) { argument.setVisible(false); fit(); return; }
        if (kind.isEmpty()) { append(); return; }
        if ("item".equals(kind)) argument.add(item, BorderLayout.CENTER);
        else {
            String[] names;
            if ("set".equals(kind)) {
                choiceValues = new String[RspsGearSets.COUNT];
                names = new String[choiceValues.length];
                for (int i = 0; i < names.length; i++) { choiceValues[i] = Integer.toString(i + 1); names[i] = RspsGearSets.name(i + 1); }
            } else if ("prayer".equals(kind)) { choiceValues = RspsPkData.PRAYER_IDS; names = RspsPkData.PRAYER_NAMES; }
            else { choiceValues = RspsPkData.SPELL_IDS; names = RspsPkData.SPELL_NAMES; }
            choice = RspsScriptTools.combo(names);
            choice.addActionListener(e -> { if (!refreshing) choice.setToolTipText(RspsScriptTools.tooltip(String.valueOf(choice.getSelectedItem()))); });
            choice.setToolTipText(RspsScriptTools.tooltip(String.valueOf(choice.getSelectedItem())));
            argument.add(choice, BorderLayout.CENTER);
        }
        argument.add(addStep, BorderLayout.EAST);
        argument.setVisible(true);
        // Reserve a compact input/Add region; long names stay available in tooltips.
        argument.setPreferredSize(new Dimension(100, 22));
        updateChoices();
        fit();
    }
    private void updateChoices() {
        if (choice != null && "set".equals(RspsScripts.argKind(selectedType()))) {
            int selected = choice.getSelectedIndex();
            for (int i = 0; i < RspsGearSets.COUNT; i++) {
                String name = RspsGearSets.name(i + 1);
                if (!name.equals(choice.getItemAt(i))) { choice.removeItemAt(i); choice.insertItemAt(name, i); }
            }
            choice.setSelectedIndex(selected);
        }
        if (choice != null) choice.setToolTipText(RspsScriptTools.tooltip(String.valueOf(choice.getSelectedItem())));
        if ("item".equals(RspsScripts.argKind(selectedType()))) updateItemTooltip();
    }
    private void updateItemTooltip() {
        String text = "Item ID (0-65535). Enter or Add appends it.";
        try {
            int id = Integer.parseInt(item.getText().trim());
            if (RspsGearSets.validId(id)) text = "Item " + id + ": " + RspsGearSets.itemName(id) + ". Enter or Add appends it.";
        } catch (NumberFormatException invalid) { /* Show input instructions until the ID is valid. */ }
        item.setToolTipText(RspsScriptTools.tooltip(text));
    }
    private void append() {
        if (refreshing) return;
        String selected = selectedType();
        if (selected.isEmpty()) return;
        String kind = RspsScripts.argKind(selected);
        String arg = "item".equals(kind) ? item.getText().trim() : choice == null || choice.getSelectedIndex() < 0 ? "" : choiceValues[choice.getSelectedIndex()];
        RspsScripts.Step step = new RspsScripts.Step(selected, arg);
        if (!step.valid()) { showError("Choose a valid argument; not added."); return; }
        if (!RspsScripts.add(number, step)) { showError("Script full: maximum " + RspsScripts.MAX_STEPS + " steps."); return; }
        refreshing = true;
        try { type.setSelectedIndex(0); } finally { refreshing = false; }
        argument.removeAll(); argument.setVisible(false); choice = null; choiceValues = null;
        error.setVisible(false);
        FOLDED.put(number, false);
        refresh();
    }
    private void showError(String text) { error.setText(text); error.setToolTipText(RspsScriptTools.tooltip(text)); error.setVisible(true); fit(); }
    private void fit() {
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, body.getPreferredSize().height));
        RspsScriptTools.fit(this);
    }
    private static String[] typeNames() {
        String[] names = new String[RspsScripts.TYPE_NAMES.length + 1];
        names[0] = "+ Step";
        System.arraycopy(RspsScripts.TYPE_NAMES, 0, names, 1, RspsScripts.TYPE_NAMES.length);
        return names;
    }
    @Override public Dimension getPreferredSize() { return new Dimension(200, super.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }

    private String bindingKey() { return "pkhotkeys.bind.script" + number + "Key"; }

    private static int selectedScript() {
        try { return Integer.parseInt(RspsHudConfig.get("pkhotkeys.addTo")); }
        catch (NumberFormatException e) { return 0; }
    }

    static boolean hiddenEmpty(int n) {
        return RspsScripts.steps(n).isEmpty() && n != selectedScript() && !REVEALED.contains(n);
    }

    static void reveal(int n) {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> reveal(n)); return; }
        if (n < 1 || n > RspsScripts.COUNT) return;
        REVEALED.add(n);
        FOLDED.put(n, false);
        RspsScriptTools.refreshViews();
    }

    private static void flash(int n) {
        Iterator<WeakReference<RspsScriptBox>> it = BOXES.iterator();
        while (it.hasNext()) {
            RspsScriptBox view = it.next().get();
            if (view == null) { it.remove(); continue; }
            if (view.number == n && view.isDisplayable()) {
                view.flashing = true;
                view.updateBorder();
                view.flashTimer.restart();
            }
        }
    }

    private void updateBorder() {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(flashing ? RspsSidebar.accent() : new Color(0x333333), 2),
            new EmptyBorder(1, 2, 1, 2)));
        repaint();
    }

    @Override public void removeNotify() {
        flashTimer.stop();
        flashing = false;
        updateBorder();
        super.removeNotify();
    }

    private static Icon itemIcon(int id) {
        BufferedImage image = RspsLootTracker.icon(id, 1);
        return new Icon() {
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (image != null) g.drawImage(image, x, y, 14, 14, null);
                else { g.setColor(new Color(0xFFFF00)); g.fillRect(x + 2, y + 2, 10, 10); }
            }
        };
    }

    private static JLabel chip(String text, Color colour) {
        JLabel chip = RspsScriptTools.label(text);
        chip.setForeground(colour);
        chip.setBackground(new Color(0x232323));
        chip.setOpaque(true);
        chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x333333)), new EmptyBorder(1, 3, 1, 3)));
        return chip;
    }

    private static String chipText(RspsScripts.Step step) {
        String t = step.type;
        if (RspsScripts.SET.equals(t)) return "Set " + step.arg;
        if ("prayer".equals(RspsScripts.argKind(t))) {
            return (RspsScripts.PRAY_OFF.equals(t) ? "-" : RspsScripts.PRAY_TOGGLE.equals(t) ? "\u00b1" : "") + step.object();
        }
        if (RspsScripts.QUICK.equals(t)) return "Quick";
        if (RspsScripts.CAST.equals(t) || RspsScripts.SELECT.equals(t)) {
            String name = step.object().replace(" -> target", "");
            return name.substring(name.lastIndexOf(' ') + 1) + (RspsScripts.CAST.equals(t) ? ">" : "");
        }
        if (RspsScripts.ATTACK.equals(t)) return "Attack>";
        if (RspsScripts.STEP.equals(t)) return "Step";
        if (RspsScripts.SPEC.equals(t)) return "Spec";
        if (RspsScripts.BREW.equals(t)) return "Brew";
        if (RspsScripts.RESTORE.equals(t)) return "Restore";
        if (RspsScripts.VENG.equals(t)) return "Veng";
        return step.verb();
    }

    private static Color stepColour(String type) {
        if (RspsScripts.PRAY_TOGGLE.equals(type) || RspsScripts.QUICK.equals(type)) return new Color(0xFF4040);
        return new Color(RspsScripts.colour(type));
    }

    private void rebuildChips() {
        chips.removeAll();
        List<JLabel> all = new ArrayList<JLabel>();
        StringBuilder full = new StringBuilder();
        for (RspsScripts.Step step : displayed) {
            if (full.length() > 0) full.append('\n');
            full.append(step.label());
        }
        for (int i = 0; i < displayed.size();) {
            RspsScripts.Step step = displayed.get(i);
            if (RspsScripts.WEAR.equals(step.type)) {
                int end = i;
                while (end < displayed.size() && RspsScripts.WEAR.equals(displayed.get(end).type)) end++;
                final List<Icon> icons = new ArrayList<Icon>();
                for (int k = i; k < Math.min(end, i + 4); k++) icons.add(itemIcon(displayed.get(k).intArg()));
                JLabel wear = chip(end - i > 4 ? "x" + (end - i) : "", Color.YELLOW);
                wear.setIcon(new Icon() {
                    public int getIconWidth() { return icons.size() * 14; }
                    public int getIconHeight() { return 14; }
                    public void paintIcon(Component c, Graphics g, int x, int y) {
                        for (int k = 0; k < icons.size(); k++) icons.get(k).paintIcon(c, g, x + k * 14, y);
                    }
                });
                all.add(wear);
                i = end;
            } else { all.add(chip(chipText(step), stepColour(step.type))); i++; }
        }
        if (all.isEmpty()) {
            JLabel empty = chip("empty - + Step or right-click an item", ColorScheme.LIGHT_GRAY_COLOR);
            int width = chips.getWidth() > 0 ? chips.getWidth() : 190;
            empty.setPreferredSize(new Dimension(Math.max(1, width - 4), empty.getPreferredSize().height));
            all.add(empty);
        }
        String tip = RspsScriptTools.tooltip(full.length() == 0 ? "Empty - + Step or right-click an item" : full.toString());
        chips.setToolTipText(tip);
        int width = chips.getWidth() > 0 ? chips.getWidth() : 190;
        int used = 2, shown = 0;
        for (int i = 0; i < all.size(); i++) {
            JLabel label = all.get(i);
            int reserve = i + 1 < all.size() ? chip("+" + (all.size() - i - 1), Color.GRAY).getPreferredSize().width + 2 : 0;
            int size = label.getPreferredSize().width + 2;
            if (used + size + reserve > width) break;
            chips.add(label); used += size; shown++;
        }
        if (shown < all.size()) {
            JLabel rest = chip(displayed.isEmpty() ? "empty" : "+" + (all.size() - shown), ColorScheme.LIGHT_GRAY_COLOR);
            chips.add(rest);
        }
        for (Component c : chips.getComponents()) {
            ((JComponent) c).setToolTipText(tip);
            c.addMouseListener((MouseAdapter) chips.getClientProperty("toggleListener"));
        }
        chips.revalidate(); chips.repaint();
    }

    private static final class StepRenderer extends JPanel implements ListCellRenderer<RspsScripts.Step> {
        private final JLabel verb = RspsScriptTools.label(""), object = RspsScriptTools.label(""), marker = new JLabel();
        StepRenderer() {
            super(new BorderLayout(3, 0));
            JPanel words = new JPanel(new BorderLayout(3, 0));
            words.setOpaque(false); words.add(verb, BorderLayout.WEST); words.add(object, BorderLayout.CENTER);
            marker.setPreferredSize(new Dimension(14, 14));
            add(marker, BorderLayout.WEST); add(words, BorderLayout.CENTER);
        }
        @Override public Component getListCellRendererComponent(JList<? extends RspsScripts.Step> list, RspsScripts.Step step, int index, boolean selected, boolean focus) {
            setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            final Color colour = stepColour(step.type);
            marker.setIcon(RspsScripts.WEAR.equals(step.type) ? itemIcon(step.intArg()) : new Icon() {
                public int getIconWidth() { return 14; }
                public int getIconHeight() { return 14; }
                public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(colour); g.fillRect(x + 2, y + 2, 10, 10); }
            });
            verb.setText(step.verb()); verb.setForeground(colour);
            object.setText(step.object()); object.setForeground(Color.WHITE);
            setToolTipText(RspsScriptTools.tooltip(step.label()));
            return this;
        }
    }
}




