package net.twelfthengine.qgui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUIButton extends QGUIElement {
  public String text;
  private Runnable onClick;
  private boolean isHovered = false;

  public QGUIButton(int x, int y, int w, int h, String text, Runnable onClick) {
    super(x, y, w, h);
    this.text = text;
    this.onClick = onClick;
  }

  public void setText(String newText) {
    text = newText;
  }

  public void setCallback(Runnable newOnClick) {
    onClick = newOnClick;
  }

  @Override
  public void update(
      int mx, int my, boolean mousePressed, boolean mouseDown, int parentX, int parentY) {
    isHovered = isHovered(mx, my, parentX, parentY);
    if (isHovered && mousePressed && onClick != null) {
      onClick.run();
    }
  }

  @Override
  public void render(Renderer2D r2d, TextRenderer textRenderer, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;

    // Change color based on hover state
    if (isHovered) r2d.setColor(0.3f, 0.3f, 0.3f, 1f);
    else r2d.setColor(0.2f, 0.2f, 0.2f, 1f);

    r2d.drawRoundedRect(absX, absY, width, height, 4);

    // Draw Text centered
    r2d.setColor(1f, 1f, 1f, 1f); // Reset to white for text
    float textW = textRenderer.getTextWidth(text, 1.0f);
    textRenderer.drawText2D(
        text, absX + (width / 2f) - (textW / 2f), absY + (height / 2f) - 4f, 1.0f, 1f, 1f, 1f, 1f);
  }
}
