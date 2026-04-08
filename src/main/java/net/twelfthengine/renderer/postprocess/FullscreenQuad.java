package net.twelfthengine.renderer.postprocess;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class FullscreenQuad {

  private final int vaoId;
  private final int vboId;

  public FullscreenQuad() {
    // Two triangles covering clip space (-1 to 1)
    float[] verts = {
      -1f, -1f,
      1f, -1f,
      1f, 1f,
      -1f, -1f,
      1f, 1f,
      -1f, 1f,
    };

    FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
    buf.put(verts).flip();

    vaoId = GL30.glGenVertexArrays();
    GL30.glBindVertexArray(vaoId);

    vboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);

    GL20.glEnableVertexAttribArray(0);
    GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

    GL30.glBindVertexArray(0);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void draw() {
    GL30.glBindVertexArray(vaoId);
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    GL30.glBindVertexArray(0);
  }

  public void dispose() {
    GL30.glDeleteVertexArrays(vaoId);
    GL15.glDeleteBuffers(vboId);
  }
}
