package net.twelfthengine.qgui;

import java.util.ArrayList;
import java.util.List;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUIWindow {
  public int x, y, width, height;
  public String title;

  private List<QGUIElement> children = new ArrayList<>();
  private boolean isDragging = false;
  private int dragOffsetX, dragOffsetY;

  public boolean isMinimized = false;
  public boolean isClosed = false;
  private final int titleBarHeight = 20;

  public QGUIWindow(String title, int x, int y, int width, int height) {
    this.title = title;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  private int getLogicalHeight() {
    return isMinimized ? titleBarHeight : height;
  }

  public void addElement(QGUIElement element) {
    children.add(element);
  }

  public void update(int mx, int my, boolean mousePressed, boolean mouseDown) {
    if (isClosed) return;

    int logicalHeight = getLogicalHeight();

    boolean overWindow = mx >= x && mx <= x + width && my >= y && my <= y + logicalHeight;

    boolean overTitleBar = mx >= x && mx <= x + width && my >= y && my <= y + titleBarHeight;

    boolean overCloseBtn =
        mx >= x + width - 20 && mx <= x + width && my >= y && my <= y + titleBarHeight;

    boolean overMinBtn =
        mx >= x + width - 40 && mx <= x + width - 20 && my >= y && my <= y + titleBarHeight;

    // --- CLICK EVENTS ---
    if (mousePressed && overWindow) {
      if (overCloseBtn) {
        isClosed = true;
        return;
      } else if (overMinBtn) {
        isMinimized = !isMinimized;
        return;
      } else if (overTitleBar) {
        isDragging = true;
        dragOffsetX = mx - x;
        dragOffsetY = my - y;
      }
    }

    if (!mouseDown) isDragging = false;

    // --- DRAGGING ---
    if (isDragging) {
      x = mx - dragOffsetX;
      y = my - dragOffsetY;
    }

    if (!isMinimized && overWindow) {
      for (QGUIElement child : children) {
        if (child.visible) child.update(mx, my, mousePressed, mouseDown, x, y + titleBarHeight);
      }
    }
  }

  public void render(Renderer2D r2d, TextRenderer textRenderer) {
    if (isClosed) return;

    // Title Bar
    r2d.setColor(0.15f, 0.15f, 0.15f, 0.9f);
    r2d.drawRoundedRect(x, y, width, titleBarHeight, 2);

    // Controls (Minimize & Close)
    r2d.setColor(0.8f, 0.8f, 0.2f, 1f); // Yellow Min
    r2d.drawRect(x + width - 40, y + 4, 12, 12);
    r2d.setColor(0.8f, 0.2f, 0.2f, 1f); // Red Close
    r2d.drawRect(x + width - 20, y + 4, 12, 12);

    r2d.setColor(1f, 1f, 1f, 1f);
    textRenderer.drawText2D(title, x + 4, y + 4, 1.0f, 1f, 1f, 1f, 1f);

    if (!isMinimized) {
      r2d.setColor(0.1f, 0.1f, 0.1f, 0.8f);
      r2d.drawRect(x, y + titleBarHeight, width, height - titleBarHeight);

      for (QGUIElement child : children) {
        if (child.visible) child.render(r2d, textRenderer, x, y + titleBarHeight);
      }
    }
  }
}
