package net.twelfthengine;

import java.util.Locale;
import net.twelfthengine.controls.InputManager;
import net.twelfthengine.coord.iab.IAB;
import net.twelfthengine.core.EngineObject;
import net.twelfthengine.core.debug.CullingDebugWindow;
import net.twelfthengine.core.discord.DiscordPresence;
import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.core.resources.TwelfthPackage;
import net.twelfthengine.core.tick.TickManager;
import net.twelfthengine.core.tick.TickPhase;
import net.twelfthengine.core.tick.TickProfiler;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.gui.PauseMenuScreen;
import net.twelfthengine.gui.VideoIntroScreen;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderContext;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.test.TickTest;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;
import org.lwjgl.glfw.GLFW;

public class TwelfthEngine {

  private static final double TICK_RATE = 20.0;
  private static final double TICK_TIME = 1.0 / TICK_RATE;
  private static int TICK_COUNTER = 0;
  private static volatile int CURRENT_TPS = 0;
  private static volatile int CURRENT_FPS = 0;
  private static volatile boolean SIMULATE_LAG = false;
  private static final int LAG_DELAY_MS = 200;

  private static final boolean ENABLE_CULLING_DEBUG = false;

  public static void main(String[] args) {
    EngineObject engine = EngineObject.getInstance();
    engine.start();
    DiscordPresence.init();
    Logger.info("Startup", "Starting EngineObject");

    TickManager tickManager = engine.getTickManager();
    TickTest.register();
    Logger.info("Startup", "Starting TickManager");

    Window window = new Window(0, 1920, 1080, "12th Engine");
    window.init();
    window.lockMouse();
    InputManager.init(window.getHandle());
    Logger.info("Startup", "Initialize InputManager and Window");

    Renderer2D renderer = new Renderer2D(window);
    Renderer3D r3d = new Renderer3D(1920, 1080);
    TextRenderer textRenderer = new TextRenderer();
    RenderPipeline renderPipeline = new RenderPipeline();
    r3d.setFovDegrees(120f);

    Window debugWindow = null;
    Renderer2D debugRenderer = null;
    Renderer3D debugR3d = null;

    if (ENABLE_CULLING_DEBUG) {
      debugWindow = new Window(1, 800, 800, "Top-Down View");
      debugWindow.init(window.getHandle());
      GLFW.glfwMakeContextCurrent(debugWindow.getHandle());
      debugRenderer = new Renderer2D(debugWindow);
      debugR3d = new Renderer3D(800, 800);
      debugR3d.setFovDegrees(90f);
      GLFW.glfwMakeContextCurrent(window.getHandle());
    }

    CameraEntity cam = new PlayerCameraEntity(0f, 5f, 5f);
    cam.setPosition(new Vec3(0, 5, 0));
    cam.setRotation(0, 0, 0);
    Logger.info("Startup", "Initialize Renderer and make a Camera");

    World world = new World(new IAB(1000, 1000, 1000));
    engine.setWorld(world);
    world.addEntity(cam);
    world.setActiveCamera(cam);
    Logger.info("Startup", "Creating new World with new IAB");

    CullingDebugWindow cullingDebugWindow = null;
    CameraEntity topDownCam = null;

    if (ENABLE_CULLING_DEBUG) {
      cullingDebugWindow = new CullingDebugWindow(world);

      topDownCam =
          new CameraEntity(0f, 200f, 0f) {
            @Override
            public void update(float deltaTime) {
              Vec3 p = cam.getPosition();
              setPosition(new Vec3(p.x(), 200f, p.z()));
              setRotation(90f, 0f, 0f);
            }
          };
      topDownCam.setRigidBodyEnabled(false);
      topDownCam.setGravityEnabled(false);
      topDownCam.setCollidable(false);
      world.addEntity(topDownCam);
    }

    r3d.setAntialiasing(true);
    r3d.setMultisampling(true);

    if (ENABLE_CULLING_DEBUG) {
      GLFW.glfwMakeContextCurrent(debugWindow.getHandle());
      debugR3d.setAntialiasing(true);
      debugR3d.setMultisampling(true);
      debugR3d.setFrustumCullingEnabled(false);
      GLFW.glfwMakeContextCurrent(window.getHandle());
    }

    world.addEntity(new BasicPlaneEntity(0, -3, 0, 50, 50));

    LightEntity mainLight = new LightEntity(28f, 42f, 18f);
    mainLight.setRotation(-48f, -32f, 0f);
    mainLight.setColor(1f, 0.96f, 0.88f);
    mainLight.setIntensity(1.2f);
    mainLight.setShadowOrthoHalfSize(60f);
    mainLight.setCastShadows(true);
    mainLight.setShadowNear(1f);
    mainLight.setShadowFar(200f);
    world.addEntity(mainLight);

    Logger.showConsole();

    Logger.info("Startup", "Starting 12th Engine v1.1.2");

    VideoIntroScreen introScreen = null;
    try {
      introScreen =
          new VideoIntroScreen(
              window, renderer, textRenderer, "src/main/resources/engine-intro.mp4");
    } catch (Exception e) {
      e.printStackTrace();
      Logger.error("Intro", "Failed to initialize intro screen!");
    }

    // --- OBJ ENTITY SETUP ---
    /*
    ModelEntity treeEntity1 = new ModelEntity(0, -2, 0, "/models/tree/Tree.obj");
    ModelEntity treeEntity2 = new ModelEntity(3, -2, 0, "/models/tree/Tree.obj");
    ModelEntity treeEntity3 = new ModelEntity(6, -2, 0, "/models/tree/Tree.obj");
    ModelEntity treeEntity4 = new ModelEntity(9, -2, 0, "/models/tree/Tree.obj");
    */

    ModelEntity bombEntity = null;
    try {
      TwelfthPackage assetArchive =
          new TwelfthPackage("src/main/resources/models/6ovcmof8fc56.twa");

      // 2. Extract the Model Archive (.twm) as raw bytes
      byte[] twmBytes = assetArchive.getFileData("6ovcmof8fc56.twm");

      // 3. Mount the nested .twm into memory (which automatically decrypts the XOR obfuscation!)
      TwelfthPackage modelArchive = new TwelfthPackage(twmBytes, "6ovcmof8fc56.twm");

      // 4. Create the ModelEntity completely out of the obfuscated archive in memory
      bombEntity = new ModelEntity(-6, 600, 0, "6ovcmof8fc56.obj", modelArchive);
    } catch (java.io.IOException e) {
      System.err.println("Failed to load packaged model!");
      e.printStackTrace();
    }

    TextureEntity treeSprite =
        new TextureEntity(-10, 2, 0, "/models/tree/DB2X2_L01.png", 2.0f, 4.0f);

    // Set sizes
    /*
    treeEntity1.setSize(1.0f);
    treeEntity2.setSize(1.0f);
    treeEntity3.setSize(1.0f);
    treeEntity4.setSize(1.0f);

     */
    if (bombEntity != null) {
      bombEntity.setSize(1.0f);

      // Enable physics for bomb
      bombEntity.enableRigidbody();
      bombEntity.setMass(1.0f);
      bombEntity.setDrag(0.999f);
      bombEntity.setGravity(19.62f);
      bombEntity.setRotation(new Vec3(0, 45, 0));
      bombEntity.setCollisionShape(BasicEntity.CollisionShape.AABB);
      bombEntity.setPushable(true);
      bombEntity.setCollidable(true);

      world.addEntity(bombEntity);
    }

    // Add entities to world
    /*
    world.addEntity(treeEntity1);
    world.addEntity(treeEntity2);
    world.addEntity(treeEntity3);
    world.addEntity(treeEntity4);

     */
    world.addEntity(treeSprite);

    // --- Render pipeline setup ---
    final Renderer3D finalDebugR3d = debugR3d;
    renderPipeline.addStep(
        RenderLayer.OPAQUE_3D,
        ctx -> {
          if (ENABLE_CULLING_DEBUG && ctx.renderer3D() == finalDebugR3d) {
            java.util.List<BasicEntity> hidden = new java.util.ArrayList<>();
            java.util.List<Vec3> oldPositions = new java.util.ArrayList<>();
            org.joml.FrustumIntersection f = r3d.getFrustum();

            for (BasicEntity e : ctx.world().getEntities()) {
              if (e instanceof CameraEntity
                  || e instanceof net.twelfthengine.entity.world.LightEntity) continue;

              float radius = e.getCollisionRadius();
              if (e instanceof ModelEntity me) radius = me.getModelBoundingRadius() * me.getSize();
              else if (e instanceof BasicPlaneEntity plane)
                radius = Math.max(plane.getWidth(), plane.getLength());
              else if (e instanceof net.twelfthengine.entity.world.TextureEntity te)
                radius = Math.max(te.getWidth(), te.getHeight());

              if (f != null
                  && !f.testSphere(
                      e.getPosition().x(), e.getPosition().y(), e.getPosition().z(), radius)) {
                hidden.add(e);
                oldPositions.add(e.getPosition());
                // Move far out of bounds so it doesn't render in the debug view
                e.setPosition(new Vec3(100000f, 100000f, 100000f));
              }
            }

            ctx.renderer3D().render(ctx.world());

            // Restore actual positions
            for (int i = 0; i < hidden.size(); i++) {
              hidden.get(i).setPosition(oldPositions.get(i));
            }
          } else {
            ctx.renderer3D().render(ctx.world());
          }
        });
    renderPipeline.addStep(
        RenderLayer.DEBUG_3D,
        ctx -> {
          ctx.renderer3D().setColor(1, 0, 0, 1);
          ctx.renderer3D().drawWireCube(new Vec3(-1, -1, -1), 3f);

          ctx.renderer3D().setColor(0, 0, 1, 1);
          ctx.renderer3D().drawFilledBox(new Vec3(5, 5, 5), new Vec3(3, 3, 3));

          if (ENABLE_CULLING_DEBUG && ctx.renderer3D() == finalDebugR3d) {
            ctx.renderer3D().setColor(1f, 1f, 0f, 1f);
            org.joml.Matrix4f view =
                new org.joml.Matrix4f()
                    .rotateX((float) Math.toRadians(cam.getPitch()))
                    .rotateY((float) Math.toRadians(cam.getYaw()))
                    .rotateZ((float) Math.toRadians(cam.getRoll()))
                    .translate(
                        -cam.getPosition().x(), -cam.getPosition().y(), -cam.getPosition().z());

            org.joml.Matrix4f proj =
                new org.joml.Matrix4f()
                    .perspective((float) Math.toRadians(95f), 1920f / 1080f, 0.1f, 1000f);

            org.joml.Matrix4f invProjView = new org.joml.Matrix4f(proj).mul(view).invert();

            Vec3[] corners = new Vec3[8];
            int i = 0;
            for (int x = -1; x <= 1; x += 2) {
              for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                  org.joml.Vector4f v = new org.joml.Vector4f(x, y, z, 1.0f);
                  v.mul(invProjView);
                  corners[i++] = new Vec3(v.x / v.w, v.y / v.w, v.z / v.w);
                }
              }
            }

            ctx.renderer3D().drawLine(corners[0], corners[2]);
            ctx.renderer3D().drawLine(corners[2], corners[6]);
            ctx.renderer3D().drawLine(corners[6], corners[4]);
            ctx.renderer3D().drawLine(corners[4], corners[0]);

            ctx.renderer3D().drawLine(corners[1], corners[3]);
            ctx.renderer3D().drawLine(corners[3], corners[7]);
            ctx.renderer3D().drawLine(corners[7], corners[5]);
            ctx.renderer3D().drawLine(corners[5], corners[1]);

            ctx.renderer3D().drawLine(corners[0], corners[1]);
            ctx.renderer3D().drawLine(corners[2], corners[3]);
            ctx.renderer3D().drawLine(corners[4], corners[5]);
            ctx.renderer3D().drawLine(corners[6], corners[7]);
          }
        });
    final boolean[] showDebugOverlay = {false};
    final int graphSamples = 100;
    final int[] fpsHistory = new int[graphSamples];
    final int[] tpsHistory = new int[graphSamples];
    final int[] graphIndex = {0};
    final long[] lastGraphSampleNano = {System.nanoTime()};
    final double graphSampleStepSeconds = 0.10;

    final boolean[] paused = {false};
    final PauseMenuScreen pauseMenu = new PauseMenuScreen();

    renderPipeline.addStep(
        RenderLayer.UI_2D,
        ctx -> {
          int sw = ctx.window().getWidth();
          int sh = ctx.window().getHeight();
          int centerX = sw / 2;
          int centerY = sh / 2;
          int size = 8;
          float hudFontSize = 2f;
          String tpsText = "TPS: " + CURRENT_TPS;
          String tickText = "Ticks: " + TICK_COUNTER;

          if (paused[0]) {
            pauseMenu.captureBackdrop(ctx.window());
            pauseMenu.layout(sw, sh);
            pauseMenu.drawBackdropBlurAndDim(ctx.renderer2D(), sw, sh);
            double mx = InputManager.getMouseX();
            double my = InputManager.getMouseY();
            pauseMenu.drawButtons(ctx.window(), ctx.renderer2D(), textRenderer, mx, my);
          } else {
            ctx.renderer2D().setColor(1f, 1f, 1f, 1f);
            ctx.renderer2D().drawLine(centerX - size, centerY, centerX + size, centerY);
            ctx.renderer2D().drawLine(centerX, centerY - size, centerX, centerY + size);

            float textWidth = textRenderer.getTextWidth(tpsText, hudFontSize);
            float textX = sw - textWidth - 16f;
            float textY = sh - 20f;
            textRenderer.drawText2D(tpsText, textX, textY, hudFontSize, 1f, 1f, 1f, 1f);

            float tickTextX = 16f;
            float tickTextY = sh - 20f;
            textRenderer.drawText2D(tickText, tickTextX, tickTextY, hudFontSize, 1f, 1f, 1f, 1f);
          }

          if (showDebugOverlay[0] && !paused[0]) {
            float debugScale = 1.4f;
            float infoY = 16f;
            Vec3 p = cam.getPosition();

            textRenderer.drawText2D(
                "12th Engine Debug (F3)", 16f, infoY, debugScale, 0.9f, 1f, 0.9f, 1f);
            infoY += 18f;
            textRenderer.drawText2D(
                String.format(Locale.US, "Pos: X %.2f  Y %.2f  Z %.2f", p.x(), p.y(), p.z()),
                16f,
                infoY,
                debugScale,
                1f,
                1f,
                1f,
                1f);
            infoY += 16f;
            textRenderer.drawText2D(
                String.format(
                    Locale.US,
                    "Rot: Pitch %.2f  Yaw %.2f  Roll %.2f",
                    cam.getPitch(),
                    cam.getYaw(),
                    cam.getRoll()),
                16f,
                infoY,
                debugScale,
                1f,
                1f,
                1f,
                1f);
            infoY += 16f;
            textRenderer.drawText2D(
                "FPS: "
                    + CURRENT_FPS
                    + " | TPS: "
                    + CURRENT_TPS
                    + " | Entities: "
                    + world.getEntities().size(),
                16f,
                infoY,
                debugScale,
                1f,
                1f,
                1f,
                1f);
            infoY += 16f;
            textRenderer.drawText2D(
                String.format(
                    Locale.US,
                    "Frame: %.2f ms | Tick: %.2f ms | LagSim: %s",
                    CURRENT_FPS > 0 ? 1000f / CURRENT_FPS : 0f,
                    CURRENT_TPS > 0 ? 1000f / CURRENT_TPS : 0f,
                    SIMULATE_LAG ? "ON" : "OFF"),
                16f,
                infoY,
                debugScale,
                1f,
                1f,
                1f,
                1f);

            int graphX = 16;
            int graphY = 104;
            int graphW = 520;
            int graphH = 120;
            int maxFpsScale = 240;
            int maxTpsScale = 20;

            ctx.renderer2D().setColor(0f, 0f, 0f, 0.45f);
            ctx.renderer2D().drawRect(graphX - 4, graphY - 4, graphW + 8, graphH + 28);

            ctx.renderer2D().setColor(0.25f, 0.25f, 0.25f, 1f);
            ctx.renderer2D().drawLine(graphX, graphY + graphH, graphX + graphW, graphY + graphH);
            ctx.renderer2D().drawLine(graphX, graphY, graphX, graphY + graphH);
            ctx.renderer2D().drawLine(graphX + graphW, graphY, graphX + graphW, graphY + graphH);

            for (int i = 0; i < graphSamples - 1; i++) {
              int idxA = (graphIndex[0] + i) % graphSamples;
              int idxB = (graphIndex[0] + i + 1) % graphSamples;

              int x1 = graphX + (i * graphW) / (graphSamples - 1);
              int x2 = graphX + ((i + 1) * graphW) / (graphSamples - 1);

              int fps1 = Math.min(maxFpsScale, fpsHistory[idxA]);
              int fps2 = Math.min(maxFpsScale, fpsHistory[idxB]);
              int yFps1 = graphY + graphH - ((fps1 * graphH) / maxFpsScale);
              int yFps2 = graphY + graphH - ((fps2 * graphH) / maxFpsScale);
              ctx.renderer2D().setColor(0.2f, 0.75f, 1f, 1f);
              ctx.renderer2D().drawLine(x1, yFps1, x2, yFps2);

              int tps1 = Math.min(maxTpsScale, tpsHistory[idxA]);
              int tps2 = Math.min(maxTpsScale, tpsHistory[idxB]);
              int yTps1 = graphY + graphH - ((tps1 * graphH) / maxTpsScale);
              int yTps2 = graphY + graphH - ((tps2 * graphH) / maxTpsScale);
              ctx.renderer2D().setColor(0.35f, 1f, 0.35f, 1f);
              ctx.renderer2D().drawLine(x1, yTps1, x2, yTps2);
            }

            textRenderer.drawText2D(
                "Profiler Graph: TPS(left scale 0-20) / FPS(right scale 0-240)",
                graphX,
                graphY + graphH + 8,
                1.0f,
                1f,
                1f,
                1f,
                1f);
          }
        });

    long lastTime = System.nanoTime();
    long tpsWindowStart = System.nanoTime();
    int ticksInWindow = 0;
    TickProfiler profiler = new TickProfiler(TICK_RATE);
    double accumulator = 0.0;

    long fpsWindowStart = System.nanoTime();
    int framesInWindow = 0;
    long discordLastUpdate = System.nanoTime();
    double DISCORD_UPDATE_INTERVAL = 5.0;

    while (!window.shouldClose() && EngineObject.getInstance().isRunning()) {
      long currentTime = System.nanoTime();
      float deltaTime = (currentTime - lastTime) / 1_000_000_000f;
      lastTime = currentTime;
      framesInWindow++;

      GLFW.glfwPollEvents();
      InputManager.update();

      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F3)) {
        showDebugOverlay[0] = !showDebugOverlay[0];
      }
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F11)) {
        window.toggleFullscreen();
      }

      if (introScreen != null && introScreen.isPlaying()) {
        try {
          GLFW.glfwPollEvents();
          introScreen.update(deltaTime);
          GLFW.glfwMakeContextCurrent(window.getHandle());
          introScreen.render();
          window.update();
        } catch (Exception e) {
          e.printStackTrace();
          Logger.error("Intro", "Error during intro playback!");
        }
        continue;
      }

      accumulator += deltaTime;

      while (accumulator >= TICK_TIME) {
        if (SIMULATE_LAG) {
          try {
            Thread.sleep(LAG_DELAY_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        TICK_COUNTER++;
        ticksInWindow++;
        profiler.startTick();
        tickManager.fire(TickPhase.PRE, TICK_TIME);
        Vec3 toLight = mainLight.getDirectionToLightWorld();
        System.out.println("LightDir: " + toLight.x() + " " + toLight.y() + " " + toLight.z());
        tickManager.fire(TickPhase.POST, TICK_TIME);
        profiler.endTick();
        accumulator -= TICK_TIME;
      }

      double tpsWindowElapsed = (currentTime - tpsWindowStart) / 1_000_000_000.0;
      if (tpsWindowElapsed >= 1.0) {
        CURRENT_TPS = ticksInWindow;
        ticksInWindow = 0;
        tpsWindowStart = currentTime;
      }
      profiler.update();

      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
        paused[0] = !paused[0];
        if (paused[0]) {
          window.unlockMouse();
        } else {
          window.lockMouse();
        }
      }
      if (paused[0] && InputManager.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
        PauseMenuScreen.Action a =
            pauseMenu.handleClick(window, InputManager.getMouseX(), InputManager.getMouseY());
        if (a == PauseMenuScreen.Action.RESUME) {
          paused[0] = false;
          window.lockMouse();
        } else if (a == PauseMenuScreen.Action.EXIT) {
          window.requestClose();
        }
      }
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_L)) {
        SIMULATE_LAG = !SIMULATE_LAG;
        Logger.info(
            "TickLag",
            "Simulated lag "
                + (SIMULATE_LAG ? "enabled" : "disabled")
                + " ("
                + LAG_DELAY_MS
                + "ms delay per tick)");
      }

      double fpsElapsed = (currentTime - fpsWindowStart) / 1_000_000_000.0;
      if (fpsElapsed >= 1.0) {
        CURRENT_FPS = framesInWindow;
        framesInWindow = 0;
        fpsWindowStart = currentTime;
      }

      double graphElapsed = (currentTime - lastGraphSampleNano[0]) / 1_000_000_000.0;
      if (graphElapsed >= graphSampleStepSeconds) {
        fpsHistory[graphIndex[0]] = CURRENT_FPS;
        tpsHistory[graphIndex[0]] = CURRENT_TPS;
        graphIndex[0] = (graphIndex[0] + 1) % graphSamples;
        lastGraphSampleNano[0] = currentTime;
      }

      engine.getWorld().setActiveCamera(cam);
      if (!paused[0]) {
        engine.getWorld().update(deltaTime);
      }

      double discordElapsed = (currentTime - discordLastUpdate) / 1_000_000_000.0;
      if (discordElapsed >= DISCORD_UPDATE_INTERVAL) {
        String state;
        if (paused[0]) {
          state = "Paused";
        } else {
          state = "In World";
        }

        String details = "FPS: " + CURRENT_FPS + " | TPS: " + CURRENT_TPS;

        DiscordPresence.update(details, state);

        discordLastUpdate = currentTime;
      }

      GLFW.glfwMakeContextCurrent(window.getHandle());
      renderPipeline.renderFrame(new RenderContext(window, engine.getWorld(), renderer, r3d));
      window.update();

      if (ENABLE_CULLING_DEBUG) {
        if (!debugWindow.shouldClose()) {
          GLFW.glfwMakeContextCurrent(debugWindow.getHandle());
          engine.getWorld().setActiveCamera(topDownCam);
          renderPipeline.renderFrame(
              new RenderContext(debugWindow, engine.getWorld(), debugRenderer, finalDebugR3d));
          debugWindow.update();
        }

        if (cullingDebugWindow != null) {
          cullingDebugWindow.update(r3d.getFrustum());
        }
      }
    }

    pauseMenu.dispose();
    if (ENABLE_CULLING_DEBUG && debugWindow != null) {
      debugWindow.close();
    }
    window.close();
    engine.stop();
    DiscordPresence.shutdown();
    Logger.info("Window", "Window closed, Engine continues running!");
  }
}
