package net.twelfthengine.core.profiler;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import net.twelfthengine.controls.InputManager;
import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;

public class EngineProfiler {

  private static final int GRAPH_SAMPLES = 100;
  private static final double GRAPH_SAMPLE_STEP = 0.10;

  private final int[] fpsHistory = new int[GRAPH_SAMPLES];
  private final int[] tpsHistory = new int[GRAPH_SAMPLES];
  private int graphIndex = 0;
  private long lastGraphSampleNano = System.nanoTime();

  private int currentFps = 0;
  private int currentTps = 0;
  private int framesInWindow = 0;
  private int ticksInWindow = 0;
  private long fpsWindowStart = System.nanoTime();
  private long tpsWindowStart = System.nanoTime();

  private boolean simulateLag = false;
  private static final int LAG_DELAY_MS = 200;

  private int tickCounter = 0;
  private float lastDeltaTime = 0f;

  private final double targetTickRate;

  public EngineProfiler(double targetTickRate) {
    this.targetTickRate = targetTickRate;
  }

  // ------------------------------------------------------------------
  // Called once per frame BEFORE ImGui rendering
  // ------------------------------------------------------------------
  public void update(long currentTimeNano, float deltaTime) {
    this.lastDeltaTime = deltaTime;

    framesInWindow++;

    double fpsElapsed = (currentTimeNano - fpsWindowStart) / 1_000_000_000.0;
    if (fpsElapsed >= 1.0) {
      currentFps = framesInWindow;
      framesInWindow = 0;
      fpsWindowStart = currentTimeNano;
    }

    double tpsElapsed = (currentTimeNano - tpsWindowStart) / 1_000_000_000.0;
    if (tpsElapsed >= 1.0) {
      currentTps = ticksInWindow;
      ticksInWindow = 0;
      tpsWindowStart = currentTimeNano;
    }

    double graphElapsed = (currentTimeNano - lastGraphSampleNano) / 1_000_000_000.0;
    if (graphElapsed >= GRAPH_SAMPLE_STEP) {
      fpsHistory[graphIndex] = currentFps;
      tpsHistory[graphIndex] = currentTps;
      graphIndex = (graphIndex + 1) % GRAPH_SAMPLES;
      lastGraphSampleNano = currentTimeNano;
    }
  }

  // ------------------------------------------------------------------
  // Called once per tick
  // ------------------------------------------------------------------
  public void onTick() {
    ticksInWindow++;
    tickCounter++;
  }

  // ------------------------------------------------------------------
  // Called after a tick if lag sim is active
  // ------------------------------------------------------------------
  public boolean isSimulatingLag() {
    return simulateLag;
  }

  public int getLagDelayMs() {
    return LAG_DELAY_MS;
  }

  public void toggleLag() {
    simulateLag = !simulateLag;
    Logger.info(
        "Profiler",
        "Lag simulation "
            + (simulateLag ? "enabled" : "disabled")
            + " ("
            + LAG_DELAY_MS
            + " ms/tick)");
  }

