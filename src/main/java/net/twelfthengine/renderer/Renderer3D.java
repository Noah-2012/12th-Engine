package net.twelfthengine.renderer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import net.twelfthengine.core.resources.TwelfthPackage;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.math.Mat4;
import net.twelfthengine.math.MatrixStack3D;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.legacy.LegacyRenderer;
import net.twelfthengine.renderer.mesh.PlaneMesh;
import net.twelfthengine.renderer.mesh.TextureMesh;
import net.twelfthengine.renderer.mesh.UnitCubeMesh;
import net.twelfthengine.renderer.obj.ObjLoader;
import net.twelfthengine.renderer.obj.ObjModel;
import net.twelfthengine.renderer.obj.VboModel;
import net.twelfthengine.renderer.shader.ShaderProgram;
import net.twelfthengine.renderer.shadow.ShadowFramebuffer;
import net.twelfthengine.renderer.texture.TextureLoader;
import net.twelfthengine.world.World;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Renderer3D — the top-level 3D rendering orchestrator for 12th Engine.
 *
 * <p>Responsibilities of THIS class:
 * <ul>
 *   <li>Asset caching (OBJ, VBO, textures)</li>
 *   <li>Frame setup: shadow pass → lit pass  (or legacy fallback)</li>
 *   <li>Frustum culling</li>
 *   <li>begin3D() — writes cached view/proj to both JOML fields and the GL matrix stack</li>
 * </ul>
 *
 * <p>Responsibilities that live in their own subsystem:
 * <ul>
 *   <li>{@link LegacyRenderer} — everything that touches glBegin/glEnd, GL_LIGHTING,
 *       fixed-function matrices, drawFilledBox, drawWireCube, etc.</li>
 * </ul>
 */
public class Renderer3D {

  // =============================
  // CONFIGURATION
  // =============================

  private final int width;
  private final int height;
  private float fovDegrees = 90f;

  // =============================
  // SUBSYSTEMS
  // =============================

  /** All fixed-function / OpenGL-legacy operations. */
  private final LegacyRenderer legacy;

  // =============================
  // SHARED MESH GEOMETRY
  // =============================

  private final MatrixStack3D modelStack = new MatrixStack3D();
  private UnitCubeMesh unitCubeMesh;
  private PlaneMesh planeMesh;
  private TextureMesh textureMesh;

  // =============================
  // SHADOW / LIT PIPELINE
  // =============================

  private boolean shadowsEnabled;
  private ShaderProgram depthShader;
  private ShaderProgram litShader;
  private ShadowFramebuffer shadowFbo;

  // =============================
  // CACHED MATRICES
  // =============================

  private Matrix4f currentView = new Matrix4f();
  private Matrix4f currentProj = new Matrix4f();
  private Matrix4f lastVP      = new Matrix4f();

  public Matrix4f getLastVP() { return lastVP; }

  // =============================
  // ACTIVE FBO
  // =============================

  private int activeFboId = 0;

  public void setActiveFbo(int fboId) { this.activeFboId = fboId; }

  // =============================
  // FRUSTUM CULLING
  // =============================

  private org.joml.FrustumIntersection frustum;
  private boolean frustumCullingEnabled = true;

  public org.joml.FrustumIntersection getFrustum()                         { return frustum; }
  public boolean isFrustumCullingEnabled()                                  { return frustumCullingEnabled; }
  public void    setFrustumCullingEnabled(boolean frustumCullingEnabled)    { this.frustumCullingEnabled = frustumCullingEnabled; }

  // =============================
  // ASSET CACHES
  // =============================

  private final Map<String, ObjModel> modelCache     = new HashMap<>();
  private final Map<String, VboModel> vboCache       = new HashMap<>();
  private final Map<String, Integer>  textureIdCache = new HashMap<>();

  // =============================
  // ACCESSORS
  // =============================

  public MatrixStack3D getMatrices()      { return modelStack;    }
  public UnitCubeMesh  getUnitCubeMesh()  { return unitCubeMesh;  }
  public PlaneMesh     getPlaneMesh()     { return planeMesh;     }
  public TextureMesh   getTextureMesh()   { return textureMesh;   }

  /** Exposes the legacy subsystem so game code can call drawLine, drawAxes, etc. */
  public LegacyRenderer getLegacy()       { return legacy;        }

  public void setFovDegrees(float fovDegrees) {
    this.fovDegrees = Math.max(30f, Math.min(150f, fovDegrees));
  }

  // =============================
  // CONSTRUCTION
  // =============================

