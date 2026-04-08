package net.twelfthengine.qgui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUITextField extends QGUIElement {
  public String text = "";
  private boolean isFocused = false;
  private int cursorBlinkTimer = 0;

  public QGUITextField(int x, int y, int w, int h) {
    super(x, y, w, h);
  }

  @Override
  public void update(
      int mx, int my, boolean mousePressed, boolean mouseDown, int parentX, int parentY) {
    if (mousePressed) {
      isFocused = isHovered(mx, my, parentX, parentY);
    }

    if (isFocused) {
      cursorBlinkTimer++;

      // NOTE: You must implement a way to poll characters in InputManager!
      // Example pseudo-code for how it should look:

      /*
      char c = InputManager.getTypedChar();
      if (c != '\0') {
          text += c;
      }
      */

      // Handle Backspace (assuming you have access to GLFW keys in InputManager)
      // You might need a cooldown timer here so it doesn't delete the whole string instantly
      /*
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_BACKSPACE) && text.length() > 0) {
          text = text.substring(0, text.length() - 1);
      }
      */
    } else {
      cursorBlinkTimer = 0;
    }
  }

  @Override
  public void render(Renderer2D r2d, TextRenderer textRenderer, int parentX, int parentY) {
    int absX = parentX + localX;
    int absY = parentY + localY;

    // Background
    r2d.setColor(0.1f, 0.1f, 0.1f, 1f);
    if (isFocused) r2d.setColor(0.15f, 0.15f, 0.2f, 1f); // Highlight slightly when focused
    r2d.drawRoundedRect(absX, absY, width, height, 2);

    // Border
    if (isFocused) {
      r2d.setColor(0.4f, 0.6f, 1.0f, 1f); // Blue border when focused
      r2d.drawLine(absX, absY, absX + width, absY);
      r2d.drawLine(absX, absY + height, absX + width, absY + height);
      r2d.drawLine(absX, absY, absX, absY + height);
      r2d.drawLine(absX + width, absY, absX + width, absY + height);
    }

    // Draw Text
    r2d.setColor(1f, 1f, 1f, 1f);
    textRenderer.drawText2D(text, absX + 5, absY + 4, 1.0f, 1f, 1f, 1f, 1f);

    // Blinking Cursor
    if (isFocused && (cursorBlinkTimer / 30) % 2 == 0) {
      float textWidth = textRenderer.getTextWidth(text, 1.0f);
      r2d.drawLine(
          absX + 6 + (int) textWidth, absY + 4, absX + 6 + (int) textWidth, absY + height - 4);
    }
  }
}