  // ------------------------------------------------------------------
  // Renders the ImGui profiler window
  // ------------------------------------------------------------------
  public void render(World world, Window window, boolean paused) {
    ImGui.setNextWindowSize(420, 680, ImGuiCond.Once);
    ImGui.setNextWindowPos(10, 10, ImGuiCond.Once);
    ImGui.begin("12th Engine Profiler");

    // ---- Performance Overview ----
    if (ImGui.collapsingHeader("Performance", ImGuiTreeNodeFlags.DefaultOpen)) {

      float[] fpsColor =
          currentFps >= 60
              ? new float[] {0.2f, 0.9f, 0.3f, 1f}
              : currentFps >= 30
                  ? new float[] {0.95f, 0.75f, 0.1f, 1f}
                  : new float[] {0.95f, 0.2f, 0.2f, 1f};
      ImGui.textColored(
          fpsColor[0],
          fpsColor[1],
          fpsColor[2],
          fpsColor[3],
          String.format(
              "FPS: %d  (%.2f ms/frame)", currentFps, currentFps > 0 ? 1000f / currentFps : 0f));

      float[] tpsColor =
          currentTps >= (int) (targetTickRate * 0.95)
              ? new float[] {0.2f, 0.9f, 0.3f, 1f}
              : currentTps >= (int) (targetTickRate * 0.5)
                  ? new float[] {0.95f, 0.75f, 0.1f, 1f}
                  : new float[] {0.95f, 0.2f, 0.2f, 1f};
      ImGui.textColored(
          tpsColor[0],
          tpsColor[1],
          tpsColor[2],
          tpsColor[3],
          String.format(
              "TPS: %d / %d  (%.2f ms/tick)",
              currentTps, (int) targetTickRate, currentTps > 0 ? 1000f / currentTps : 0f));

      ImGui.separator();

      float[] fpsFloats = new float[GRAPH_SAMPLES];
      for (int i = 0; i < GRAPH_SAMPLES; i++)
        fpsFloats[i] = (float) fpsHistory[(graphIndex + i) % GRAPH_SAMPLES];
      ImGui.pushItemWidth(-1);
      ImGui.plotLines("##fps", fpsFloats, GRAPH_SAMPLES, 0, "FPS  " + currentFps, 0f, 300f);
      ImGui.popItemWidth();
      ImGui.textDisabled("  FPS history (0-300)");

      float[] tpsFloats = new float[GRAPH_SAMPLES];
      for (int i = 0; i < GRAPH_SAMPLES; i++)
        tpsFloats[i] = (float) tpsHistory[(graphIndex + i) % GRAPH_SAMPLES];
      ImGui.pushItemWidth(-1);
      ImGui.plotLines(
          "##tps",
          tpsFloats,
          GRAPH_SAMPLES,
          0,
          "TPS  " + currentTps,
          0f,
          (float) targetTickRate * 1.2f);
      ImGui.popItemWidth();
      ImGui.textDisabled("  TPS history (0-" + (int) (targetTickRate * 1.2f) + ")");

      ImGui.separator();
      ImGui.text(String.format("Total ticks elapsed: %,d", tickCounter));
    }

    // ---- Tick Budget ----
    if (ImGui.collapsingHeader("Tick Budget", ImGuiTreeNodeFlags.DefaultOpen)) {
      float tickBudgetMs = currentTps > 0 ? 1000f / currentTps : 0f;
      float tickBudgetTarget = (float) (1000.0 / targetTickRate);
      float tickLoad = tickBudgetTarget > 0 ? Math.min(1f, tickBudgetMs / tickBudgetTarget) : 0f;

      float[] barColor =
          tickLoad < 0.7f
              ? new float[] {0.2f, 0.8f, 0.3f, 1f}
              : tickLoad < 0.9f
                  ? new float[] {0.95f, 0.75f, 0.1f, 1f}
                  : new float[] {0.95f, 0.2f, 0.2f, 1f};
      ImGui.pushStyleColor(
          ImGuiCol.PlotHistogram, barColor[0], barColor[1], barColor[2], barColor[3]);
      ImGui.pushItemWidth(-1);
      ImGui.progressBar(
          tickLoad, -1, 18, String.format("%.1f / %.1f ms", tickBudgetMs, tickBudgetTarget));
      ImGui.popItemWidth();
      ImGui.popStyleColor();

      ImGui.textDisabled(String.format("  Tick utilisation: %.1f%%", tickLoad * 100f));
      ImGui.text(String.format("Target tick rate:  %d Hz", (int) targetTickRate));
      ImGui.text(String.format("Frame delta:       %.3f ms", lastDeltaTime * 1000f));
      ImGui.text(String.format("Lag sim delay:     %d ms/tick", LAG_DELAY_MS));
    }

    // ---- World & Entities ----
    if (ImGui.collapsingHeader("World & Entities")) {
      ImGui.text(String.format("Entity count:  %d", world.getEntities().size()));

      CameraEntity cam = world.getActiveCamera();
      if (cam != null) {
        Vec3 p = cam.getPosition();
        ImGui.text(String.format("Camera pos:    X %.2f  Y %.2f  Z %.2f", p.x(), p.y(), p.z()));
        ImGui.text(
            String.format(
                "Camera rot:    Pitch %.2f  Yaw %.2f  Roll %.2f",
                cam.getPitch(), cam.getYaw(), cam.getRoll()));
      } else {
        ImGui.textDisabled("No active camera");
      }
      ImGui.separator();
      ImGui.text("World bounds:  1000 x 1000 x 1000");
      ImGui.text("Simulation:    " + (paused ? "PAUSED" : "RUNNING"));
    }

    // ---- Renderer ----
    if (ImGui.collapsingHeader("Renderer")) {
      ImGui.text(String.format("Resolution:    %d x %d", window.getWidth(), window.getHeight()));
    }

    // ---- Input & Controls ----
    if (ImGui.collapsingHeader("Input & Controls")) {
      ImGui.text(
          String.format(
              "Mouse: X %.0f  Y %.0f", InputManager.getMouseX(), InputManager.getMouseY()));
      ImGui.text("Mouse locked:  " + (!paused ? "YES" : "NO"));
      ImGui.separator();
      ImGui.textDisabled("F3  — toggle debug overlay");
      ImGui.textDisabled("F11 — toggle fullscreen");
      ImGui.textDisabled("ESC — pause / resume");
      ImGui.textDisabled("L   — toggle lag simulation");
    }

    // ---- Debug Tools ----
    if (ImGui.collapsingHeader("Debug Tools", ImGuiTreeNodeFlags.DefaultOpen)) {
      if (simulateLag) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.75f, 0.15f, 0.15f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.85f, 0.25f, 0.25f, 1f);
      } else {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.15f, 0.45f, 0.15f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.60f, 0.25f, 1f);
      }
      ImGui.pushItemWidth(-1);
      if (ImGui.button(
          simulateLag ? "Lag Sim: ON  (click to disable)" : "Lag Sim: OFF (click to enable)",
          -1,
          0)) {
        simulateLag = !simulateLag;
        Logger.info(
            "Profiler",
            "Lag simulation "
                + (simulateLag ? "enabled" : "disabled")
                + " ("
                + LAG_DELAY_MS
                + " ms/tick)");
      }
      ImGui.popItemWidth();
      ImGui.popStyleColor(2);
      ImGui.textDisabled("  Injects " + LAG_DELAY_MS + " ms of sleep per tick when ON");

      ImGui.separator();

      if (ImGui.button("Log snapshot", 190, 0)) {
        Logger.info(
            "Profiler",
            "Snapshot — FPS: "
                + currentFps
                + " | TPS: "
                + currentTps
                + " | Entities: "
                + world.getEntities().size()
                + " | Ticks: "
                + tickCounter);
      }
      ImGui.sameLine();
      if (ImGui.button("Reset tick counter", 190, 0)) {
        tickCounter = 0;
      }
    }

    ImGui.end();
  }

  // ------------------------------------------------------------------
  // Getters for EngineBootstrap
  // ------------------------------------------------------------------
  public int getCurrentFps() {
    return currentFps;
  }

  public int getCurrentTps() {
    return currentTps;
  }

  public int getTickCounter() {
    return tickCounter;
  }
}
