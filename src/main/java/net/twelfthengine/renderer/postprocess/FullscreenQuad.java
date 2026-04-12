package net.twelfthengine.renderer.postprocess;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * FullscreenQuad — uploads two triangles covering clip-space and draws them.
 *
 * <p><b>NOTE:</b> {@link PostProcessPipeline} already uses the zero-VBO {@code gl_VertexID}
 * triangle trick, which is strictly cheaper than this class (no VBO allocation, no buffer upload,
 * no attribute pointer setup, one less bind per draw). If you are writing a new effect shader,
 * prefer that approach and use the pipeline's dummy VAO directly.
 *
 * <p>This class is kept for any existing effect that was wired up to it before the pipeline was
 * updated. If nothing outside the post-process package references it, it is safe to delete.
 */
public class FullscreenQuad {

  private final int vaoId;
  private final int vboId;

  public FullscreenQuad() {
    float[] verts = {
      -1f, -1f,
      1f, -1f,
      1f, 1f,
      -1f, -1f,
      1f, 1f,
      -1f, 1f,
    };

    // The FloatBuffer is only needed during construction to upload data to
    // the GPU.  It is intentionally not stored as a field — it can be GC'd
    // immediately after glBufferData returns.
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
