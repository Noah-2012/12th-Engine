package net.twelfthengine.qgui;

import java.util.ArrayList;
import java.util.List;

public class QGUIMenu {
  public String name;
  public List<QGUIMenuItem> items = new ArrayList<>();

  public QGUIMenu(String name) {
    this.name = name;
  }

  // Builder pattern for nice chaining
  public QGUIMenu addItem(String text, Runnable action) {
    items.add(new QGUIMenuItem(text, action));
    return this;
  }
}
