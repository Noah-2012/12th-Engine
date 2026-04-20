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
import net.twelfthengine.qgui.*;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.PostProcessEffect;
import net.twelfthengine.renderer.postprocess.PostProcessPipeline;
import net.twelfthengine.renderer.postprocess.effects.*;
import net.twelfthengine.scripting.LuaScriptEditor;
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
  private Window windowInstance;

  // --- Lua System & Editor ---
  private LuaSystem lua;
  private LuaScriptEditor luaEditor;
  private boolean lastF1State = false; // for edge detection

  public static void main(String[] args) throws Exception {
    EngineBootstrap.run(new MyGame(), AppConfig.defaults());
  }

  public void onInit(World world, AppConfig config) {
    camera = new PlayerCameraEntity(0f, 5f, 0f);
    camera.setPosition(new Vec3(0, 5, 0));
    camera.setRotation(0, 0, 0);
    world.addEntity(camera);
    world.setActiveCamera(camera);

    // ... (your existing entity setup commented out) ...

    // QGUI setup
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
              int yPos = 40;
              for (PostProcessEffect effect : postProcess.getEffects()) {
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

    // Lua system and editor
    lua = new LuaSystem(world);
    lua.runScript("scripts/init.lua");
    luaEditor = new LuaScriptEditor(lua);
    // Editor starts hidden – press F1 to toggle

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

    this.windowInstance = window;
    renderer3D.setFrustumCullingEnabled(true);
    RenderPipeline pipeline = new RenderPipeline();

    postProcess = new PostProcessPipeline(config.width(), config.height());
    renderer3D.setActiveFbo(postProcess.getFboAId());

    MotionBlurEffect motionBlur = new MotionBlurEffect().strength(13f).samples(320).debug(0);
    postProcess.addEffect(motionBlur);
    postProcess.addEffect(new ChromaticAberrationEffect().strength(0.008f).falloff(3f));

    pipeline.setPreFrameHook(
        () -> {
          // QGUI toggle with C
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

          // Lua editor toggle with F1 (edge detection)
          boolean currentF1State = InputManager.isKeyPressed(GLFW.GLFW_KEY_F1);
          if (currentF1State && !lastF1State) {
            luaEditor.toggleVisible();
          }
          lastF1State = currentF1State;

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
          motionBlur.updateMatrices(ctx.renderer3D().getLastVP());
          ctx.renderer3D().render(ctx.world());
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
          renderer2D.drawTexture("cat.jpg", 10, 10, 150, 84);
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
  public void onRenderImGui(Window window, World world) {
    // Render the Lua editor if it's visible (toggled with F1)
    if (luaEditor != null) {
      luaEditor.render();
    }
  }

  @Override
  public void onTick(double deltaTime) {
    if (lua != null) {
      lua.call("onTick", deltaTime);
    }
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
