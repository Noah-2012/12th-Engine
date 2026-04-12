package com.mygame;

import net.twelfthengine.api.AppConfig;
import net.twelfthengine.api.EngineBootstrap;
import net.twelfthengine.api.TwelfthApp;
import net.twelfthengine.controls.InputManager;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.qgui.*; // Import Noah's new GUI System
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.PostProcessEffect;
import net.twelfthengine.renderer.postprocess.PostProcessPipeline;
import net.twelfthengine.renderer.postprocess.effects.*;
import net.twelfthengine.scripting.LuaSystem;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class MyGame extends TwelfthApp {

  // --- Entities ---
  private PlayerCameraEntity camera;
  private LightEntity mainLight;
  private ModelEntity bombEntity;
  private TextureEntity treeSprite;

  // --- Effects ---
  private PostProcessPipeline postProcess;

  // --- QGUI System ---
  private QGUIManager qgui;
  private boolean isGuiVisible = false;
  private boolean lastCState = false;
  private Window windowInstance; // Store reference to window for locking/unlocking

  // --- Lua System ---
  private LuaSystem lua;

  public static void main(String[] args) throws Exception {
    EngineBootstrap.run(new MyGame(), AppConfig.defaults());
  }

  public void onInit(World world, AppConfig config) {
    camera = new PlayerCameraEntity(0f, 5f, 0f);
    camera.setPosition(new Vec3(0, 5, 0));
    camera.setRotation(0, 0, 0);
    world.addEntity(camera);
    world.setActiveCamera(camera);

    /*
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
      byte[] twaBytes = ResourceExtractor.readBytes("/models/6ovcmof8fc56.twa");
      TwelfthPackage assetArchive = new TwelfthPackage(twaBytes, "6ovcmof8fc56.twa");
      byte[] twmBytes = assetArchive.getFileData("6ovcmof8fc56.twm");
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

     */

    // 4. Initialize QGUI
    qgui = new QGUIManager();
    QGUIMenuBar topBar = new QGUIMenuBar();
    topBar.addMenu("Files").addItem("New", () -> {});
    topBar.addMenu("Edit");
    topBar.addMenu("Look");

    topBar
        .addMenu("Post Processing")
        .addItem(
            "Active Effects",
            () -> {
              QGUIWindow effectWindow = new QGUIWindow("Post Process Stack", 100, 100, 300, 400);

              if (postProcess == null) return;

              // We fetch the list of effects currently in the pipeline
              // Assuming postProcess.getEffects() returns List<PostProcessEffect>
              int yPos = 40;
              for (PostProcessEffect effect : postProcess.getEffects()) {
                // Stelle sicher, dass es eine BasePostProcessEffect ist
                if (!(effect instanceof BasePostProcessEffect e)) continue;

                String effectName = e.getClass().getSimpleName().replace("Effect", "");

                QGUIButton toggleBtn = new QGUIButton(20, yPos, 260, 25, "", null);

                toggleBtn.setCallback(
                    () -> {
                      boolean newState = !e.isEnabled();
                      e.setEnabled(newState);
                      toggleBtn.setText(
                          effectName + ": " + (newState ? "[ ACTIVE ]" : "[ DISABLED ]"));
                    });

                toggleBtn.setText(
                    effectName + ": " + (e.isEnabled() ? "[ ACTIVE ]" : "[ DISABLED ]"));
                effectWindow.addElement(toggleBtn);
                yPos += 35;
              }

              qgui.addWindow(effectWindow);
            });

    qgui.setMenuBar(topBar);

    QGUIWindow demoWindow = new QGUIWindow("12th Engine Dashboard", 50, 50, 350, 250);
    demoWindow.addElement(
        new QGUIButton(
            20,
            30,
            100,
            20,
            "Log Bomb Pos",
            () -> {
              if (bombEntity != null) System.out.println("Bomb Y: " + bombEntity.getPosition().y());
            }));
    qgui.addWindow(demoWindow);

    lua = new LuaSystem(world);

    lua.runScript("scripts/init.lua");

    InputManager.allowBypassKey(GLFW.GLFW_KEY_C);
  }

  @Override
  public RenderPipeline onSetupRenderer(
      World world,
      AppConfig config,
      Window window,
      Renderer2D renderer2D,
      Renderer3D renderer3D,
      TextRenderer textRenderer)
      throws Exception {

    this.windowInstance = window; // Capture window reference
    renderer3D.setFrustumCullingEnabled(false);
    RenderPipeline pipeline = new RenderPipeline();

    postProcess = new PostProcessPipeline(config.width(), config.height());
    renderer3D.setActiveFbo(postProcess.getFboAId());

    MotionBlurEffect motionBlur = new MotionBlurEffect().strength(3f).samples(32).debug(0);

    postProcess.addEffect(motionBlur);
    postProcess.addEffect(new ChromaticAberrationEffect().strength(0.008f).falloff(3f));

    pipeline.setPreFrameHook(
        () -> {
          boolean currentCState = InputManager.isKeyPressed(GLFW.GLFW_KEY_C);
          if (currentCState && !lastCState) {
            isGuiVisible = !isGuiVisible;

            InputManager.setInputBlocked(isGuiVisible);

            if (windowInstance != null) {
              if (isGuiVisible) windowInstance.unlockMouse();
              else windowInstance.lockMouse();
            }
          }
          lastCState = currentCState;

          if (isGuiVisible && windowInstance != null) {
            qgui.update(
                (int) InputManager.getMouseX(),
                (int) InputManager.getMouseY(),
                InputManager.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT),
                windowInstance.getWidth());
          }

          postProcess.bind();
          GL11.glClearColor(0f, 0f, 0f, 1f);
          GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
          GL11.glEnable(GL11.GL_DEPTH_TEST);
        });

    pipeline.addStep(
        RenderLayer.OPAQUE_3D,
        ctx -> {
          ctx.renderer3D().render(ctx.world());
          motionBlur.updateMatrices(ctx.renderer3D().getLastVP());
        });

    pipeline.addStep(
        RenderLayer.DEBUG_3D,
        ctx -> {
          ctx.legacy().setColor(1, 0, 0, 1);
          ctx.legacy().drawWireCube(new Vec3(-1, -1, -1), 3f);
          ctx.legacy().setColor(0, 0, 1, 1);
          ctx.legacy().drawFilledBox(new Vec3(5, 5, 5), new Vec3(3, 3, 3));
        });

    pipeline.addStep(RenderLayer.UI_2D, ctx -> {});

    pipeline.addStep(RenderLayer.POST_BLIT, ctx -> postProcess.present());

    pipeline.addStep(
        RenderLayer.UI_2D_OVERLAY,
        ctx -> {
          // CONTEXT MENU IN HERE IS UNSTABLE BECAUSE OF THE OTHER THINGS THAT ARE ALREADY IN HERE
        });

    pipeline.addStep(
        RenderLayer.UI_2D_FOREGROUND,
        ctx -> {
          renderer2D.begin2D();
          if (isGuiVisible) {
            qgui.render(renderer2D, textRenderer);
          }
          renderer2D.end2D();
        });

    return pipeline;
  }

  @Override
  public void onTick(double deltaTime) {
    if (lua != null) {
      lua.call("onTick", deltaTime);
    }

    // Original Log
    if (mainLight != null) {
      Vec3 toLight = mainLight.getDirectionToLightWorld();
      System.out.println("LightDir: " + toLight.x() + " " + toLight.y() + " " + toLight.z());
    }
  }

  @Override
  public void onDispose() {
    if (postProcess != null) postProcess.dispose();
  }
}
