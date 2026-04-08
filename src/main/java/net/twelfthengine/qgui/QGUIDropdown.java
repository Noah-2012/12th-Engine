package net.twelfthengine.qgui;

import java.util.List;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUIDropdown extends QGUIElement {
  private List<String> options;
  public int selectedIndex = 0;
  private boolean isOpen = false;
  private int hoveredOption = -1;

  public QGUIDropdown(int x, int y, int w, int h, List<String> options) {
    super(x, y, w, h);
    this.options = options;
  }

  @Override
  public void update(
      int mx, int my, boolean mousePressed, boolean mouseDown, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;

    boolean hoveredMain = isHovered(mx, my, parentX, parentY);

    if (isOpen) {
      hoveredOption = -1;
      // Check if hovering over the open list
      if (mx >= absX
          && mx <= absX + width
          && my > absY + height
          && my <= absY + height + (options.size() * height)) {
        hoveredOption = (my - (absY + height)) / height;

        if (mousePressed && hoveredOption < options.size()) {
          selectedIndex = hoveredOption;
          isOpen = false; // Close after selecting
        }
      } else if (mousePressed && !hoveredMain) {
        isOpen = false; // Clicked outside, close it
      }
    }

    if (hoveredMain && mousePressed) {
      isOpen = !isOpen; // Toggle open/close
    }
  }

  @Override
  public void render(Renderer2D r2d, TextRenderer textRenderer, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;

    // Draw main box
    r2d.setColor(0.2f, 0.2f, 0.2f, 1f);
    r2d.drawRoundedRect(absX, absY, width, height, 2);

    // Draw selected text
    String displayText = options.isEmpty() ? "" : options.get(selectedIndex);
    textRenderer.drawText2D(displayText, absX + 5, absY + 4, 1.0f, 1f, 1f, 1f, 1f);

    // Draw dropdown arrow (simple square for now)
    r2d.setColor(0.5f, 0.5f, 0.5f, 1f);
    r2d.drawRect(absX + width - 15, absY + 5, 10, 10);

    // Draw open list
    if (isOpen) {
      for (int i = 0; i < options.size(); i++) {
        int itemY = absY + height + (i * height);

        if (i == hoveredOption) r2d.setColor(0.3f, 0.3f, 0.5f, 1f);
        else r2d.setColor(0.15f, 0.15f, 0.15f, 1f);

        r2d.drawRect(absX, itemY, width, height);
        textRenderer.drawText2D(options.get(i), absX + 5, itemY + 4, 1.0f, 1f, 1f, 1f, 1f);
      }
    }
  }
}
