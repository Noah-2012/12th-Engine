package net.twelfthengine.qgui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUISlider extends QGUIElement {
  public float value = 0.5f; // 0.0 to 1.0
  private boolean isDragging = false;

  public QGUISlider(int x, int y, int w, int h) {
    super(x, y, w, h);
  }

  @Override
  public void update(
      int mx, int my, boolean mousePressed, boolean mouseDown, int parentX, int parentY) {
    boolean hovered = isHovered(mx, my, parentX, parentY);

    if (hovered && mousePressed) isDragging = true;
    if (!mouseDown) isDragging = false;

    if (isDragging) {
      int absX = parentX + localX;
      float relativeMouseX = mx - absX;
      value = Math.max(0.0f, Math.min(1.0f, relativeMouseX / (float) width));
    }
  }

  @Override
  public void render(Renderer2D r2d, TextRenderer textRenderer, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;

    // Track
    r2d.setColor(0.1f, 0.1f, 0.1f, 1f);
    r2d.drawRoundedRect(absX, absY + height / 2 - 2, width, 4, 2);

    // Fill
    r2d.setColor(0.2f, 0.6f, 1.0f, 1f);
    int fillWidth = (int) (width * value);
    r2d.drawRoundedRect(absX, absY + height / 2 - 2, fillWidth, 4, 2);

    // Knob
    r2d.setColor(0.8f, 0.8f, 0.8f, 1f);
    r2d.drawRoundedRect(absX + fillWidth - 4, absY, 8, height, 4);
  }
}