  public Renderer3D(int width, int height) {
    this.width  = width;
    this.height = height;

    GL11.glEnable(GL11.GL_DEPTH_TEST);

    // Boot the legacy subsystem and let it own its own GL state.
    legacy = new LegacyRenderer();
    legacy.initLegacyLighting();

    try {
      depthShader  = new ShaderProgram("/shaders/shadow_depth.vert", "/shaders/shadow_depth.frag");
      litShader    = new ShaderProgram("/shaders/lit_shadow.vert",   "/shaders/lit_shadow.frag");
      shadowFbo    = new ShadowFramebuffer();
      unitCubeMesh = new UnitCubeMesh();
      planeMesh    = new PlaneMesh();
      textureMesh  = new TextureMesh();
      shadowsEnabled = true;
    } catch (Exception e) {
      System.err.println("[Renderer3D] Shader shadows disabled: " + e.getMessage());
      shadowsEnabled = false;
    }
  }

  // =============================
  // ASSET LOADING
  // =============================

  public ObjModel loadObjModel(String path) {
    if (!modelCache.containsKey(path)) {
      try {
        modelCache.put(path, ObjLoader.load(path));
      } catch (IOException e) {
        System.err.println("Failed to load model: " + path);
        return null;
      }
    }
    return modelCache.get(path);
  }

  public VboModel loadVboModel(String path) {
    if (!vboCache.containsKey(path)) {
      ObjModel obj = loadObjModel(path);
      if (obj != null) vboCache.put(path, new VboModel(obj));
    }
    return vboCache.get(path);
  }

