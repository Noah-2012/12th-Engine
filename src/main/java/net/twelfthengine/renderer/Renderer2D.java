package net.twelfthengine.renderer;

import net.twelfthengine.math.Vec2;
import net.twelfthengine.window.Window;
import org.lwjgl.opengl.GL11;

public class Renderer2D {

  private final Window window;
  private final int width, height;

  public Renderer2D(Window window) {
    this.window = window;
    this.width = window.getWidth();
    this.height = window.getHeight();

    // begin2D();
  }

  public void begin2D() {
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    // Set up orthographic projection for pixel-space 2D rendering.
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadIdentity();
    GL11.glOrtho(0, width, height, 0, -1, 1);
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadIdentity();
  }

  public void end2D() {
    // Restore state expected by 3D renderer
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_DEPTH_TEST);

    // VERY IMPORTANT: restore default matrix mode
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadIdentity();
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadIdentity();
  }

  // ===== Primitive Rendering (int Pixel) =====

  public void drawLine(int x1, int y1, int x2, int y2) {
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex2i(x1, y1);
    GL11.glVertex2i(x2, y2);
    GL11.glEnd();
  }

  public void drawRect(int x, int y, int width, int height) {
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2i(x, y);
    GL11.glVertex2i(x + width, y);
    GL11.glVertex2i(x + width, y + height);
    GL11.glVertex2i(x, y + height);
    GL11.glEnd();
  }

  /** Filled rounded rectangle (pixel coords, top-left origin). */
  public void drawRoundedRect(int x, int y, int w, int h, int radius) {
    if (w <= 0 || h <= 0) return;
    int r = Math.min(radius, Math.min(w / 2, h / 2));
    if (r <= 0) {
      drawRect(x, y, w, h);
      return;
    }
    int segs = Math.max(4, Math.min(16, r));

    // Center cross
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glVertex2i(x + r, y);
    GL11.glVertex2i(x + w - r, y);
    GL11.glVertex2i(x + w - r, y + h);
    GL11.glVertex2i(x + r, y + h);
    GL11.glVertex2i(x, y + r);
    GL11.glVertex2i(x + r, y + r);
    GL11.glVertex2i(x + r, y + h - r);
    GL11.glVertex2i(x, y + h - r);
    GL11.glVertex2i(x + w - r, y + r);
    GL11.glVertex2i(x + w, y + r);
    GL11.glVertex2i(x + w, y + h - r);
    GL11.glVertex2i(x + w - r, y + h - r);
    GL11.glEnd();

    // Corners: triangle fans (angles in standard math: +x right, +y up in trig; screen y increases
    // down)
    cornerFan(x + r, y + r, r, Math.PI, Math.PI / 2, segs);
    cornerFan(x + w - r, y + r, r, Math.PI / 2, 0, segs);
    cornerFan(x + w - r, y + h - r, r, 0, -Math.PI / 2, segs);
    cornerFan(x + r, y + h - r, r, -Math.PI / 2, -Math.PI, segs);
  }

  private void cornerFan(
      float cx, float cy, float r, double startAngle, double endAngle, int segments) {
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    GL11.glVertex2f(cx, cy);
    for (int i = 0; i <= segments; i++) {
      double t = i / (double) segments;
      double a = startAngle + (endAngle - startAngle) * t;
      float px = cx + (float) (r * Math.cos(a));
      float py = cy - (float) (r * Math.sin(a));
      GL11.glVertex2f(px, py);
    }
    GL11.glEnd();
  }

  public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
    GL11.glBegin(GL11.GL_TRIANGLES);
    GL11.glVertex2i(x1, y1);
    GL11.glVertex2i(x2, y2);
    GL11.glVertex2i(x3, y3);
    GL11.glEnd();
  }

  public void drawPoint(int x, int y) {
    GL11.glBegin(GL11.GL_POINTS);
    GL11.glVertex2i(x, y);
    GL11.glEnd();
  }

  public void drawPoint(Vec2 vec2) {
    GL11.glBegin(GL11.GL_POINTS);
    GL11.glVertex2i(vec2.x(), vec2.y());
    GL11.glEnd();
  }

  // ===== Farbe =====
  public void setColor(float r, float g, float b, float a) {
    GL11.glColor4f(r, g, b, a);
  }

  // ===== Clear & Update =====
  public void clear(float r, float g, float b, float a) {
    window.clear(r, g, b, a);
  }

  public void update() {
    window.update();
  }
}
