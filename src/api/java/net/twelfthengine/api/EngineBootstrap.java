package net.twelfthengine.api;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.util.Locale;
import net.twelfthengine.controls.InputManager;
import net.twelfthengine.coord.iab.IAB;
import net.twelfthengine.core.EngineObject;
import net.twelfthengine.core.discord.DiscordPresence;
import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.core.profiler.EngineProfiler;
import net.twelfthengine.core.tick.TickManager;
import net.twelfthengine.core.tick.TickPhase;
import net.twelfthengine.core.tick.TickProfiler;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.gui.PauseMenuScreen;
import net.twelfthengine.gui.VideoIntroScreen;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderContext;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;
import org.lwjgl.glfw.GLFW;

public class EngineBootstrap {

  private static ImGuiImplGlfw imGuiGlfw;
  private static ImGuiImplGl3 imGuiGl3;

  public static void run(TwelfthApp app, AppConfig config) throws Exception {

    // ------------------------------------------------------------------
    // Engine core
    // ------------------------------------------------------------------
    EngineObject engine = EngineObject.getInstance();
    engine.start();
    DiscordPresence.init();
    Logger.info("Startup", "Starting EngineObject");

    TickManager tickManager = engine.getTickManager();
    Logger.info("Startup", "Starting TickManager");

    // ------------------------------------------------------------------
    // Window
    // ------------------------------------------------------------------
    Window window = new Window(0, config.width(), config.height(), config.title());
    window.init(window.getHandle(), false);
    window.lockMouse();
    window.setWindowIconFromResource("app-icon.png");
    InputManager.init(window.getHandle());

    ImGui.createContext();

    ImGuiStyle style = ImGui.getStyle();
    style.setWindowRounding(10f);

    imGuiGlfw = new ImGuiImplGlfw();
    imGuiGl3 = new ImGuiImplGl3();

    imGuiGlfw.init(window.getHandle(), true);
    imGuiGl3.init("#version 330");

    Logger.info("Startup", "Initialized InputManager and Window");

    // ------------------------------------------------------------------
    // Renderers
    // ------------------------------------------------------------------
    Renderer2D renderer2D = new Renderer2D(window);
    Renderer3D renderer3D = new Renderer3D(config.width(), config.height());
    TextRenderer textRenderer = new TextRenderer();
    renderer3D.setFovDegrees(config.rendererFov());
    renderer3D.getLegacy().setAntialiasing(config.enableAntialiasing());
    renderer3D.getLegacy().setMultisampling(config.enableMultisampling());

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------
    World world = new World(new IAB(1000, 1000, 1000));
    engine.setWorld(world);
    Logger.info("Startup", "Created World");

    // ------------------------------------------------------------------
    // Game init
    // ------------------------------------------------------------------
    app._setWorld(world);
    app.onInit(world, config);
    Logger.info("Startup", "Game onInit complete");

    // ------------------------------------------------------------------
    // Game render pipeline
    // ------------------------------------------------------------------
    RenderPipeline pipeline =
        app.onSetupRenderer(world, config, window, renderer2D, renderer3D, textRenderer);
    Logger.info("Startup", "Game render pipeline ready");

    // ------------------------------------------------------------------
    // Engine profiler
    // ------------------------------------------------------------------
    EngineProfiler engineProfiler = new EngineProfiler(config.tickRate());

    // ------------------------------------------------------------------
    // Engine overlay pipeline — F3 debug HUD + pause menu + crosshair
    // ------------------------------------------------------------------
    final boolean[] showDebugOverlay = {false};
    final boolean[] paused = {false};
    final PauseMenuScreen pauseMenu = new PauseMenuScreen();

    // These are still needed by the F3 overlay which draws its own graph
    final int graphSamples = 100;
    final int[] fpsHistory = new int[graphSamples];
    final int[] tpsHistory = new int[graphSamples];
    final int[] graphIndex = {0};

    pipeline.addStep(
        RenderLayer.UI_2D_OVERLAY,
        ctx -> {
          int sw = ctx.window().getWidth();
          int sh = ctx.window().getHeight();
          int centerX = sw / 2;
          int centerY = sh / 2;
          int crosshairSize = 8;
          float hudFontSize = 2f;

          if (paused[0]) {
            pauseMenu.captureBackdrop(ctx.window());
            pauseMenu.layout(sw, sh);
            pauseMenu.drawBackdropBlurAndDim(ctx.renderer2D(), sw, sh);
            pauseMenu.drawButtons(
                ctx.window(),
                ctx.renderer2D(),
                textRenderer,
                InputManager.getMouseX(),
                InputManager.getMouseY());
          } else {
            // Crosshair
            ctx.renderer2D().setColor(1f, 1f, 1f, 1f);
            ctx.renderer2D()
                .drawLine(centerX - crosshairSize, centerY, centerX + crosshairSize, centerY);
            ctx.renderer2D()
                .drawLine(centerX, centerY - crosshairSize, centerX, centerY + crosshairSize);

            // TPS counter (bottom-right)
            String tpsText = "TPS: " + engineProfiler.getCurrentTps();
            float tpsWidth = textRenderer.getTextWidth(tpsText, hudFontSize);
            textRenderer.drawText2D(
                tpsText, sw - tpsWidth - 16f, sh - 20f, hudFontSize, 1f, 1f, 1f, 1f);

            // Tick counter (bottom-left)
            textRenderer.drawText2D(
                "Ticks: " + engineProfiler.getTickCounter(),
                16f,
                sh - 20f,
                hudFontSize,
                1f,
                1f,
                1f,
                1f);
          }

          // F3 debug overlay
          if (showDebugOverlay[0] && !paused[0]) {
            CameraEntity cam = world.getActiveCamera();
            float scale = 1.4f;
            float infoY = 16f;

            textRenderer.drawText2D(
                "12th Engine Debug (F3)", 16f, infoY, scale, 0.9f, 1f, 0.9f, 1f);
            infoY += 18f;

            if (cam != null) {
              Vec3 p = cam.getPosition();
              textRenderer.drawText2D(
                  String.format(Locale.US, "Pos: X %.2f  Y %.2f  Z %.2f", p.x(), p.y(), p.z()),
                  16f,
                  infoY,
                  scale,
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
                  scale,
                  1f,
                  1f,
                  1f,
                  1f);
              infoY += 16f;
            }

            textRenderer.drawText2D(
                "FPS: "
                    + engineProfiler.getCurrentFps()
                    + " | TPS: "
                    + engineProfiler.getCurrentTps()
                    + " | Entities: "
                    + world.getEntities().size(),
                16f,
                infoY,
                scale,
                1f,
                1f,
                1f,
                1f);
            infoY += 16f;

            textRenderer.drawText2D(
                String.format(
                    Locale.US,
                    "Frame: %.2f ms | Tick: %.2f ms | LagSim: %s",
                    engineProfiler.getCurrentFps() > 0
                        ? 1000f / engineProfiler.getCurrentFps()
                        : 0f,
                    engineProfiler.getCurrentTps() > 0
                        ? 1000f / engineProfiler.getCurrentTps()
                        : 0f,
                    engineProfiler.isSimulatingLag() ? "ON" : "OFF"),
                16f,
                infoY,
                scale,
                1f,
                1f,
                1f,
                1f);

            // F3 profiler graph (uses same history arrays, synced in main loop)
            int gx = 16, gy = 104, gw = 520, gh = 120;
            ctx.renderer2D().setColor(0f, 0f, 0f, 0.45f);
            ctx.renderer2D().drawRect(gx - 4, gy - 4, gw + 8, gh + 28);
            ctx.renderer2D().setColor(0.25f, 0.25f, 0.25f, 1f);
            ctx.renderer2D().drawLine(gx, gy + gh, gx + gw, gy + gh);
            ctx.renderer2D().drawLine(gx, gy, gx, gy + gh);
            ctx.renderer2D().drawLine(gx + gw, gy, gx + gw, gy + gh);

            for (int i = 0; i < graphSamples - 1; i++) {
              int idxA = (graphIndex[0] + i) % graphSamples;
              int idxB = (graphIndex[0] + i + 1) % graphSamples;
              int x1 = gx + (i * gw) / (graphSamples - 1);
              int x2 = gx + ((i + 1) * gw) / (graphSamples - 1);

              int fps1 = Math.min(240, fpsHistory[idxA]);
              int fps2 = Math.min(240, fpsHistory[idxB]);
              ctx.renderer2D().setColor(0.2f, 0.75f, 1f, 1f);
              ctx.renderer2D()
                  .drawLine(x1, gy + gh - (fps1 * gh / 240), x2, gy + gh - (fps2 * gh / 240));

              int tps1 = Math.min(20, tpsHistory[idxA]);
              int tps2 = Math.min(20, tpsHistory[idxB]);
              ctx.renderer2D().setColor(0.35f, 1f, 0.35f, 1f);
              ctx.renderer2D()
                  .drawLine(x1, gy + gh - (tps1 * gh / 20), x2, gy + gh - (tps2 * gh / 20));
            }

            textRenderer.drawText2D(
                "Profiler Graph: TPS (left 0-20) / FPS (right 0-240)",
                gx,
                gy + gh + 8,
                1.0f,
                1f,
                1f,
                1f,
                1f);
          }
        });

    // ------------------------------------------------------------------
    // Intro screen
    // ------------------------------------------------------------------
    VideoIntroScreen introScreen = null;
    String introPath = config.introVideoPath();
    if (introPath != null && !introPath.isBlank()) {
      try {
        introScreen = new VideoIntroScreen(window, renderer2D, textRenderer, introPath);
      } catch (Exception e) {
        e.printStackTrace();
        Logger.error("Intro", "Failed to initialize intro screen!");
      }
    }

    Logger.info("Startup", "Starting 12th Engine v1.1.3");
    Logger.showConsole();

    // ------------------------------------------------------------------
    // Main loop state
    // ------------------------------------------------------------------
    double tickTime = 1.0 / config.tickRate();
    double accumulator = 0.0;
    long lastTime = System.nanoTime();
    TickProfiler tickProfiler = new TickProfiler(config.tickRate());

    long discordLastUpdate = System.nanoTime();
    double DISCORD_INTERVAL = 5.0;

    // ------------------------------------------------------------------
    // Main loop
    // ------------------------------------------------------------------
    while (!window.shouldClose() && EngineObject.getInstance().isRunning()) {

      long currentTime = System.nanoTime();
      float deltaTime = (currentTime - lastTime) / 1_000_000_000f;
      lastTime = currentTime;

      GLFW.glfwPollEvents();
      InputManager.update();

      // ----------------------------------------------------------------
      // Update profiler — must happen before ImGui reads any values
      // ----------------------------------------------------------------
      engineProfiler.update(currentTime, deltaTime);

      // Sync history arrays for the F3 overlay graph
      fpsHistory[graphIndex[0]] = engineProfiler.getCurrentFps();
      tpsHistory[graphIndex[0]] = engineProfiler.getCurrentTps();
      graphIndex[0] = (graphIndex[0] + 1) % graphSamples;

      // ----------------------------------------------------------------
      // ImGui
      // ----------------------------------------------------------------
      imGuiGlfw.newFrame();
      ImGui.newFrame();

      engineProfiler.render(world, window, paused[0]);

      // ----------------------------------------------------------------
      // Key toggles
      // ----------------------------------------------------------------
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F3)) showDebugOverlay[0] = !showDebugOverlay[0];
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F11)) window.toggleFullscreen();
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_L)) engineProfiler.toggleLag();

      // ----------------------------------------------------------------
      // Intro — blocks everything else until finished
      // ----------------------------------------------------------------
      if (introScreen != null && introScreen.isPlaying()) {
        try {
          introScreen.update(deltaTime);
          introScreen.render();
          window.update();
          ImGui.render();
          imGuiGl3.renderDrawData(ImGui.getDrawData());
        } catch (Exception e) {
          e.printStackTrace();
          Logger.error("Intro", "Error during intro playback!");
        }
        continue;
      }

      // ----------------------------------------------------------------
      // Fixed-rate tick
      // ----------------------------------------------------------------
      accumulator += deltaTime;
      while (accumulator >= tickTime) {
        if (engineProfiler.isSimulatingLag()) {
          try {
            Thread.sleep(engineProfiler.getLagDelayMs());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        engineProfiler.onTick();
        tickProfiler.startTick();
        tickManager.fire(TickPhase.PRE, tickTime);
        app.onTick(tickTime);
        tickManager.fire(TickPhase.POST, tickTime);
        tickProfiler.endTick();
        accumulator -= tickTime;
      }

      tickProfiler.update();

      // ----------------------------------------------------------------
      // Pause / resume
      // ----------------------------------------------------------------
      if (InputManager.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
        paused[0] = !paused[0];
        if (paused[0]) window.unlockMouse();
        else window.lockMouse();
      }
      if (paused[0] && InputManager.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
        PauseMenuScreen.Action action =
            pauseMenu.handleClick(window, InputManager.getMouseX(), InputManager.getMouseY());
        if (action == PauseMenuScreen.Action.RESUME) {
          paused[0] = false;
          window.lockMouse();
        } else if (action == PauseMenuScreen.Action.EXIT) {
          window.requestClose();
        }
      }

      // ----------------------------------------------------------------
      // World update (skipped while paused)
      // ----------------------------------------------------------------
      if (!paused[0]) engine.getWorld().update(deltaTime);

      // ----------------------------------------------------------------
      // Discord
      // ----------------------------------------------------------------
      double discordElapsed = (currentTime - discordLastUpdate) / 1_000_000_000.0;
      if (discordElapsed >= DISCORD_INTERVAL) {
        DiscordPresence.update(
            "FPS: " + engineProfiler.getCurrentFps() + " | TPS: " + engineProfiler.getCurrentTps(),
            paused[0] ? "Paused" : "In World");
        discordLastUpdate = currentTime;
      }

      // ----------------------------------------------------------------
      // Render
      // ----------------------------------------------------------------
      GLFW.glfwMakeContextCurrent(window.getHandle());
      engine.getWorld().setActiveCamera(world.getActiveCamera());

      RenderContext ctx = new RenderContext(window, engine.getWorld(), renderer2D, renderer3D);
      pipeline.renderFrame(ctx);
      ImGui.render();
      imGuiGl3.renderDrawData(ImGui.getDrawData());
      window.update();
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------
    app.onDispose();
    pauseMenu.dispose();
    window.close();
    engine.stop();
    DiscordPresence.shutdown();
    imGuiGl3.dispose();
    imGuiGlfw.dispose();
    ImGui.destroyContext();
    Logger.info("Window", "Window closed, engine stopped.");
  }
}