  public ObjModel loadObjModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    if (!modelCache.containsKey(key)) {
      try {
        modelCache.put(key, ObjLoader.loadFromPackage(pack, internalPath));
      } catch (IOException e) {
        System.err.println("Failed to load model from package: " + internalPath);
        return null;
      }
    }
    return modelCache.get(key);
  }

  public VboModel loadVboModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    if (!vboCache.containsKey(key)) {
      ObjModel obj = loadObjModelFromPackage(pack, internalPath);
      if (obj != null) vboCache.put(key, new VboModel(obj));
    }
    return vboCache.get(key);
  }

  public int loadTextureId(String path) {
    if (!textureIdCache.containsKey(path)) {
      textureIdCache.put(path, TextureLoader.loadTexture(path));
    }
    return textureIdCache.get(path);
  }

  // =============================
  // FRAME ENTRY POINT
  // =============================

  public void render(World world) {
    CameraEntity cam         = world.getActiveCamera();
    LightEntity  shadowLight = world.getPrimaryShadowLight();

    Matrix4f lightSpaceMatrix =
            (shadowsEnabled && shadowLight != null && shadowLight.isCastShadows())
                    ? computeLightSpaceMatrix(shadowLight)
                    : null;

    if (lightSpaceMatrix != null) {
      renderShadowPass(world, lightSpaceMatrix);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, activeFboId);
      GL11.glViewport(0, 0, width, height);
    }

    if (lightSpaceMatrix != null) {
      renderLitScene(world, shadowLight, lightSpaceMatrix);
    } else {
      renderLegacyScene(world);
    }
  }

  // =============================
  // BEGIN FRAME
  // =============================

  /**
   * Computes and caches the view + projection matrices, pushes them into the
   * fixed-function GL stack (so legacy draw calls still work), and rebuilds
   * the frustum.
   */
  public void begin3D(CameraEntity cam) {
    float aspect = (float) width / height;
    currentProj = new Matrix4f().perspective(
            (float) Math.toRadians(fovDegrees), aspect, 0.1f, 1000f);

    Vec3 pos = cam.getPosition();
    currentView = new Matrix4f()
            .rotateX((float) Math.toRadians(cam.getPitch()))
            .rotateY((float) Math.toRadians(cam.getYaw()))
            .translate(-pos.x(), -pos.y(), -pos.z());

    lastVP  = new Matrix4f(currentProj).mul(currentView);
    frustum = new org.joml.FrustumIntersection(lastVP);

    // Keep the fixed-function stack in sync so legacy calls are correct.
    FloatBuffer pb = BufferUtils.createFloatBuffer(16);
    currentProj.get(pb);
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadMatrixf(pb);

    FloatBuffer vb = BufferUtils.createFloatBuffer(16);
    currentView.get(vb);
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadMatrixf(vb);
  }

  // =============================
  // SHADOW PASS
  // =============================

  private Matrix4f computeLightSpaceMatrix(LightEntity light) {
    Vec3 p = light.getPosition();
    Matrix4f lightView = new Matrix4f().lookAt(
            p.x(), p.y(), p.z(), 0f, 0f, 0f, 0f, 1f, 0f);
    float s = light.getShadowOrthoHalfSize();
    Matrix4f lightProj = new Matrix4f().ortho(
            -s, s, -s, s, light.getShadowNear(), light.getShadowFar());
    return new Matrix4f(lightProj).mul(lightView);
  }

  private void renderShadowPass(World world, Matrix4f lightSpace) {
    shadowFbo.bindForShadowPass();

    int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
      System.err.println("[SHADOW FBO] Incomplete! status=" + status);
    }

    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glCullFace(GL11.GL_FRONT);
    GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glPolygonOffset(4f, 16f);

    depthShader.use();

    org.joml.FrustumIntersection lightFrustum = new org.joml.FrustumIntersection(lightSpace);

    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      float radius = boundingRadius(e);
      if (frustumCullingEnabled
              && !lightFrustum.testSphere(e.getPosition().x(), e.getPosition().y(), e.getPosition().z(), radius)) {
        continue;
      }

      if (e instanceof Renderable3D renderable) {
        renderable.renderShadow(this, depthShader, lightSpace);
      }
    }

    depthShader.unbind();
    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glDisable(GL11.GL_CULL_FACE);
  }

  // =============================
  // LIT (MODERN) SCENE
  // =============================

  private void renderLitScene(World world, LightEntity light, Matrix4f lightSpace) {
    GL11.glDisable(GL11.GL_LIGHTING);
    litShader.use();

    Vec3  toLight = light.getDirectionToLightWorld().mul(-1f);
    Vec3  lc      = light.getColor();
    float inten   = light.getIntensity();

    litShader.setUniform3f("uLightDirWorld", toLight.x(), toLight.y(), toLight.z());
    litShader.setUniform3f("uLightColor",    lc.x() * inten, lc.y() * inten, lc.z() * inten);
    litShader.setUniform3f("uAmbient",       0.1f, 0.11f, 0.14f);
    litShader.setUniform1i("uDiffuseTex",    0);
    litShader.setUniform1i("uShadowMap",     1);

    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowFbo.getDepthTextureId());

    this.frustum = new org.joml.FrustumIntersection(new Matrix4f(currentProj).mul(currentView));

    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      float radius = boundingRadius(e);
      if (frustumCullingEnabled
              && !frustum.testSphere(e.getPosition().x(), e.getPosition().y(), e.getPosition().z(), radius)) {
        continue;
      }

      if (e instanceof Renderable3D renderable) {
        renderable.renderLit(this, litShader, currentView, currentProj, lightSpace);
      }
    }

    litShader.unbind();
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glEnable(GL11.GL_LIGHTING);
  }

  // =============================
  // LEGACY SCENE (delegates to LegacyRenderer)
  // =============================

  private void renderLegacyScene(World world) {
    LegacyRenderer.ModelRenderer   modelRenderer   = new LegacyRenderer.ModelRenderer(this::loadVboModel, this::loadObjModel);
    LegacyRenderer.TextureRenderer textureRenderer = new LegacyRenderer.TextureRenderer(this::loadTextureId);
    legacy.renderScene(world, modelRenderer, textureRenderer);
  }

  // =============================
  // MATRIX HELPERS (used by Renderable3D implementations)
  // =============================

  public Matrix4f modelMatrixForPlane(BasicPlaneEntity plane) {
    return new Matrix4f()
            .translate(0f, plane.getTop(), 0f)
            .scale(plane.getWidth(), 1f, plane.getLength());
  }

  public Matrix4f modelMatrixForModel(ModelEntity me, VboModel vbo) {
    Vec3 p   = me.getPosition();
    Vec3 rot = me.getRotation();
    float s  = me.getSize();
    return new Matrix4f()
            .translate(p.x(), p.y(), p.z())
            .rotateZ((float) Math.toRadians(rot.z()))
            .rotateY((float) Math.toRadians(rot.y()))
            .rotateX((float) Math.toRadians(rot.x()))
            .scale(s);
  }

  // =============================
  // PRIVATE UTILITY
  // =============================

  /** Returns an approximate bounding sphere radius for frustum culling. */
  private float boundingRadius(BasicEntity e) {
    if (e instanceof ModelEntity me)          return me.getModelBoundingRadius() * me.getSize();
    if (e instanceof BasicPlaneEntity plane)  return Math.max(plane.getWidth(), plane.getLength());
    if (e instanceof TextureEntity te)        return Math.max(te.getWidth(), te.getHeight());
    return e.getCollisionRadius();
  }
}