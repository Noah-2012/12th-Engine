package com.mygame;

import net.twelfthengine.api.AppConfig;
import net.twelfthengine.api.EngineBootstrap;
import net.twelfthengine.api.TwelfthApp;
import net.twelfthengine.core.resources.ResourceExtractor;
import net.twelfthengine.core.resources.TwelfthPackage;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.renderer.postprocess.PostProcessPipeline;
import net.twelfthengine.renderer.postprocess.effects.FogEffect;
import net.twelfthengine.renderer.postprocess.effects.MotionBlurEffect;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;
import org.lwjgl.opengl.GL11;

public class MyGame extends TwelfthApp {

  // --- Entities ---
  private PlayerCameraEntity camera;
  private LightEntity        mainLight;
  private ModelEntity        bombEntity;
  private TextureEntity      treeSprite;

  private PostProcessPipeline postProcess;
  private MotionBlurEffect    motionBlur;

  // ------------------------------------------------------------------
  // Entry point
  // ------------------------------------------------------------------
  public static void main(String[] args) throws Exception {
    EngineBootstrap.run(new MyGame(), AppConfig.defaults());
  }

  // ------------------------------------------------------------------
  // onInit — spawn all entities, set active camera
  // ------------------------------------------------------------------
  @Override
  public void onInit(World world, AppConfig config) {

    // Camera
    camera = new PlayerCameraEntity(0f, 5f, 0f);
    camera.setPosition(new Vec3(0, 5, 0));
    camera.setRotation(0, 0, 0);
    world.addEntity(camera);
    world.setActiveCamera(camera);

    // Ground plane
    world.addEntity(new BasicPlaneEntity(0, -3, 0, 50, 50));

    // Sunlight
    mainLight = new LightEntity(28f, 42f, 18f);
    mainLight.setRotation(-48f, -32f, 0f);
    mainLight.setColor(1f, 0.96f, 0.88f);
    mainLight.setIntensity(1.2f);
    mainLight.setShadowOrthoHalfSize(60f);
    mainLight.setCastShadows(true);
    mainLight.setShadowNear(1f);
    mainLight.setShadowFar(200f);
    world.addEntity(mainLight);

    // Bomb model — loaded from encrypted .twa/.twm package
    try {
      byte[]        twaBytes     = ResourceExtractor.readBytes("/models/6ovcmof8fc56.twa");
      TwelfthPackage assetArchive = new TwelfthPackage(twaBytes, "6ovcmof8fc56.twa");
      byte[]        twmBytes     = assetArchive.getFileData("6ovcmof8fc56.twm");
      TwelfthPackage modelArchive = new TwelfthPackage(twmBytes, "6ovcmof8fc56.twm");

      bombEntity = new ModelEntity(-6, 600, 0, "6ovcmof8fc56.obj", modelArchive);
      bombEntity.setSize(1.0f);
      bombEntity.enableRigidbody();
      bombEntity.setMass(1.0f);
      bombEntity.setDrag(0.999f);
      bombEntity.setGravity(19.62f);
      bombEntity.setRotation(new Vec3(0, 45, 0));
      bombEntity.setCollisionShape(BasicEntity.CollisionShape.AABB);
      bombEntity.setPushable(true);
      bombEntity.setCollidable(true);
      world.addEntity(bombEntity);
    } catch (java.io.IOException e) {
      System.err.println("Failed to load packaged bomb model!");
      e.printStackTrace();
    }

    // Tree billboard sprite
    treeSprite = new TextureEntity(-10, 2, 0, "/models/tree/DB2X2_L01.png", 2.0f, 4.0f);
    world.addEntity(treeSprite);
  }

  // ------------------------------------------------------------------
  // onSetupRenderer — build and return the full render pipeline
  // ------------------------------------------------------------------
  @Override
  public RenderPipeline onSetupRenderer(
          World world,
          AppConfig config,
          Window window,
          Renderer2D renderer2D,
          Renderer3D renderer3D,
          TextRenderer textRenderer) throws Exception {

    renderer3D.setFrustumCullingEnabled(false);

    RenderPipeline pipeline = new RenderPipeline();

    // Post-processing pipeline
    postProcess = new PostProcessPipeline(config.width(), config.height());
    renderer3D.setActiveFbo(postProcess.getFboAId());

    // Motion blur (optional)
    motionBlur  = new MotionBlurEffect().strength(3f).samples(32).debug(0);
    postProcess.addEffect(motionBlur);

    // Pre-frame: bind FBO and clear buffers
    pipeline.setPreFrameHook(() -> {
      postProcess.bind();
      GL11.glClearColor(0f, 0f, 0f, 1f);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      GL11.glEnable(GL11.GL_DEPTH_TEST);
    });

    // 3D opaque geometry
    pipeline.addStep(RenderLayer.OPAQUE_3D, ctx -> {
      ctx.renderer3D().render(ctx.world());
      motionBlur.updateMatrices(ctx.renderer3D().getLastVP());
    });

    // Debug 3D
    pipeline.addStep(RenderLayer.DEBUG_3D, ctx -> {
      ctx.renderer3D().setColor(1, 0, 0, 1);
      ctx.renderer3D().drawWireCube(new Vec3(-1, -1, -1), 3f);
      ctx.renderer3D().setColor(0, 0, 1, 1);
      ctx.renderer3D().drawFilledBox(new Vec3(5, 5, 5), new Vec3(3, 3, 3));
    });

    // 2D UI & final blit to screen
    pipeline.addStep(RenderLayer.UI_2D, ctx -> {
    });

    pipeline.addStep(RenderLayer.POST_BLIT, ctx -> {
      postProcess.present();
    });

    return pipeline;
  }

  // ------------------------------------------------------------------
  // onTick — fixed-rate game logic
  // ------------------------------------------------------------------
  @Override
  public void onTick(double deltaTime) {
    // normales Game-Update
    if (mainLight != null) {
      Vec3 toLight = mainLight.getDirectionToLightWorld();
      System.out.println("LightDir: " + toLight.x() + " " + toLight.y() + " " + toLight.z());
    }
  }

  // ------------------------------------------------------------------
  // onDispose — cleanup
  // ------------------------------------------------------------------
  @Override
  public void onDispose() {
    if (postProcess != null) postProcess.dispose();
  }
}