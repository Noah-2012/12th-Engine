package net.twelfthengine.renderer.obj;

import java.nio.FloatBuffer;
import java.util.*;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class VboModel {
  private int vaoId;
  private int vboId;
  private final List<MaterialBatch> batches = new ArrayList<>();
  private float size = 1.0f;

  private static class MaterialBatch {
    String materialName;
    int startIndex;
    int vertexCount;
  }

  public VboModel(ObjModel model) {
    if (model == null) return;

    // Group faces by material
    Map<String, List<ObjModel.Face>> groupedFaces = new HashMap<>();
    for (ObjModel.Face face : model.faces) {
      groupedFaces.computeIfAbsent(face.materialName, k -> new ArrayList<>()).add(face);
    }

    int totalVertices = model.faces.size() * 3;
    FloatBuffer buffer = BufferUtils.createFloatBuffer(totalVertices * 8);

    int currentOffset = 0;
    for (Map.Entry<String, List<ObjModel.Face>> entry : groupedFaces.entrySet()) {
      MaterialBatch batch = new MaterialBatch();
      batch.materialName = entry.getKey();
      batch.startIndex = currentOffset;

      List<ObjModel.Face> faces = entry.getValue();
      for (ObjModel.Face face : faces) {
        for (int i = 0; i < 3; i++) {
          // Position
          Vec3 v = model.vertices.get(face.vertexIndices[i]);
          buffer.put(v.x()).put(v.y()).put(v.z());

          // Normal
          Vec3 n = face.hasNormals ? model.normals.get(face.normalIndices[i]) : new Vec3(0, 1, 0);
          buffer.put(n.x()).put(n.y()).put(n.z());

          // UV (flip Y)
          net.twelfthengine.math.Vec2f uv =
              face.hasUVs
                  ? model.uvs.get(face.uvIndices[i])
                  : new net.twelfthengine.math.Vec2f(0, 0);
          buffer.put(uv.x()).put(1.0f - uv.y());

          currentOffset++;
        }
      }
      batch.vertexCount = faces.size() * 3;
      batches.add(batch);
    }
    buffer.flip();

    // Create VAO
    vaoId = GL30.glGenVertexArrays();
    GL30.glBindVertexArray(vaoId);

    // Create VBO and upload data
    vboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

    int stride = 8 * 4; // 8 floats per vertex: 3 pos, 3 normal, 2 uv

    // Position attribute (location 0)
    GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
    GL20.glEnableVertexAttribArray(0);

    // Normal attribute (location 1)
    GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * 4);
    GL20.glEnableVertexAttribArray(1);

    // UV attribute (location 2)
    GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6 * 4);
    GL20.glEnableVertexAttribArray(2);

    // Unbind VAO (optional but good practice)
    GL30.glBindVertexArray(0);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void setSize(float size) {
    this.size = Math.max(0.001f, size);
  }

  public float getSize() {
    return size;
  }

  /**
   * Legacy fixed-function rendering (used by LegacyRenderer). This still uses client-side arrays –
   * if you never use the legacy path, you can remove this method entirely.
   */
  public void render(ObjModel sourceModel) {
    GL11.glPushMatrix();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

    int stride = 8 * 4;
    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
    GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);
    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
    GL11.glNormalPointer(GL11.GL_FLOAT, stride, 3 * 4);
    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 6 * 4);

    GL11.glScalef(size, size, size);

    for (MaterialBatch batch : batches) {
      ObjModel.Material mat = sourceModel.materials.get(batch.materialName);
      if (mat != null) {
        if (mat.textureID != -1) {
          GL11.glEnable(GL11.GL_TEXTURE_2D);
          GL11.glBindTexture(GL11.GL_TEXTURE_2D, mat.textureID);
          GL11.glColor3f(1, 1, 1);
        } else {
          GL11.glDisable(GL11.GL_TEXTURE_2D);
          GL11.glColor3f(mat.diffuseColor.x(), mat.diffuseColor.y(), mat.diffuseColor.z());
        }
      }
      GL11.glDrawArrays(GL11.GL_TRIANGLES, batch.startIndex, batch.vertexCount);
    }

    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
    GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    GL11.glPopMatrix();
  }

  /** Depth-only pass for shadow map (modern pipeline). */
  public void renderDepth(ShaderProgram shader, Matrix4f lightMvp) {
    GL30.glBindVertexArray(vaoId);

    // FIX: Use the non-deprecated Matrix4f overload which reuses
    //      ShaderProgram's cached off-heap FloatBuffer instead of allocating
    //      a fresh one via BufferUtils.createFloatBuffer(16) every call.
    //      (Noted as a hazard in ShaderProgram.matrixToBuffer's deprecation
    //      javadoc, and already fixed for MotionBlurEffect — now fixed here
    //      too.) Saves ~1 direct NIO allocation per entity per shadow pass.
    shader.setUniformMatrix4fv("uLightMVP", false, lightMvp);

    for (MaterialBatch batch : batches) {
      GL11.glDrawArrays(GL11.GL_TRIANGLES, batch.startIndex, batch.vertexCount);
    }

    GL30.glBindVertexArray(0);
  }

  /** Lit pass with shadows (modern pipeline). */
  public void renderLit(
      ObjModel sourceModel,
      ShaderProgram shader,
      Matrix4f model,
      Matrix4f view,
      Matrix4f projection,
      Matrix4f lightSpace) {

    GL30.glBindVertexArray(vaoId);

    // FIX: Same deprecated-allocation fix as renderDepth. Four matrix uploads
    //      per entity per lit pass used to each allocate a fresh off-heap
    //      FloatBuffer (~4 direct NIO allocs per entity per frame → GC
    //      pressure through the JVM Cleaner). The overload below copies into
    //      ShaderProgram's cached internal buffer instead.
    //
    //      NOTE: uView / uProjection / uLightSpaceMatrix are intentionally
    //      still uploaded per-entity here rather than once per pass in
    //      Renderer3D#renderLitScene, because hoisting them caused visual
    //      regressions — keeping the original call order on purpose.
    shader.setUniformMatrix4fv("uModel", false, model);
    shader.setUniformMatrix4fv("uView", false, view);
    shader.setUniformMatrix4fv("uProjection", false, projection);
    shader.setUniformMatrix4fv("uLightSpaceMatrix", false, lightSpace);

    for (MaterialBatch batch : batches) {
      ObjModel.Material mat = sourceModel.materials.get(batch.materialName);
      if (mat != null && mat.textureID != -1) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mat.textureID);
        shader.setUniform1i("uUseTexture", 1);
        shader.setUniform3f("uBaseColor", 1f, 1f, 1f);
      } else {
        shader.setUniform1i("uUseTexture", 0);
        Vec3 c = mat != null ? mat.diffuseColor : new Vec3(0.75f, 0.75f, 0.78f);
        shader.setUniform3f("uBaseColor", c.x(), c.y(), c.z());
      }
      GL11.glDrawArrays(GL11.GL_TRIANGLES, batch.startIndex, batch.vertexCount);
    }

    GL30.glBindVertexArray(0);
  }

  /** Release GPU resources. */
  public void dispose() {
    GL30.glDeleteVertexArrays(vaoId);
    GL15.glDeleteBuffers(vboId);
  }
}
