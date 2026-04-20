package net.twelfthengine.renderer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import net.twelfthengine.core.console.Console;
import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.core.resources.TwelfthPackage;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
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
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Renderer3D - the top-level 3D rendering orchestrator for 12th Engine.
 *
 * <p>Responsibilities of THIS class:
 *
 * <ul>
 *   <li>Asset caching (OBJ, VBO, textures)
 *   <li>Frame setup: shadow pass → lit pass (or legacy fallback)
 *   <li>Frustum culling
 *   <li>begin3D() — writes cached view/proj to both JOML fields and the GL matrix stack
 * </ul>
 *
 * <p>Responsibilities that live in their own subsystem:
 *
 * <ul>
 *   <li>{@link LegacyRenderer} — everything that touches glBegin/glEnd, GL_LIGHTING, fixed-function
 *       matrices, drawFilledBox, drawWireCube, etc.
 * </ul>
 */

public class Renderer3D {

  private static final String TAG = "Renderer3D";

  // =============================
  // CONFIGURATION
  // =============================

  private static int width;
  private static int height;
  private static float fovDegrees = 90f;

  // =============================
  // SUBSYSTEMS
  // =============================

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

  private static boolean shadowsEnabled;
  private ShaderProgram depthShader;
  private ShaderProgram litShader;
  private ShadowFramebuffer shadowFbo;

  // =============================
  // CACHED MATRICES
  // =============================

  private final Matrix4f currentView = new Matrix4f();
  private final Matrix4f currentProj = new Matrix4f();
  private final Matrix4f lastVP = new Matrix4f();
  private final Matrix4f scratchMat = new Matrix4f();
  private final Matrix4f lightView = new Matrix4f();
  private final Matrix4f lightProj = new Matrix4f();
  private final Matrix4f lightSpace = new Matrix4f();

  // FIX: Per-entity scratch matrices reused across draw calls instead of
  //      allocating a new Matrix4f every time modelMatrixForModel /
  //      modelMatrixForPlane is called (previously one allocation per entity
  //      per pass). The MVP scratch is used by ModelEntity.renderShadow for
  //      the light * model product — exposed via getLightMvpScratch() so the
  //      caller can chain .set(lightSpace).mul(model) without `new`.
  //
  //      Safe to share because the pipeline draws one entity at a time and
  //      all matrix uploads are synchronous (the ShaderProgram overload we
  //      now use copies the matrix into its internal FloatBuffer before
  //      returning).
  private final Matrix4f modelMatrixScratch = new Matrix4f();
  private final Matrix4f lightMvpScratch = new Matrix4f();

  public Matrix4f getLightMvpScratch() {
    return lightMvpScratch;
  }

  public Matrix4f getLastVP() {
    return lastVP;
  }

  // =============================
  // CACHED FLOAT BUFFERS
  // =============================

