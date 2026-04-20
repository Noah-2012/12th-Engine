package net.twelfthengine.renderer;

import java.nio.ByteBuffer;
import net.twelfthengine.core.console.Console;
import net.twelfthengine.math.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBEasyFont;

/**
 * Renders text in 2‑D screen space or 3‑D world space using the lightweight
 * {@link org.lwjgl.stb.STBEasyFont STBEasyFont} rasterizer. The renderer
 * exposes simple methods that temporarily disable lighting, enable alpha blending,
 * and push the current OpenGL matrix to isolate transformations.
 *
 * <p>Typical usage patterns:</p>
 * <pre>{@code
 * TextRenderer renderer = new TextRenderer();
 * renderer.drawText2D("Hello, world!", 50, 75, 1f, 1f, 1f, 1f);
 * renderer.drawText3D("Beacon", new Vec3(0,10,5), 0.5f, 1f, 0f, 0f, 1f);
 * }</pre>
 *
 * <p>Key configuration points:</p>
 * <ul>
 *   <li>Maximum characters (`MAX_CHARS`) and vertex buffer size (`VERTEX_BUFFER_SIZE`) are
 *   bound to console variables for runtime tuning.</li>
 *   <li>{@link #DEFAULT_FONT_SCALE} is the base scale applied by the {@code drawText*}
 *   overloads that omit an explicit scale.</li>
 * </ul>
 *
 * <p>All drawing methods quietly return if the supplied text is {@code null} or empty,
 * or if a non‑positive scale is provided. The renderer leaves no permanent state
 * behind, ensuring it can be safely called from arbitrary rendering contexts.</p>
 *
 * @see #drawText2D(String,float,float,float,float,float,float)
 * @see #drawText3D(String,Vec3,float,float,float,float,float)
 * @see #getTextWidth(String)
 */
public class TextRenderer {
  private static int MAX_CHARS = 512;
  private static int VERTEX_BUFFER_SIZE = MAX_CHARS * 270;
  private static float DEFAULT_FONT_SCALE = 1.0f;

  static {
    Console.bindInt("cv_text_renderer_max_chars", () -> MAX_CHARS, v -> MAX_CHARS = v);
    Console.bindInt(
            "cv_text_renderer_vertex_buffer_size",
            () -> VERTEX_BUFFER_SIZE,
            v -> VERTEX_BUFFER_SIZE = v);
    Console.bindFloat(
            "cv_text_renderer_default_font_size",
            () -> DEFAULT_FONT_SCALE,
            v -> DEFAULT_FONT_SCALE = v);
  }

  private final ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(VERTEX_BUFFER_SIZE);

  /**
   * Draws the supplied {@code text} at the specified 2‑D screen coordinates using the default font scale.
   *
   * <p>If {@code text} is {@code null} or empty the method exits silently. The coordinates {@code x} and
   * {@code y} are in pixel space relative to the lower-left corner of the viewport. The color is
   * specified by the {@code r}, {@code g}, {@code b} and {@code a} components, each ranging from 0 to 1.
   *
   * @param text the string to render; if {@code null} or empty no rendering occurs
   * @param x    the horizontal position in pixels
   * @param y    the vertical position in pixels
   * @param r    red component of the color (0‑1)
   * @param g    green component of the color (0‑1)
   * @param b    blue component of the color (0‑1)
   * @param a    alpha component of the color (0‑1)
   * @see #drawText2D(String, float, float, float, float, float, float, float)
   */
  public void drawText2D(String text, float x, float y, float r, float g, float b, float a) {
    drawText2D(text, x, y, DEFAULT_FONT_SCALE, r, g, b, a);
  }

  /**
   * Draws the supplied {@code text} at the specified 2‑D screen coordinates with an explicit
   * {@code fontScale}.
   * <p>
   * If {@code text} is {@code null} or empty, or if {@code fontScale} is zero or negative, the
   * method returns immediately. The method temporarily disables lighting, enables blending,
   * sets the current color to {@code (r,g,b,a)}, translates to {@code (x,y,0)} and scales
   * uniformly by {@code fontScale}. The text is then rendered using {@link STBEasyFont}.
   * <p>
   * The viewport origin is the lower‑left corner of the screen in pixel coordinates.
   *
   * @param text      the string to render; ignored if {@code null} or empty
   * @param x         horizontal position in pixels
   * @param y         vertical position in pixels
   * @param fontScale scaling factor applied uniformly in x‑ and y‑direction
   * @param r         red component of colour (0–1)
   * @param g         green component of colour (0–1)
   * @param b         blue component of colour (0–1)
   * @param a         alpha component of colour (0–1)
   * @see #drawText2D(String, float, float, float, float, float, float, float)
   */
  public void drawText2D(
          String text, float x, float y, float fontScale, float r, float g, float b, float a) {
    if (text == null || text.isEmpty()) return;
    if (fontScale <= 0f) return;

    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4f(r, g, b, a);

    GL11.glPushMatrix();
    GL11.glTranslatef(x, y, 0f);
    GL11.glScalef(fontScale, fontScale, 1f);
    renderEasyFontQuads(text, 0f, 0f);
    GL11.glPopMatrix();
  }

