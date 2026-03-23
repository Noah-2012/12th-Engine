package net.twelfthengine.renderer;

import java.nio.ByteBuffer;
import net.twelfthengine.math.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBEasyFont;

public class TextRenderer {
  private static final int MAX_CHARS = 512;
  private static final int VERTEX_BUFFER_SIZE = MAX_CHARS * 270;
  private static final float DEFAULT_FONT_SCALE = 1.0f;

  private final ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(VERTEX_BUFFER_SIZE);

  public void drawText2D(String text, float x, float y, float r, float g, float b, float a) {
    drawText2D(text, x, y, DEFAULT_FONT_SCALE, r, g, b, a);
  }

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

  public float getTextWidth(String text) {
    return getTextWidth(text, DEFAULT_FONT_SCALE);
  }

  public float getTextWidth(String text, float fontScale) {
    if (text == null || text.isEmpty()) return 0f;
    return STBEasyFont.stb_easy_font_width(text) * Math.max(0f, fontScale);
  }

  private void renderEasyFontQuads(String text, float x, float y) {
    vertexBuffer.clear();
    int numQuads = STBEasyFont.stb_easy_font_print(x, y, text, null, vertexBuffer);
    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
    GL11.glVertexPointer(2, GL11.GL_FLOAT, 16, vertexBuffer);
    GL11.glDrawArrays(GL11.GL_QUADS, 0, numQuads * 4);
    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
  }
}
