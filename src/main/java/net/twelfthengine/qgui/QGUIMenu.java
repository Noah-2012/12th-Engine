package net.twelfthengine.qgui;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an application menu that can contain {@link QGUIMenuItem}s.
 * <p>
 * Each menu has a display name and a list of items. Items are added via the
 * builder-style {@link #addItem(String, Runnable)} method, which returns
 * the {@code QGUIMenu} instance to enable fluent chaining:
 * <pre>
 *     QGUIMenu menu = new QGUIMenu("File")
 *         .addItem("Open", () -> openFile())
 *         .addItem("Save", () -> saveFile());
 * </pre>
 * </p>
 *
 * @since 1.0
 */
public class QGUIMenu {
    /** Display name of the menu. */
    public String name;

    /** Ordered list of menu items attached to this menu. */
    public List<QGUIMenuItem> items = new ArrayList<>();

    /**
     * Creates a new menu with the given name.
     *
     * @param name the menu title
     */
    public QGUIMenu(String name) {
        this.name = name;
    }

    /**
     * Adds a new menu item.
     *
     * @param text   the item label
     * @param action the action to run when the item is selected
     * @return this menu, enabling method chaining
     */
    public QGUIMenu addItem(String text, Runnable action) {
        items.add(new QGUIMenuItem(text, action));
        return this;
    }
}
