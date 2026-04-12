package net.twelfthengine.renderer.legacy;

import java.nio.FloatBuffer;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.obj.ObjModel;
import net.twelfthengine.renderer.obj.VboModel;
import net.twelfthengine.world.World;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * LegacyRenderer — OpenGL fixed-function pipeline (pre-core-profile).
 *
 * <p>Owns everything that uses: - glBegin / glEnd - GL_LIGHTING / GL_LIGHT0 - glPushMatrix /
 * glPopMatrix / glLoadMatrixf - glColor*, glNormal*, glVertex*, glTexCoord* - drawFilledBox,
 * drawWireCube, drawLine, drawPoint, drawAxes
 *
 * <p>Nothing in here should be touched when migrating to a modern pipeline. To disable legacy
 * rendering, simply stop calling this subsystem.
 */
public class LegacyRenderer {

  // =============================
  // CACHED FLOAT BUFFERS
  // FIX: Allocate once; rewind (clear()) before each use instead of
  //      creating a new FloatBuffer on every applyCamera() call.
  // =============================

  private final FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
  private final FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);

  // Scratch Matrix4f instances reused across applyCamera calls.
  private final Matrix4f proj = new Matrix4f();
  private final Matrix4f view = new Matrix4f();

  // =============================
  // FIXED-FUNCTION STATE SETUP
  // =============================

  /**
   * Call once after the GL context is current to initialise the legacy lighting model that was
   * previously set up inside Renderer3D's constructor.
   */
  public void initLegacyLighting() {
    GL11.glEnable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_LIGHT0);
    GL11.glEnable(GL11.GL_COLOR_MATERIAL);

    float[] lightPos = {0f, 10f, 10f, 1.0f};
    FloatBuffer buffer = BufferUtils.createFloatBuffer(4).put(lightPos);
    buffer.flip();
    GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, buffer);
  }

  // =============================
  // CAMERA (fixed-function)
  // FIX: Reuse Matrix4f and FloatBuffer instances; no per-call heap allocation.
  // =============================

  /**
   * Pushes projection + view matrices into the OpenGL fixed-function stack. Call this before any
   * legacy draw calls for a given frame.
   */
  public void applyCamera(CameraEntity cam, float fovDegrees, int width, int height) {
    float aspect = (float) width / height;

    proj.identity().perspective((float) Math.toRadians(fovDegrees), aspect, 0.1f, 1000f);

    Vec3 pos = cam.getPosition();
    view.identity()
        .rotateX((float) Math.toRadians(cam.getPitch()))
        .rotateY((float) Math.toRadians(cam.getYaw()))
        .translate(-pos.x(), -pos.y(), -pos.z());

    projBuffer.clear();
    proj.get(projBuffer);
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadMatrixf(projBuffer);

    viewBuffer.clear();
    view.get(viewBuffer);
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadMatrixf(viewBuffer);
  }

  // =============================
  // SCENE RENDERING
  // =============================

  /**
   * Renders every entity in the world using the fixed-function pipeline. Equivalent to the old
   * Renderer3D#renderLegacyScene.
   */
  public void renderScene(
      World world, ModelRenderer modelRenderer, TextureRenderer textureRenderer) {
    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      if (e instanceof BasicPlaneEntity plane) {
        GL11.glCullFace(GL11.GL_FRONT);
        drawPlane(plane);
      } else if (e instanceof ModelEntity modelEntity) {
        modelRenderer.renderLegacy(modelEntity);
      } else if (e instanceof TextureEntity te) {
        textureRenderer.renderLegacy(te);
      }
    }
  }

  // =============================
  // PRIMITIVES
  // =============================

  public void drawPoint(Vec3 p) {
    GL11.glBegin(GL11.GL_POINTS);
    GL11.glVertex3f(p.x(), p.y(), p.z());
    GL11.glEnd();
  }

  public void drawLine(Vec3 a, Vec3 b) {
    GL11.glBegin(GL11.GL_LINES);
    GL11.glVertex3f(a.x(), a.y(), a.z());
    GL11.glVertex3f(b.x(), b.y(), b.z());
    GL11.glEnd();
  }

  /** Draws a wireframe cube centred at {@code pos} with edge length {@code size}. */
  public void drawWireCube(Vec3 pos, float size) {
    float s = size / 2f;
    GL11.glPushMatrix();
    GL11.glTranslatef(pos.x(), pos.y(), pos.z());

    GL11.glBegin(GL11.GL_LINE_LOOP); // Top
    GL11.glVertex3f(-s, s, -s);
    GL11.glVertex3f(s, s, -s);
    GL11.glVertex3f(s, s, s);
    GL11.glVertex3f(-s, s, s);
    GL11.glEnd();

    GL11.glBegin(GL11.GL_LINE_LOOP); // Bottom
    GL11.glVertex3f(-s, -s, -s);
    GL11.glVertex3f(s, -s, -s);
    GL11.glVertex3f(s, -s, s);
    GL11.glVertex3f(-s, -s, s);
    GL11.glEnd();

    GL11.glBegin(GL11.GL_LINES); // Verticals
    GL11.glVertex3f(-s, -s, -s);
    GL11.glVertex3f(-s, s, -s);
    GL11.glVertex3f(s, -s, -s);
    GL11.glVertex3f(s, s, -s);
    GL11.glVertex3f(s, -s, s);
    GL11.glVertex3f(s, s, s);
    GL11.glVertex3f(-s, -s, s);
    GL11.glVertex3f(-s, s, s);
    GL11.glEnd();

    GL11.glPopMatrix();
  }

  /** Draws a filled AABB between {@code min} and {@code max}. */
  public void drawFilledBox(Vec3 min, Vec3 max) {
    GL11.glBegin(GL11.GL_QUADS);

    // Front (Z+)
    GL11.glNormal3d(0, 0, 1);
    GL11.glVertex3f(min.x(), min.y(), max.z());
    GL11.glVertex3f(max.x(), min.y(), max.z());
    GL11.glVertex3f(max.x(), max.y(), max.z());
    GL11.glVertex3f(min.x(), max.y(), max.z());

    // Back (Z-)
    GL11.glNormal3d(0, 0, -1);
    GL11.glVertex3f(min.x(), min.y(), min.z());
    GL11.glVertex3f(min.x(), max.y(), min.z());
    GL11.glVertex3f(max.x(), max.y(), min.z());
    GL11.glVertex3f(max.x(), min.y(), min.z());

    // Top (Y+)
    GL11.glNormal3d(0, 1, 0);
    GL11.glVertex3f(min.x(), max.y(), min.z());
    GL11.glVertex3f(min.x(), max.y(), max.z());
    GL11.glVertex3f(max.x(), max.y(), max.z());
    GL11.glVertex3f(max.x(), max.y(), min.z());

    // Bottom (Y-)
    GL11.glNormal3d(0, -1, 0);
    GL11.glVertex3f(min.x(), min.y(), min.z());
    GL11.glVertex3f(max.x(), min.y(), min.z());
    GL11.glVertex3f(max.x(), min.y(), max.z());
    GL11.glVertex3f(min.x(), min.y(), max.z());

    // Right (X+)
    GL11.glNormal3d(1, 0, 0);
    GL11.glVertex3f(max.x(), min.y(), min.z());
    GL11.glVertex3f(max.x(), max.y(), min.z());
    GL11.glVertex3f(max.x(), max.y(), max.z());
    GL11.glVertex3f(max.x(), min.y(), max.z());

    // Left (X-)
    GL11.glNormal3d(-1, 0, 0);
    GL11.glVertex3f(min.x(), min.y(), min.z());
    GL11.glVertex3f(min.x(), min.y(), max.z());
    GL11.glVertex3f(min.x(), max.y(), max.z());
    GL11.glVertex3f(min.x(), max.y(), min.z());

    GL11.glEnd();
  }

  public void drawPlane(BasicPlaneEntity plane) {
    float w = plane.getWidth() / 2f;
    float l = plane.getLength() / 2f;
    float y = plane.getTop();

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glNormal3f(0f, 1f, 0f);
    GL11.glVertex3f(-w, y, -l);
    GL11.glVertex3f(w, y, -l);
    GL11.glVertex3f(w, y, l);
    GL11.glVertex3f(-w, y, l);
    GL11.glEnd();
  }

  // =============================
  // STATE HELPERS
  // =============================

  public void setColor(float r, float g, float b, float a) {
    GL11.glColor4f(r, g, b, a);
  }

  public void setPointSize(float size) {
    GL11.glPointSize(size);
  }

  public void setLineWidth(float width) {
    GL11.glLineWidth(width);
  }

  public void setAntialiasing(boolean enabled) {
    if (enabled) {
      GL11.glEnable(GL11.GL_LINE_SMOOTH);
      GL11.glEnable(GL11.GL_POINT_SMOOTH);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
      GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
    } else {
      GL11.glDisable(GL11.GL_LINE_SMOOTH);
      GL11.glDisable(GL11.GL_POINT_SMOOTH);
      GL11.glDisable(GL11.GL_BLEND);
    }
  }

  public void setMultisampling(boolean enabled) {
    // Only effective if the window was created with multisampling enabled.
    if (enabled) GL11.glEnable(0x809D); // GL_MULTISAMPLE
    else GL11.glDisable(0x809D);
  }

  // =============================
  // DEBUG HELPERS
  // =============================

  /** Draws the world axes: X=Red, Y=Green, Z=Blue. */
  public void drawAxes(float length) {
    setLineWidth(2f);
    setColor(1, 0, 0, 1);
    drawLine(Vec3.ZERO, new Vec3(length, 0, 0));
    setColor(0, 1, 0, 1);
    drawLine(Vec3.ZERO, new Vec3(0, length, 0));
    setColor(0, 0, 1, 1);
    drawLine(Vec3.ZERO, new Vec3(0, 0, length));
    setColor(1, 1, 1, 1);
  }

  // =============================
  // INNER HELPERS (delegated rendering)
  // =============================

  /**
   * Thin helper that handles the fixed-function matrix stack for a ModelEntity. The actual VBO draw
   * call still lives in VboModel — this class just wraps the glPushMatrix/glPopMatrix dance that
   * belongs to the legacy pipeline.
   */
  public static class ModelRenderer {
    private final java.util.function.Function<String, VboModel> vboSupplier;
    private final java.util.function.Function<String, ObjModel> objSupplier;

    public ModelRenderer(
        java.util.function.Function<String, VboModel> vboSupplier,
        java.util.function.Function<String, ObjModel> objSupplier) {
      this.vboSupplier = vboSupplier;
      this.objSupplier = objSupplier;
    }

    public void renderLegacy(ModelEntity entity) {
      String path = entity.getModelPath();
      if (path == null || path.isEmpty()) return;

      VboModel vbo = vboSupplier.apply(path);
      ObjModel obj = objSupplier.apply(path);
      if (vbo == null || obj == null) return;

      GL11.glPushMatrix();

      Vec3 pos = entity.getPosition();
      GL11.glTranslatef(pos.x(), pos.y(), pos.z());

      Vec3 rot = entity.getRotation();
      if (rot.z() != 0) GL11.glRotatef(rot.z(), 0, 0, 1);
      if (rot.y() != 0) GL11.glRotatef(rot.y(), 0, 1, 0);
      if (rot.x() != 0) GL11.glRotatef(rot.x(), 1, 0, 0);

      float s = entity.getSize();
      GL11.glScalef(s, s, s);

      vbo.render(obj);

      GL11.glPopMatrix();
    }
  }

  /** Thin helper for TextureEntity rendering via the fixed-function pipeline. */
  public static class TextureRenderer {
    private final java.util.function.Function<String, Integer> textureSupplier;

    public TextureRenderer(java.util.function.Function<String, Integer> textureSupplier) {
      this.textureSupplier = textureSupplier;
    }

    public void renderLegacy(TextureEntity te) {
      String path = te.getTexturePath();
      if (path == null || path.isEmpty()) return;

      int texId = textureSupplier.apply(path);

      GL11.glPushMatrix();
      GL11.glTranslatef(te.getPosition().x(), te.getPosition().y(), te.getPosition().z());
      GL11.glRotatef(te.getRotation().y(), 0, 1, 0);
      GL11.glRotatef(te.getRotation().x(), 1, 0, 0);
      GL11.glScalef(te.getWidth(), te.getHeight(), 1f);

      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glColor4f(1f, 1f, 1f, 1f);

      GL11.glBegin(GL11.GL_QUADS);
      GL11.glNormal3f(0, 0, 1);
      GL11.glTexCoord2f(0, 0);
      GL11.glVertex3f(-0.5f, -0.5f, 0);
      GL11.glTexCoord2f(1, 0);
      GL11.glVertex3f(0.5f, -0.5f, 0);
      GL11.glTexCoord2f(1, 1);
      GL11.glVertex3f(0.5f, 0.5f, 0);
      GL11.glTexCoord2f(0, 1);
      GL11.glVertex3f(-0.5f, 0.5f, 0);
      GL11.glEnd();

      GL11.glDisable(GL11.GL_BLEND);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glPopMatrix();
    }
  }
}
