import rsps.runelite.ColorScheme;
import rsps.runelite.FontManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
/** Manual gear-set editor. Only RspsGearSets crosses the Swing/client boundary. */
final class RspsGearSetEditor extends JPanel {
    private static int selectedSet = 1;
    private static boolean listenerRegistered;
    private static volatile WeakReference<RspsGearSetEditor> current =
        new WeakReference<RspsGearSetEditor>(null);
    private static final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final JComboBox<String> sets = new JComboBox<String>();
    private final JTextField name = new JTextField();
    private final JTextField itemId = new JTextField();
    private final DefaultListModel<String> rows = new DefaultListModel<String>();
    private final JList<String> items = new JList<String>(rows);
    private final JLabel message = label(" ", ColorScheme.BRAND_ORANGE);
    private final JLabel empty = label(
        "<html><body style=\"width: 174px\">Empty - wear gear and press Save worn, or add IDs.</body></html>",
        ColorScheme.LIGHT_GRAY_COLOR);
    private final JButton remove = button("Remove");
    private final JButton clear = button("Clear");
    private final JButton up = button("Move up");
    private final JButton down = button("Move down");
    private final Timer clearTimer = new Timer(3000, e -> disarmClear());
    private int editingSet = selectedSet;
    private int[] ids = new int[0];
    private boolean refreshing;
    private boolean clearArmed;
    /** Construct on the Swing thread; reopening installs no additional model listener. */
    RspsGearSetEditor() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setOpaque(false);
        clearTimer.setRepeats(false);
        sets.setFont(FontManager.getRunescapeSmallFont());
        sets.setForeground(Color.WHITE);
        sets.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sets.addActionListener(e -> {
            if (refreshing || sets.getSelectedIndex() < 0) return;
            saveName();
            editingSet = sets.getSelectedIndex() + 1;
            selectedSet = editingSet;
            name.setText(RspsGearSets.name(editingSet));
            disarmClear();
            message.setText(" ");
            items.clearSelection();
            refresh();
        });
        addRow(sets);
        styleField(name);
        ((AbstractDocument) name.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text,
                                     AttributeSet attrs) throws BadLocationException {
                replace(fb, offset, 0, text, attrs);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                                AttributeSet attrs) throws BadLocationException {
                String value = text == null ? "" : text;
                int room = 24 - (fb.getDocument().getLength() - length);
                if (value.length() > room) value = value.substring(0, Math.max(0, room));
                fb.replace(offset, length, value, attrs);
            }
        });
        name.addActionListener(e -> saveName());
        name.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { saveName(); }
        });
        JPanel nameRow = row();
        nameRow.add(label("Name:", Color.WHITE), BorderLayout.WEST);
        nameRow.add(name, BorderLayout.CENTER);
        addRow(nameRow);
        items.setFont(FontManager.getRunescapeSmallFont());
        items.setForeground(Color.WHITE);
        items.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        items.setSelectionBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        items.setSelectionForeground(Color.WHITE);
        items.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        items.setVisibleRowCount(8);
        items.setFixedCellHeight(18);
        items.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                putClientProperty("html.disable", Boolean.TRUE);
                JLabel cell = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focus);
                // Cache names are plain text, even if they contain HTML-looking characters.
                cell.setText(String.valueOf(value));
                cell.setToolTipText(String.valueOf(value));
                return cell;
            }
        });
        items.addListSelectionListener(e -> updateButtons());
        empty.setVerticalAlignment(SwingConstants.TOP);
        empty.setBorder(new EmptyBorder(4, 4, 4, 4));
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        listPanel.add(items, BorderLayout.CENTER);
        listPanel.add(empty, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setPreferredSize(new Dimension(200, 8 * 18 + 2));
        addRow(scroll);
        styleField(itemId);
        itemId.setToolTipText("Item ID (digits only)");
        ((AbstractDocument) itemId.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text,
                                     AttributeSet attrs) throws BadLocationException {
                replace(fb, offset, 0, text, attrs);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                                AttributeSet attrs) throws BadLocationException {
                if (text == null || text.matches("[0-9]*")) {
                    fb.replace(offset, length, text, attrs);
                } else {
                    message.setText("Use digits only.");
                }
            }
        });
        itemId.addActionListener(e -> addItem());
        JButton add = button("+ Add");
        add.addActionListener(e -> addItem());
        JPanel addRow = row();
        addRow.add(label("Item ID", Color.WHITE), BorderLayout.WEST);
        addRow.add(itemId, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        addRow(addRow);
        addRow(message);
        remove.addActionListener(e -> removeItem());
        clear.addActionListener(e -> {
            if (!clearArmed) {
                clearArmed = true;
                clear.setText("Sure?");
                clearTimer.restart();
            } else {
                store(new int[0], -1);
            }
        });
        JButton worn = button("Save worn");
        worn.addActionListener(e -> {
            disarmClear();
            message.setText("Save worn requested.");
            RspsGearSets.requestSaveWorn(editingSet);
        });
        JPanel actions = new JPanel(new GridLayout(1, 3, 3, 0));
        actions.setOpaque(false);
        actions.add(remove);
        actions.add(clear);
        actions.add(worn);
        addRow(actions);
        addRow(label("<html>Order = click order.<br>Weapon before shield.</html>",
            ColorScheme.LIGHT_GRAY_COLOR));
        up.setToolTipText("Move selected item up");
        down.setToolTipText("Move selected item down");
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));
        JPanel order = new JPanel(new GridLayout(1, 2, 3, 0));
        order.setOpaque(false);
        order.add(up);
        order.add(down);
        addRow(order);
        current = new WeakReference<RspsGearSetEditor>(this);
        registerListener();
        refresh();
    }
    /** One shared callback; queued work resolves the current editor on the Swing thread. */
    private static synchronized void registerListener() {
        if (listenerRegistered) return;
        RspsGearSets.addListener(() -> {
            if (refreshQueued.getAndSet(true)) return;
            SwingUtilities.invokeLater(() -> {
                refreshQueued.set(false);
                RspsGearSetEditor editor = current.get();
                if (editor != null) editor.refresh();
            });
        });
        listenerRegistered = true;
    }
    /** Reload labels and items without overwriting a name being typed or losing item selection. */
    void refresh() {
        int index = items.getSelectedIndex();
        int selectedId = index >= 0 && index < ids.length ? ids[index] : -1;
        refreshing = true;
        try {
            for (int n = 1; n <= RspsGearSets.COUNT; n++) {
                String title = n + ": " + RspsGearSets.name(n);
                int slot = n - 1;
                if (slot >= sets.getItemCount()) sets.addItem(title);
                else if (!title.equals(sets.getItemAt(slot))) {
                    sets.removeItemAt(slot);
                    sets.insertItemAt(title, slot);
                }
            }
            sets.setSelectedIndex(editingSet - 1);
            if (!name.isFocusOwner()) name.setText(RspsGearSets.name(editingSet));
            ids = RspsGearSets.items(editingSet);
            rows.clear();
            int restore = -1;
            for (int i = 0; i < ids.length; i++) {
                rows.addElement(RspsGearSets.itemName(ids[i]) + " (" + ids[i] + ")");
                if (restore < 0 && ids[i] == selectedId) restore = i;
            }
            empty.setVisible(ids.length == 0);
            items.setVisible(ids.length != 0);
            items.setSelectedIndex(restore);
        } finally {
            refreshing = false;
        }
        updateButtons();
        revalidate();
        repaint();
    }
    private void saveName() {
        if (refreshing) return;
        if (!name.getText().equals(RspsGearSets.name(editingSet))) {
            RspsGearSets.setName(editingSet, name.getText());
            name.setText(RspsGearSets.name(editingSet));
        }
    }
    private void addItem() {
        final int id;
        try {
            id = Integer.parseInt(itemId.getText());
        } catch (NumberFormatException invalid) {
            message.setText("Enter a valid item ID.");
            return;
        }
        if (!RspsGearSets.validId(id)) {
            message.setText("Item ID must be 0-65535.");
            return;
        }
        int[] saved = ids; // the shown list: store() refuses it if the set changed meanwhile
        for (int existing : saved) {
            if (existing == id) { message.setText("Already in this set."); return; }
        }
        if (saved.length >= RspsGearSets.MAX_ITEMS) {
            message.setText("Set full (" + RspsGearSets.MAX_ITEMS + " items).");
            return;
        }
        int[] changed = Arrays.copyOf(saved, saved.length + 1);
        changed[saved.length] = id;
        if (store(changed, saved.length)) {
            itemId.setText(""); // kept when the set changed meanwhile, so the player can just press Add again
        }
    }
    private void removeItem() {
        int index = items.getSelectedIndex();
        if (index < 0 || index >= ids.length) return;
        int[] changed = new int[ids.length - 1];
        System.arraycopy(ids, 0, changed, 0, index);
        System.arraycopy(ids, index + 1, changed, index, ids.length - index - 1);
        store(changed, Math.min(index, changed.length - 1));
    }
    private void move(int direction) {
        int index = items.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= ids.length) return;
        int[] changed = ids.clone();
        int swap = changed[index];
        changed[index] = changed[target];
        changed[target] = swap;
        store(changed, target);
    }
    private boolean store(int[] changed, int selection) {
        disarmClear();
        message.setText(" ");
        // Only over the list this editor shows: a "Save worn" that arrived in between wins (Codex review v3, finding 5).
        if (!RspsGearSets.replace(editingSet, ids, changed)) {
            refresh();
            message.setText("Set changed - try again.");
            return false;
        }
        refresh();
        items.setSelectedIndex(selection);
        if (selection >= 0) items.ensureIndexIsVisible(selection);
        return true;
    }
    private void updateButtons() {
        int index = items.getSelectedIndex();
        remove.setEnabled(index >= 0);
        clear.setEnabled(ids.length > 0);
        up.setEnabled(index > 0);
        down.setEnabled(index >= 0 && index < ids.length - 1);
    }
    private void disarmClear() {
        clearTimer.stop();
        clearArmed = false;
        clear.setText("Clear");
    }
    @Override
    public void removeNotify() {
        disarmClear();
        super.removeNotify();
    }

    private void addRow(JComponent component) {
        if (getComponentCount() > 0) add(Box.createVerticalStrut(4));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        add(component);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    private static JPanel row() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private static void styleField(JTextField field) {
        field.setFont(FontManager.getRunescapeSmallFont());
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setMinimumSize(new Dimension(20, field.getPreferredSize().height));
        field.setPreferredSize(new Dimension(70, field.getPreferredSize().height));
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusPainted(false);
        return button;
    }
}
