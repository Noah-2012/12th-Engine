package net.twelfthengine.qgui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public abstract class QGUIElement {
  public int localX, localY;
  public int width, height;
  public boolean visible = true;

  public QGUIElement(int localX, int localY, int width, int height) {
    this.localX = localX;
    this.localY = localY;
    this.width = width;
    this.height = height;
  }

  // mx and my are absolute screen coordinates.
  // parentX and parentY are the absolute position of the window holding this element.
  public abstract void update(
      int mx, int my, boolean mousePressed, boolean mouseDown, int parentX, int parentY);

  public abstract void render(Renderer2D r2d, TextRenderer textRenderer, int parentX, int parentY);

  public boolean isHovered(int mx, int my, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;
    return mx >= absX && mx <= absX + width && my >= absY && my <= absY + height;
  }
}
