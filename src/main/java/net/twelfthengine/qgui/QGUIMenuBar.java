package net.twelfthengine.qgui;

import java.util.ArrayList;
import java.util.List;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUIMenuBar {
  private List<QGUIMenu> menus = new ArrayList<>();
  private int openMenuIndex = -1;
  private int hoveredItemIndex = -1;
  private int hoveredIndex = -1;
  public final int height = 24;

  public QGUIMenu addMenu(String name) {
    QGUIMenu m = new QGUIMenu(name);
    menus.add(m);
    return m;
  }

  public void update(int mx, int my, boolean mousePressed, int screenWidth) {
    hoveredIndex = -1;
    hoveredItemIndex = -1;

    boolean clickedOnMenuBar = false;
    boolean clickedOnDropdown = false;

    int currentX = 10;

    // --- Top bar hover ---
    if (my >= 0 && my <= height) {
      for (int i = 0; i < menus.size(); i++) {
        int itemWidth = menus.get(i).name.length() * 8 + 20;

        if (mx >= currentX && mx <= currentX + itemWidth) {
          hoveredIndex = i;

          if (mousePressed) {
            openMenuIndex = i; // open dropdown
            clickedOnMenuBar = true;
          }
          break;
        }
        currentX += itemWidth;
      }
    }

    // --- Dropdown interaction ---
    if (openMenuIndex != -1) {
      QGUIMenu menu = menus.get(openMenuIndex);
      int dropdownX = getMenuX(openMenuIndex);
      int dropdownY = height;
      int dropdownWidth = 150;
      int dropdownHeight = menu.items.size() * height;

      // Prüfen ob Maus im Dropdown ist
      if (mx >= dropdownX
              && mx <= dropdownX + dropdownWidth
              && my >= dropdownY
              && my <= dropdownY + dropdownHeight) {
        clickedOnDropdown = mousePressed;
      }

      for (int i = 0; i < menu.items.size(); i++) {
        int itemY = dropdownY + i * height;

        if (mx >= dropdownX
                && mx <= dropdownX + dropdownWidth
                && my >= itemY
                && my <= itemY + height) {

          hoveredItemIndex = i;

          if (mousePressed) {
            menu.items.get(i).run();
            openMenuIndex = -1; // close after click
            return;
          }
        }
      }
    }

    // ⭐ NEU: Klick außerhalb → Menü schließen
    if (mousePressed && !clickedOnMenuBar && !clickedOnDropdown) {
      openMenuIndex = -1;
    }
  }

  public void render(Renderer2D r2d, TextRenderer textRenderer, int screenWidth) {
    // Draw main bar background
    r2d.setColor(0.15f, 0.15f, 0.15f, 1f);
    r2d.drawRect(0, 0, screenWidth, height);

    int currentX = 10;
    for (int i = 0; i < menus.size(); i++) {
      String menu = menus.get(i).name;
      int itemWidth = menu.length() * 8 + 20;

      if (i == hoveredIndex) {
        r2d.setColor(0.3f, 0.3f, 0.3f, 1f);
        r2d.drawRect(currentX, 0, itemWidth, height);
      }

      r2d.setColor(1f, 1f, 1f, 1f);
      textRenderer.drawText2D(menu, currentX + 10, 6, 1.0f, 1f, 1f, 1f, 1f);
      currentX += itemWidth;
    }

    // --- Render dropdown ---
    if (openMenuIndex != -1) {
      QGUIMenu menu = menus.get(openMenuIndex);
      int x = getMenuX(openMenuIndex);
      int width = 150;

      for (int i = 0; i < menu.items.size(); i++) {
        int y = height + i * height;

        if (i == hoveredItemIndex) r2d.setColor(0.35f, 0.35f, 0.35f, 1f);
        else r2d.setColor(0.2f, 0.2f, 0.2f, 1f);

        r2d.drawRect(x, y, width, height);

        r2d.setColor(1f, 1f, 1f, 1f);
        textRenderer.drawText2D(menu.items.get(i).text, x + 10, y + 6, 1f, 1f, 1f, 1f, 1f);
      }
    }
  }

  private int getMenuX(int menuIndex) {
    int x = 10;
    for (int i = 0; i < menuIndex; i++) x += menus.get(i).name.length() * 8 + 20;
    return x;
  }
}
