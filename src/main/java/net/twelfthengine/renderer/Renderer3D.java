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
 * Renderer3D — the top-level 3D rendering orchestrator for 12th Engine.
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
  // FIX: Reuse Matrix4f instances every frame instead of allocating new ones.
  //      Use .identity() + in-place methods to avoid GC pressure.
  // =============================

  private final Matrix4f currentView = new Matrix4f();
  private final Matrix4f currentProj = new Matrix4f();
  private final Matrix4f lastVP = new Matrix4f();
  // Scratch matrices for intermediate calculations — never returned to callers.
  private final Matrix4f scratchMat = new Matrix4f();
  private final Matrix4f lightView = new Matrix4f();
  private final Matrix4f lightProj = new Matrix4f();
  private final Matrix4f lightSpace = new Matrix4f();

  public Matrix4f getLastVP() {
    return lastVP;
  }

  // =============================
  // CACHED FLOAT BUFFERS
  // FIX: Allocate once at construction; rewind before each use instead of
  //      creating a new FloatBuffer every frame.
  // =============================

  private final FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
  private final FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);

  // =============================
  // ACTIVE FBO
  // =============================

  private int activeFboId = 0;

  public void setActiveFbo(int fboId) {
    this.activeFboId = fboId;
  }

  // =============================
  // FRUSTUM CULLING
  // FIX: Reuse a single FrustumIntersection instance and call set() on it
  //      rather than allocating a new object every frame.
  // =============================

  private final FrustumIntersection frustum = new FrustumIntersection();
  private boolean frustumCullingEnabled = true;

  public FrustumIntersection getFrustum() {
    return frustum;
  }

  public boolean isFrustumCullingEnabled() {
    return frustumCullingEnabled;
  }

  public void setFrustumCullingEnabled(boolean frustumCullingEnabled) {
    this.frustumCullingEnabled = frustumCullingEnabled;
  }

  // =============================
  // ASSET CACHES
  // =============================

  private final Map<String, ObjModel> modelCache = new HashMap<>();
  private final Map<String, VboModel> vboCache = new HashMap<>();
  private final Map<String, Integer> textureIdCache = new HashMap<>();

  // =============================
  // LEGACY SCENE HELPERS
  // FIX: Construct ModelRenderer / TextureRenderer once; they capture method
  //      references that are stable across frames, so there is no reason to
  //      re-allocate them inside renderLegacyScene() every frame.
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

  /** Exposes the legacy subsystem so game code can call drawLine, drawAxes, etc. */
  public LegacyRenderer getLegacy() {
    return legacy;
  }

  public void setFovDegrees(float fovDegrees) {
    this.fovDegrees = Math.max(30f, Math.min(150f, fovDegrees));
  }

  // =============================
  // CONSTRUCTION
  // =============================

  public Renderer3D(int width, int height) {
    this.width = width;
    this.height = height;

    GL11.glEnable(GL11.GL_DEPTH_TEST);

    // Boot the legacy subsystem and let it own its own GL state.
    legacy = new LegacyRenderer();
    legacy.initLegacyLighting();

    // FIX: Build these once; they only capture stable method references.
    legacyModelRenderer = new LegacyRenderer.ModelRenderer(this::loadVboModel, this::loadObjModel);
    legacyTextureRenderer = new LegacyRenderer.TextureRenderer(this::loadTextureId);

    try {
      depthShader = new ShaderProgram("/shaders/shadow_depth.vert", "/shaders/shadow_depth.frag");
      litShader = new ShaderProgram("/shaders/lit_shadow.vert", "/shaders/lit_shadow.frag");
      shadowFbo = new ShadowFramebuffer();
      unitCubeMesh = new UnitCubeMesh();
      planeMesh = new PlaneMesh();
      textureMesh = new TextureMesh();
      shadowsEnabled = true;
    } catch (Exception e) {
      System.err.println("[Renderer3D] Shader shadows disabled: " + e.getMessage());
      shadowsEnabled = false;
    }
  }

  // =============================
  // ASSET LOADING
  // FIX: Use computeIfAbsent — one map lookup instead of containsKey + get (two lookups).
  // =============================

  public ObjModel loadObjModel(String path) {
    return modelCache.computeIfAbsent(
        path,
        k -> {
          try {
            return ObjLoader.load(k);
          } catch (IOException e) {
            System.err.println("Failed to load model: " + k);
            return null;
          }
        });
  }

  public VboModel loadVboModel(String path) {
    return vboCache.computeIfAbsent(
        path,
        k -> {
          ObjModel obj = loadObjModel(k);
          return obj != null ? new VboModel(obj) : null;
        });
  }

  public ObjModel loadObjModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    return modelCache.computeIfAbsent(
        key,
        k -> {
          try {
            return ObjLoader.loadFromPackage(pack, internalPath);
          } catch (IOException e) {
            System.err.println("Failed to load model from package: " + internalPath);
            return null;
          }
        });
  }

  public VboModel loadVboModelFromPackage(TwelfthPackage pack, String internalPath) {
    String key = pack.getArchiveName() + ":" + internalPath;
    return vboCache.computeIfAbsent(
        key,
        k -> {
          ObjModel obj = loadObjModelFromPackage(pack, internalPath);
          return obj != null ? new VboModel(obj) : null;
        });
  }

  public int loadTextureId(String path) {
    return textureIdCache.computeIfAbsent(path, TextureLoader::loadTexture);
  }

  // =============================
  // FRAME ENTRY POINT
  // =============================

  public void render(World world) {
    CameraEntity cam = world.getActiveCamera();
    LightEntity shadowLight = world.getPrimaryShadowLight();

    // Compute light-space matrix into the cached field (no allocation).
    boolean doShadows = shadowsEnabled && shadowLight != null && shadowLight.isCastShadows();
    if (doShadows) {
      computeLightSpaceMatrix(shadowLight); // writes into this.lightSpace
      renderShadowPass(world);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, activeFboId);
      GL11.glViewport(0, 0, width, height);
      renderLitScene(world, shadowLight);
    } else {
      renderLegacyScene(world);
    }
  }

  // =============================
  // BEGIN FRAME
  // FIX: No new Matrix4f / FloatBuffer allocations. All operations are in-place.
  //      FrustumIntersection is updated via set() on the cached instance.
  // =============================

  /**
   * Computes and caches the view + projection matrices, pushes them into the fixed-function GL
   * stack (so legacy draw calls still work), and rebuilds the frustum.
   */
  public void begin3D(CameraEntity cam) {
    float aspect = (float) width / height;

    // In-place: identity() clears the matrix, then perspective() fills it.
    currentProj.identity().perspective((float) Math.toRadians(fovDegrees), aspect, 0.1f, 1000f);

    Vec3 pos = cam.getPosition();
    currentView
        .identity()
        .rotateX((float) Math.toRadians(cam.getPitch()))
        .rotateY((float) Math.toRadians(cam.getYaw()))
        .translate(-pos.x(), -pos.y(), -pos.z());

    // lastVP = proj * view (in-place, reuses lastVP storage)
    currentProj.mul(currentView, lastVP);

    // FIX: set() instead of new FrustumIntersection(matrix) — no allocation.
    frustum.set(lastVP);

    // Push to the fixed-function stack.  Rewind the cached buffers before use.
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
  // FIX: computeLightSpaceMatrix writes into cached fields (no allocation).
  //      glCheckFramebufferStatus removed from the hot path — validate the FBO
  //      once inside ShadowFramebuffer's constructor instead.
  //      Light frustum also reuses a cached FrustumIntersection via set().
  // =============================

  /** Writes the light-space matrix into {@link #lightSpace}. No objects allocated. */
  private void computeLightSpaceMatrix(LightEntity light) {
    Vec3 p = light.getPosition();
    lightView.identity().lookAt(p.x(), p.y(), p.z(), 0f, 0f, 0f, 0f, 1f, 0f);
    float s = light.getShadowOrthoHalfSize();
    lightProj.identity().ortho(-s, s, -s, s, light.getShadowNear(), light.getShadowFar());
    lightProj.mul(lightView, lightSpace); // lightSpace = lightProj * lightView
  }

  private final FrustumIntersection lightFrustum = new FrustumIntersection();

  private void renderShadowPass(World world) {
    shadowFbo.bindForShadowPass();

    GL11.glEnable(GL11.GL_CULL_FACE);
    GL11.glCullFace(GL11.GL_FRONT);
    GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glPolygonOffset(4f, 16f);

    depthShader.use();

    // FIX: Reuse lightFrustum via set() instead of allocating each frame.
    lightFrustum.set(lightSpace);

    for (BasicEntity e : world.getEntities()) {
      if (e instanceof LightEntity || e instanceof CameraEntity) continue;

      float radius = boundingRadius(e);
      if (frustumCullingEnabled
          && !lightFrustum.testSphere(
              e.getPosition().x(), e.getPosition().y(), e.getPosition().z(), radius)) {
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
  // FIX: Frustum is already set in begin3D; no second FrustumIntersection
  //      allocation needed here.  scratchMat used for any temporaries.
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

    // FIX: frustum was already populated with the camera VP in begin3D().
    //      No new object or matrix multiplication needed here.

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
  // FIX: Renderers are cached fields — no per-frame allocation.
  // =============================

  private void renderLegacyScene(World world) {
    legacy.renderScene(world, legacyModelRenderer, legacyTextureRenderer);
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
    Vec3 p = me.getPosition();
    Vec3 rot = me.getRotation();
    float s = me.getSize();
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
    if (e instanceof ModelEntity me) return me.getModelBoundingRadius() * me.getSize();
    if (e instanceof BasicPlaneEntity plane) return Math.max(plane.getWidth(), plane.getLength());
    if (e instanceof TextureEntity te) return Math.max(te.getWidth(), te.getHeight());
    return e.getCollisionRadius() * 1.25f;
  }
}
