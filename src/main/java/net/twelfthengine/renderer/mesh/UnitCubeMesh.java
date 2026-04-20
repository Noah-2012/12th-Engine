package net.twelfthengine.renderer.mesh;

import java.nio.FloatBuffer;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Unit cube from -0.5 to 0.5; transform with model matrix to any box. */
public class UnitCubeMesh {

  private final int vboId;
  public static final int FLOATS_PER_VERTEX = 8;
  public static final int VERTEX_COUNT = 36;

  public UnitCubeMesh() {
    float[] d = buildInterleavedCube();
    FloatBuffer buf = BufferUtils.createFloatBuffer(d.length);
    buf.put(d);
    buf.flip();
    vboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  private static void face(
      float[] out,
      int base,
      float x1,
      float y1,
      float z1,
      float nx,
      float ny,
      float nz,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float x4,
      float y4,
      float z4) {
    int i = base;
    // tri 1
    putV(out, i, x1, y1, z1, nx, ny, nz, 0, 0);
    i += FLOATS_PER_VERTEX;
    putV(out, i, x2, y2, z2, nx, ny, nz, 1, 0);
    i += FLOATS_PER_VERTEX;
    putV(out, i, x3, y3, z3, nx, ny, nz, 1, 1);
    i += FLOATS_PER_VERTEX;
    // tri 2
    putV(out, i, x1, y1, z1, nx, ny, nz, 0, 0);
    i += FLOATS_PER_VERTEX;
    putV(out, i, x3, y3, z3, nx, ny, nz, 1, 1);
    i += FLOATS_PER_VERTEX;
    putV(out, i, x4, y4, z4, nx, ny, nz, 0, 1);
    i += FLOATS_PER_VERTEX;
  }

  private static void putV(
      float[] out,
      int i,
      float x,
      float y,
      float z,
      float nx,
      float ny,
      float nz,
      float u,
      float v) {
    out[i] = x;
    out[i + 1] = y;
    out[i + 2] = z;
    out[i + 3] = nx;
    out[i + 4] = ny;
    out[i + 5] = nz;
    out[i + 6] = u;
    out[i + 7] = v;
  }

  private static float[] buildInterleavedCube() {
    float[] out = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
    int b = 0;
    float h = 0.5f;
    // +Z
    face(out, b, h, -h, h, 0, 0, 1, h, h, h, -h, h, h, -h, -h, h);
    b += 6 * FLOATS_PER_VERTEX;
    // -Z
    face(out, b, -h, -h, -h, 0, 0, -1, h, -h, -h, h, h, -h, -h, h, -h);
    b += 6 * FLOATS_PER_VERTEX;
    // +Y top
    face(out, b, -h, h, -h, 0, 1, 0, h, h, -h, h, h, h, -h, h, h);
    b += 6 * FLOATS_PER_VERTEX;
    // -Y bottom
    face(out, b, -h, -h, h, 0, -1, 0, h, -h, h, h, -h, -h, -h, -h, -h);
    b += 6 * FLOATS_PER_VERTEX;
    // +X
    face(out, b, h, -h, -h, 1, 0, 0, h, h, -h, h, h, h, h, -h, h);
    b += 6 * FLOATS_PER_VERTEX;
    // -X
    face(out, b, -h, -h, h, -1, 0, 0, -h, h, h, -h, h, -h, -h, -h, -h);
    b += 6 * FLOATS_PER_VERTEX;
    return out;
  }

  public void drawDepth(ShaderProgram shader, String uniformMvp, Matrix4f lightMvp) {
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    int stride = FLOATS_PER_VERTEX * Float.BYTES;
    GL20.glEnableVertexAttribArray(0);
    GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);
    GL20.glDisableVertexAttribArray(1);
    GL20.glDisableVertexAttribArray(2);
    // FIX: use non-deprecated overload — no direct FloatBuffer allocation.
    shader.setUniformMatrix4fv(uniformMvp, false, lightMvp);
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);
    GL20.glDisableVertexAttribArray(0);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void drawLit(
      ShaderProgram shader,
      Matrix4f model,
      Matrix4f view,
      Matrix4f projection,
      Matrix4f lightSpace) {
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    int stride = FLOATS_PER_VERTEX * Float.BYTES;
    GL20.glEnableVertexAttribArray(0);
    GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);
    GL20.glEnableVertexAttribArray(1);
    GL20.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, stride, 3L * Float.BYTES);
    GL20.glEnableVertexAttribArray(2);
    GL20.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, stride, 6L * Float.BYTES);

    // FIX: use non-deprecated overload — no direct FloatBuffer allocation.
    shader.setUniformMatrix4fv("uModel", false, model);
    shader.setUniformMatrix4fv("uView", false, view);
    shader.setUniformMatrix4fv("uProjection", false, projection);
    shader.setUniformMatrix4fv("uLightSpaceMatrix", false, lightSpace);

    shader.setUniform1i("uUseTexture", 0);
    shader.setUniform3f("uBaseColor", 0.7f, 0.7f, 0.7f);

    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);

    GL20.glDisableVertexAttribArray(0);
    GL20.glDisableVertexAttribArray(1);
    GL20.glDisableVertexAttribArray(2);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void delete() {
    GL15.glDeleteBuffers(vboId);
  }
}
