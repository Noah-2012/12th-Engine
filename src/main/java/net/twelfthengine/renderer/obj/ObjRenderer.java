package net.twelfthengine.renderer.obj;

import static org.lwjgl.opengl.GL11.*;

import net.twelfthengine.math.Vec2f;
import net.twelfthengine.math.Vec3;

@Deprecated
public class ObjRenderer {
  public enum RenderMode {
    SOLID,
    WIREFRAME_ONLY,
    SOLID_WITH_WIRE
  }

  private RenderMode mode = RenderMode.SOLID;

  public void setRenderMode(RenderMode mode) {
    this.mode = mode;
  }

  public void render(ObjModel model) {
    if (model == null) return;

    if (mode == RenderMode.SOLID || mode == RenderMode.SOLID_WITH_WIRE) {
      glEnable(GL_LIGHTING);
      glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
      draw(model);
    }

    if (mode == RenderMode.WIREFRAME_ONLY || mode == RenderMode.SOLID_WITH_WIRE) {
      glDisable(GL_LIGHTING);
      glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
      glColor3f(1, 1, 1); // Wireframe weiß
      draw(model);
      glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
      glEnable(GL_LIGHTING);
    }
  }

  private void draw(ObjModel model) {
    String lastMat = "";

    // Wir starten den ersten Block
    glBegin(GL_TRIANGLES);

    for (ObjModel.Face face : model.faces) {
      // Materialwechsel prüfen
      if (!face.materialName.equals(lastMat)) {
        glEnd(); // Aktuellen Zeichenvorgang unterbrechen für State-Change

        ObjModel.Material m = model.materials.get(face.materialName);
        if (m != null) {
          if (m.textureID != -1) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, m.textureID);
            glColor3f(1.0f, 1.0f, 1.0f); // Weiß, damit die Textur nicht eingefärbt wird
          } else {
            glDisable(GL_TEXTURE_2D);
            glColor3f(m.diffuseColor.x(), m.diffuseColor.y(), m.diffuseColor.z());
          }
        }

        lastMat = face.materialName;
        glBegin(GL_TRIANGLES); // Neuen Block starten
      }

      for (int i = 0; i < 3; i++) {
        if (face.hasNormals) {
          Vec3 n = model.normals.get(face.normalIndices[i]);
          glNormal3f(n.x(), n.y(), n.z());
        }

        if (face.hasUVs) {
          Vec2f uv = model.uvs.get(face.uvIndices[i]);
          // STB lädt von oben nach unten, OpenGL von unten nach oben
          // Falls die Textur auf dem Kopf steht, nimm: 1.0f - uv.y
          glTexCoord2f(uv.x(), 1.0f - uv.y());
        }

        Vec3 v = model.vertices.get(face.vertexIndices[i]);
        glVertex3f(v.x(), v.y(), v.z());
      }
    }
    glEnd();
    glDisable(GL_TEXTURE_2D); // Sauber hinterlassen
  }
}
