package net.twelfthengine.renderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.twelfthengine.core.resources.ResourceExtractor;
import net.twelfthengine.math.Vec2;
import net.twelfthengine.window.Window;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class Renderer2D {

  private final Window window;

  private final Map<String, Integer> textureCache = new HashMap<>();

  public Renderer2D(Window window) {
    this.window = window;

    // begin2D();
  }

  public int getWidth() {
    return window.getWidth(); // ← live, not stale
  }

  public int getHeight() {
    return window.getHeight(); // ← add this too, callers will need it
  }

  public void begin2D() {
    int width = window.getWidth(); // ← read live each frame
    int height = window.getHeight();

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

  public void drawTexture(String resourcePath, int x, int y, int width, int height) {
    int texID = getOrCreateTexture(resourcePath);
    if (texID == -1) return;

    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texID);

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2f(0, 0);
    GL11.glVertex2i(x, y);
    GL11.glTexCoord2f(1, 0);
    GL11.glVertex2i(x + width, y);
    GL11.glTexCoord2f(1, 1);
    GL11.glVertex2i(x + width, y + height);
    GL11.glTexCoord2f(0, 1);
    GL11.glVertex2i(x, y + height);
    GL11.glEnd();

    GL11.glDisable(GL11.GL_TEXTURE_2D);
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

  private int getOrCreateTexture(String path) {
    if (textureCache.containsKey(path)) {
      return textureCache.get(path);
    }

    try {
      // 1. Get bytes from your ResourceExtractor
      byte[] data = ResourceExtractor.readBytes(path);

      // 2. Decode image
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      int texID = loadToGPU(image);

      // 3. Cache it
      textureCache.put(path, texID);
      return texID;
    } catch (IOException e) {
      System.err.println("Failed to load texture: " + path + " - " + e.getMessage());
      return -1;
    }
  }

  private int loadToGPU(BufferedImage image) {
    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

    ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = pixels[y * image.getWidth() + x];
        buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
        buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green
        buffer.put((byte) (pixel & 0xFF)); // Blue
        buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
      }
    }
    buffer.flip();

    int id = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

    // Setup filtering (Linear for smoothness, Nearest for pixel art)
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

    GL11.glTexImage2D(
        GL11.GL_TEXTURE_2D,
        0,
        GL11.GL_RGBA8,
        image.getWidth(),
        image.getHeight(),
        0,
        GL11.GL_RGBA,
        GL11.GL_UNSIGNED_BYTE,
        buffer);

    return id;
  }
}