  /**
   * Renders the supplied {@code text} at a 3‑D world position with a uniform scale and color.
   *
   * <p>The method temporarily disables lighting, enables blending, sets the current color
   * to {@code (r,g,b,a)}, then translates to {@code worldPosition} and scales by {@code scale}
   * in all three axes before drawing the text using {@link STBEasyFont} quad geometry.
   *
   * <p>If {@code text} is {@code null} or empty the method exits without drawing.
   *
   * @param text          the string to render; ignored if {@code null} or empty
   * @param worldPosition position in world coordinates to place the text
   * @param scale         uniform scaling factor applied to all axes
   * @param r             red component of the color (0–1)
   * @param g             green component of the color (0–1)
   * @param b             blue component of the color (0–1)
   * @param a             alpha component of the color (0–1)
   * @see #drawText2D(String, float, float, float, float, float, float, float)
   */
  public void drawText3D(
          String text, Vec3 worldPosition, float scale, float r, float g, float b, float a) {
    if (text == null || text.isEmpty()) return;

    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glColor4f(r, g, b, a);

    GL11.glPushMatrix();
    GL11.glTranslatef(worldPosition.x(), worldPosition.y(), worldPosition.z());
    GL11.glScalef(scale, scale, scale);
    renderEasyFontQuads(text, 0f, 0f);
    GL11.glPopMatrix();
  }

  /**
   * Computes the onscreen width of {@code text} using the renderer’s default font scale.
   *
   * <p>This method delegates to {@link #getTextWidth(String, float)} with {@code DEFAULT_FONT_SCALE}.
   *
   * @param text the string to measure; {@code null} or empty yields {@code 0}
   * @return the width (in pixels) required to render {@code text} with the default font scale
   */
  public float getTextWidth(String text) {
    return getTextWidth(text, DEFAULT_FONT_SCALE);
  }

  /**
   * Computes the onscreen width of the supplied {@code text} using the specified font scale.
   * <p>
   * The method leverages {@link STBEasyFont#stb_easy_font_width(ByteBuffer)} to obtain the base width
   * and multiplies it by {@code fontScale}. If {@code fontScale} is negative the value is clamped
   * to {@code 0f}. A {@code null} or empty {@code text} results in a width of {@code 0f}.
   *
   * @param text      the string to measure; a {@code null} or empty string yields {@code 0f}
   * @param fontScale the scaling factor applied uniformly to the base width
   * @return the width, in pixels, that would be required to render {@code text} with the given
   * scale
   */
  public float getTextWidth(String text, float fontScale) {
    if (text == null || text.isEmpty()) return 0f;
    return STBEasyFont.stb_easy_font_width(text) * Math.max(0f, fontScale);
  }

  /**
   * Renders {@code text} as a set of four‑vertex quads via {@link STBEasyFont}.
   * <p>
   * The method clears the shared {@code vertexBuffer}, fills it with vertex
   * data returned by {@code stb_easy_font_print}, and then uses the client‑side
   * vertex array API to draw the quads with {@code GL_QUADS}.  Only the first
   * two floats (x, y) of each vertex are used by the {@code glVertexPointer};
   * the remaining two are dummy texture coordinates.
   * <p>
   * The caller is responsible for positioning the current OpenGL matrix
   * (typically with {@link GL11#glPushMatrix()}, {@link GL11#glTranslatef(float, float, float)},
   * and {@link GL11#glScalef(float, float, float)}), and for restoring the
   * matrix state after the call.
   *
   * @param text the string to render; may be {@code null} or empty but the call
   *             will still succeed harmlessly
   * @param x    the x coordinate of the text origin in the current matrix
   * @param y    the y coordinate of the text origin in the current matrix
   */
  private void renderEasyFontQuads(String text, float x, float y) {
    vertexBuffer.clear();
    int numQuads = STBEasyFont.stb_easy_font_print(x, y, text, null, vertexBuffer);
    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
    GL11.glVertexPointer(2, GL11.GL_FLOAT, 16, vertexBuffer);
    GL11.glDrawArrays(GL11.GL_QUADS, 0, numQuads * 4);
    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
  }
}