  private final FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
  private final FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);

  // =============================
  // ACTIVE FBO
  // =============================

  private static int activeFboId = 0;

  public void setActiveFbo(int fboId) {
    Logger.debug(TAG, "Active FBO changed: " + activeFboId + " → " + fboId);
    this.activeFboId = fboId;
  }

  // =============================
  // FRUSTUM CULLING
  // =============================

  private final FrustumIntersection frustum = new FrustumIntersection();
  private static boolean frustumCullingEnabled = true;

  public FrustumIntersection getFrustum() {
    return frustum;
  }

  public boolean isFrustumCullingEnabled() {
    return frustumCullingEnabled;
  }

  public void setFrustumCullingEnabled(boolean frustumCullingEnabled) {
    this.frustumCullingEnabled = frustumCullingEnabled;
    Logger.info(TAG, "Frustum culling " + (frustumCullingEnabled ? "enabled" : "disabled") + ".");
  }

  // =============================
  // ASSET CACHES
  // =============================

  private final Map<String, ObjModel> modelCache = new HashMap<>();
  private final Map<String, VboModel> vboCache = new HashMap<>();
  private final Map<String, Integer> textureIdCache = new HashMap<>();

  // =============================
  // LEGACY SCENE HELPERS
  // =============================

  private final LegacyRenderer.ModelRenderer legacyModelRenderer;
  private final LegacyRenderer.TextureRenderer legacyTextureRenderer;

  // =============================
  // ACCESSORS
  // =============================

  public MatrixStack3D getMatrices() {
    return modelStack;
  }

  public UnitCubeMesh getUnitCubeMesh() {
    return unitCubeMesh;
  }

  public PlaneMesh getPlaneMesh() {
    return planeMesh;
  }

  public TextureMesh getTextureMesh() {
    return textureMesh;
  }

  public Map<String, Integer> getTextureIdCache() {
    return textureIdCache;
  }

  public LegacyRenderer getLegacy() {
    return legacy;
  }

  public void setFovDegrees(float fovDegrees) {
    float clamped = Math.max(30f, Math.min(150f, fovDegrees));
    Logger.info(TAG, "FOV set to " + clamped + "° (requested: " + fovDegrees + "°)");
    this.fovDegrees = clamped;
  }

  // =============================
  // CONSTRUCTION
  // =============================

  static {
    Console.bindFloat("cv_renderer3d_fov_degrees", () -> fovDegrees, v -> fovDegrees = v);
    Console.bindInt("cv_renderer3d_width", () -> width, v -> width = v);
    Console.bindInt("cv_renderer3d_height", () -> height, v -> height = v);
    Console.bindBool("cv_renderer3d_shadows", () -> shadowsEnabled, v -> shadowsEnabled = v);
    Console.bindInt("cv_renderer3d_active_fbo", () -> activeFboId, v -> activeFboId = v);
    Console.bindBool(
        "cv_renderer3d_frustum_culling",
        () -> frustumCullingEnabled,
        v -> frustumCullingEnabled = v);
  }

  public Renderer3D(int width, int height) {
    this.width = width;
    this.height = height;
    Logger.info(TAG, "Initializing Renderer3D at " + width + "x" + height + "...");

    GL11.glEnable(GL11.GL_DEPTH_TEST);
    Logger.debug(TAG, "GL_DEPTH_TEST enabled.");

    legacy = new LegacyRenderer();
    legacy.initLegacyLighting();
    Logger.debug(TAG, "LegacyRenderer initialized with fixed-function lighting.");

    legacyModelRenderer = new LegacyRenderer.ModelRenderer(this::loadVboModel, this::loadObjModel);
    legacyTextureRenderer = new LegacyRenderer.TextureRenderer(this::loadTextureId);
    Logger.debug(TAG, "Legacy model/texture renderers constructed.");

    try {
      Logger.info(TAG, "Compiling shadow shaders...");
      depthShader = new ShaderProgram("/shaders/shadow_depth.vert", "/shaders/shadow_depth.frag");
      litShader = new ShaderProgram("/shaders/lit_shadow.vert", "/shaders/lit_shadow.frag");
      Logger.info(TAG, "Shadow shaders compiled successfully.");

      shadowFbo = new ShadowFramebuffer();
      unitCubeMesh = new UnitCubeMesh();
      planeMesh = new PlaneMesh();
      textureMesh = new TextureMesh();
      shadowsEnabled = true;
      Logger.info(
          TAG, "Shadow framebuffer and shared meshes initialized. Shadow pipeline: ACTIVE.");
    } catch (Exception e) {
      Logger.warn(
          TAG,
          "Shadow pipeline disabled — falling back to legacy renderer. Reason: " + e.getMessage());
      shadowsEnabled = false;
    }

    Logger.info(
        TAG,
        "Renderer3D ready. Pipeline: "
            + (shadowsEnabled ? "lit+shadow (modern)" : "legacy fixed-function"));
  }

  // =============================
  // ASSET LOADING
  // =============================

  public ObjModel loadObjModel(String path) {
    return modelCache.computeIfAbsent(
        path,
        k -> {
          Logger.info(TAG, "Loading OBJ model: " + k);
          try {
            ObjModel model = ObjLoader.load(k);
            Logger.debug(TAG, "OBJ loaded: " + k + " [cached]");
            return model;
          } catch (IOException e) {
            Logger.error(TAG, "Failed to load OBJ model '" + k + "': " + e.getMessage());
            return null;
          }
        });
  }

  public VboModel loadVboModel(String path) {
    return vboCache.computeIfAbsent(
        path,
        k -> {
          Logger.debug(TAG, "Building VBO for: " + k);
          ObjModel obj = loadObjModel(k);
          if (obj == null) {
            Logger.warn(TAG, "Cannot build VBO for '" + k + "' — OBJ model failed to load.");
            return null;
          }
          VboModel vbo = new VboModel(obj);
          Logger.debug(TAG, "VBO built and cached for: " + k);
          return vbo;
        });
  }

  public ObjModel loadObjModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    return modelCache.computeIfAbsent(
        key,
        k -> {
          Logger.info(
              TAG, "Loading OBJ from package '" + pack.getArchiveName() + "': " + internalPath);
          try {
            ObjModel model = ObjLoader.loadFromPackage(pack, internalPath);
            Logger.debug(TAG, "Package OBJ loaded and cached: " + key);
            return model;
          } catch (IOException e) {
            Logger.error(
                TAG,
                "Failed to load OBJ from package '"
                    + pack.getArchiveName()
                    + "' path '"
                    + internalPath
                    + "': "
                    + e.getMessage());
            return null;
          }
        });
  }

  public VboModel loadVboModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    return vboCache.computeIfAbsent(
        key,
        k -> {
          Logger.debug(
              TAG, "Building VBO from package '" + pack.getArchiveName() + "': " + internalPath);
          ObjModel obj = loadObjModelFromPackage(pack, internalPath);
          if (obj == null) {
            Logger.warn(TAG, "Cannot build VBO — OBJ failed to load from package: " + key);
            return null;
          }
          VboModel vbo = new VboModel(obj);
          Logger.debug(TAG, "Package VBO built and cached: " + key);
          return vbo;
        });
  }

  public int loadTextureId(String path) {
    return textureIdCache.computeIfAbsent(
        path,
        k -> {
          Logger.info(TAG, "Loading texture: " + k);
          int id = TextureLoader.loadTexture(k);
          Logger.debug(TAG, "Texture loaded — id=" + id + " path=" + k);
          return id;
        });
  }

  // =============================
  // FRAME ENTRY POINT
  // =============================

  public void render(World world) {
    CameraEntity cam = world.getActiveCamera();
    LightEntity shadowLight = world.getPrimaryShadowLight();

    if (cam == null) {
      Logger.warn(TAG, "render() called but no active camera is set — skipping frame.");
      return;
    }

    boolean doShadows = shadowsEnabled && shadowLight != null && shadowLight.isCastShadows();

    if (doShadows) {
      computeLightSpaceMatrix(shadowLight);
      renderShadowPass(world);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, activeFboId);
      GL11.glViewport(0, 0, width, height);
      renderLitScene(world, shadowLight);
    } else {
      if (shadowsEnabled && shadowLight == null) {
        Logger.debug(TAG, "No primary shadow light in world — using legacy scene.");
      }
      renderLegacyScene(world);
    }
  }

  // =============================
  // BEGIN FRAME
  // =============================

  public void begin3D(CameraEntity cam) {
    float aspect = (float) width / height;

    currentProj.identity().perspective((float) Math.toRadians(fovDegrees), aspect, 0.1f, 1000f);

    Vec3 pos = cam.getPosition();
    currentView
        .identity()
        .rotateX((float) Math.toRadians(cam.getPitch()))
        .rotateY((float) Math.toRadians(cam.getYaw()))
        .translate(-pos.x(), -pos.y(), -pos.z());

    currentProj.mul(currentView, lastVP);
    frustum.set(lastVP);

    projBuffer.clear();
    currentProj.get(projBuffer);
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadMatrixf(projBuffer);

    viewBuffer.clear();
    currentView.get(viewBuffer);
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadMatrixf(viewBuffer);
  }

  // =============================
  // SHADOW PASS
  // =============================

  private void computeLightSpaceMatrix(LightEntity light) {
    Vec3 p = light.getPosition();
    lightView.identity().lookAt(p.x(), p.y(), p.z(), 0f, 0f, 0f, 0f, 1f, 0f);
    float s = light.getShadowOrthoHalfSize();
    lightProj.identity().ortho(-s, s, -s, s, light.getShadowNear(), light.getShadowFar());
    lightProj.mul(lightView, lightSpace);
  }

  private final FrustumIntersection lightFrustum = new FrustumIntersection();

  private void renderShadowPass(World world) {
    shadowFbo.bindForShadowPass();

    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glCullFace(GL11.GL_FRONT);
    GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glPolygonOffset(4f, 16f);

    depthShader.use();
    lightFrustum.set(lightSpace);

    int culled = 0, rendered = 0;
    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      float radius = boundingRadius(e);
      Vec3 pos = e.getPosition();
      if (frustumCullingEnabled && !lightFrustum.testSphere(pos.x(), pos.y(), pos.z(), radius)) {
        culled++;
        continue;
      }

      if (e instanceof Renderable3D renderable) {
        renderable.renderShadow(this, depthShader, lightSpace);
        rendered++;
      }
    }

    Logger.debug(
        TAG, "Shadow pass: " + rendered + " rendered, " + culled + " culled by light frustum.");

    depthShader.unbind();
    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glDisable(GL11.GL_CULL_FACE);
  }

  // =============================
  // LIT (MODERN) SCENE
  // =============================

  private void renderLitScene(World world, LightEntity light) {
    GL11.glDisable(GL11.GL_LIGHTING);
    litShader.use();

    Vec3 toLight = light.getDirectionToLightWorld().mul(-1f);
    Vec3 lc = light.getColor();
    float inten = light.getIntensity();

    litShader.setUniform3f("uLightDirWorld", toLight.x(), toLight.y(), toLight.z());
    litShader.setUniform3f("uLightColor", lc.x() * inten, lc.y() * inten, lc.z() * inten);
    litShader.setUniform3f("uAmbient", 0.1f, 0.11f, 0.14f);
    litShader.setUniform1i("uDiffuseTex", 0);
    litShader.setUniform1i("uShadowMap", 1);

    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowFbo.getDepthTextureId());

    int culled = 0, rendered = 0;
    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      float radius = boundingRadius(e);
      float cx = e.getPosition().x();
      float cy = e.getPosition().y();
      float cz = e.getPosition().z();

      if (e instanceof ModelEntity me) {
        Vec3 centerOffset = me.getModelCenter();
        cx += centerOffset.x();
        cy += centerOffset.y();
        cz += centerOffset.z();
      }

      if (frustumCullingEnabled && !frustum.testSphere(cx, cy, cz, radius)) {
        culled++;
        continue;
      }

      if (e instanceof Renderable3D renderable) {
        renderable.renderLit(this, litShader, currentView, currentProj, lightSpace);
        rendered++;
      }
    }

    Logger.debug(
        TAG, "Lit pass: " + rendered + " rendered, " + culled + " culled by camera frustum.");

    litShader.unbind();
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glEnable(GL11.GL_LIGHTING);
  }

  // =============================
  // LEGACY SCENE
  // =============================

  private void renderLegacyScene(World world) {
    legacy.renderScene(world, legacyModelRenderer, legacyTextureRenderer);
  }

  // =============================
  // MATRIX HELPERS
  // =============================

  public Matrix4f modelMatrixForPlane(BasicPlaneEntity plane) {
    // FIX: reuse scratch instead of `new Matrix4f()` per call.
    return modelMatrixScratch
        .identity()
        .translate(0f, plane.getTop(), 0f)
        .scale(plane.getWidth(), 1f, plane.getLength());
  }

  public Matrix4f modelMatrixForModel(ModelEntity me, VboModel vbo) {
    Vec3 p = me.getPosition();
    Vec3 rot = me.getRotation();
    float s = me.getSize();
    // FIX: reuse scratch instead of `new Matrix4f()` per call. Called twice
    //      per entity per frame (shadow + lit) — the old code allocated a new
    //      Matrix4f each time.
    return modelMatrixScratch
        .identity()
        .translate(p.x(), p.y(), p.z())
        .rotateZ((float) Math.toRadians(rot.z()))
        .rotateY((float) Math.toRadians(rot.y()))
        .rotateX((float) Math.toRadians(rot.x()))
        .scale(s);
  }

  // =============================
  // PRIVATE UTILITY
  // =============================

  private float boundingRadius(BasicEntity e) {
    if (e instanceof ModelEntity me) return me.getModelBoundingRadius() * me.getSize() * 1.5f;
    if (e instanceof BasicPlaneEntity plane) return Math.max(plane.getWidth(), plane.getLength());
    if (e instanceof TextureEntity te) return Math.max(te.getWidth(), te.getHeight());
    return e.getCollisionRadius() * 1.25f;
  }

  // =============================
  // CACHE STATS (useful for debug commands)
  // =============================

  public void logCacheStats() {
    Logger.info(
        TAG,
        "Asset cache — OBJ models: "
            + modelCache.size()
            + ", VBO models: "
            + vboCache.size()
            + ", Textures: "
            + textureIdCache.size());
  }

  public void resize(int newW, int newH) {
    this.width = newW;
    this.height = newH;
  }
}
