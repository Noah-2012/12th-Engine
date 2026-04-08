package net.twelfthengine.qgui;

public class QGUIMenuItem {
  public String text;
  public Runnable action;

  public QGUIMenuItem(String text, Runnable action) {
    this.text = text;
    this.action = action;
  }

  public void run() {
    if (action != null) action.run();
  }
}
