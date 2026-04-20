package net.twelfthengine.qgui;

import java.util.ArrayList;
import java.util.List;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;

public class QGUIManager {
  private List<QGUIWindow> windows = new ArrayList<>();
  // You can add a QGUIMenuBar here later that always renders at y=0

  private QGUIMenuBar menuBar;

  public void setMenuBar(QGUIMenuBar bar) {
    this.menuBar = bar;
  }

  private boolean lastMouseState = false;

  public void addWindow(QGUIWindow window) {
    windows.add(window);
  }

  public void update(int mouseX, int mouseY, boolean mouseDown, int screenWidth) {

    boolean mousePressed = mouseDown && !lastMouseState;

    // Update Menu Bar first (it's always on top)
    if (menuBar != null) {
      menuBar.update(mouseX, mouseY, mousePressed, screenWidth);
    }

    // Iterate backwards so top-most windows get input first
    for (int i = windows.size() - 1; i >= 0; i--) {
      QGUIWindow w = windows.get(i);
      w.update(mouseX, mouseY, mousePressed, mouseDown);

      // Basic Z-ordering: if a window is clicked, bring it to front
      if (mousePressed
              && mouseX >= w.x
              && mouseX <= w.x + w.width
              && mouseY >= w.y
              && mouseY <= w.y + w.height) {
        windows.remove(i);
        windows.add(w);
        break; // Stop routing click to windows underneath
      }
    }

    windows.removeIf(w -> w.isClosed);
    lastMouseState = mouseDown;
  }

  public void render(Renderer2D r2d, TextRenderer textRenderer) {
    // Render bottom to top
    for (QGUIWindow window : windows) {
      window.render(r2d, textRenderer);
    }

    if (menuBar != null) {
      menuBar.render(r2d, textRenderer, r2d.getWidth()); // Ensure Renderer2D has a getWidth()
    }
  }
}
