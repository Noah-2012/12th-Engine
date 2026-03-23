package net.twelfthengine.renderer.mesh;

import java.nio.FloatBuffer;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public class PlaneMesh {

  private final int vboId;
  private static final int VERTEX_COUNT = 6; // 2 Triangles
  private static final int FLOATS_PER_VERTEX = 8; // pos(3) + normal(3) + uv(2)

  public PlaneMesh() {
    // Eine einzige flache Face von -0.5 bis 0.5 auf Y=0
    float[] data = {
      // pos(x,y,z)      normal(nx,ny,nz)   uv(u,v)
      -0.5f,
      0f,
      -0.5f,
      0f,
      1f,
      0f,
      0f,
      0f,
      0.5f,
      0f,
      -0.5f,
      0f,
      1f,
      0f,
      1f,
      0f,
      0.5f,
      0f,
      0.5f,
      0f,
      1f,
      0f,
      1f,
      1f,
      -0.5f,
      0f,
      -0.5f,
      0f,
      1f,
      0f,
      0f,
      0f,
      0.5f,
      0f,
      0.5f,
      0f,
      1f,
      0f,
      1f,
      1f,
      -0.5f,
      0f,
      0.5f,
      0f,
      1f,
      0f,
      0f,
      1f,
    };

    FloatBuffer buf = BufferUtils.createFloatBuffer(data.length);
    buf.put(data).flip();

    vboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void drawDepth(ShaderProgram shader, Matrix4f lightMvp) {
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    int stride = FLOATS_PER_VERTEX * Float.BYTES;
    GL20.glEnableVertexAttribArray(0);
    GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);
    GL20.glDisableVertexAttribArray(1);
    GL20.glDisableVertexAttribArray(2);
    shader.setUniformMatrix4fv("uLightMVP", false, ShaderProgram.matrixToBuffer(lightMvp));
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

    shader.setUniformMatrix4fv("uModel", false, ShaderProgram.matrixToBuffer(model));
    shader.setUniformMatrix4fv("uView", false, ShaderProgram.matrixToBuffer(view));
    shader.setUniformMatrix4fv("uProjection", false, ShaderProgram.matrixToBuffer(projection));
    shader.setUniformMatrix4fv(
        "uLightSpaceMatrix", false, ShaderProgram.matrixToBuffer(lightSpace));
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
