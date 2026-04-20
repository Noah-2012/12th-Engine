package net.twelfthengine.editor;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.List;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.camera.EditorCameraEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.pipeline.RenderContext;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;

/**
 * A scene editor overlay rendered inside an ImGui window.
 *
 * <pre>
 * Usage in EngineBootstrap (just before the main loop):
 *
 *   SceneEditorWindow editor = new SceneEditorWindow(window, world);
 *   editor.registerInPipeline(pipeline);
 * </pre>
 *
 * Toggle with F2. While open the editor camera replaces the active camera so 3-D rendering shows
 * the editor view. On close the original camera is restored automatically.
 */
public class SceneEditorWindow {

  // ----------------------------------------------------------------
  // State
  // ----------------------------------------------------------------
  private boolean open = false;

  private final World world;
  private final Window window;
  private final EditorCameraEntity editorCam;

  private CameraEntity savedGameCamera = null;
  private BasicEntity selectedEntity = null;

  // Per-frame ImGui buffers (reused to avoid GC pressure)
  private final float[] pos3 = new float[3];
  private final float[] rot3 = new float[3];
  private final float[] camTgt = new float[3];
  private final float[] camR = new float[1];
  private final ImString entityFilter = new ImString(64);

  // ----------------------------------------------------------------
  // Constructor
  // ----------------------------------------------------------------
  public SceneEditorWindow(Window window, World world) {
    this.window = window;
    this.world = world;
    this.editorCam = new EditorCameraEntity(window.getHandle());

    // Register scroll callback to drive zoom
    org.lwjgl.glfw.GLFW.glfwSetScrollCallback(
        window.getHandle(),
        (win, xOff, yOff) -> {
          if (open) editorCam.zoom((float) yOff);
        });
  }

  // ----------------------------------------------------------------
  // Pipeline registration — call once before the main loop
  // ----------------------------------------------------------------
  public void registerInPipeline(RenderPipeline pipeline) {
    pipeline.addStep(RenderLayer.UI_2D_OVERLAY, this::render);
  }

  // ----------------------------------------------------------------
  // Per-frame update (called from the render step)
  // ----------------------------------------------------------------
  private void render(RenderContext ctx) {
    // F2 toggle
    if (net.twelfthengine.controls.InputManager.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F2)) {
      open = !open;
      if (open) activateEditorCamera();
      else restoreGameCamera();
    }

    if (!open) return;

    float delta = ctx.delta();
    editorCam.editorUpdate(delta);

    // Keep the world rendering through the editor camera
    world.setActiveCamera(editorCam);

    drawMenuBar();
    drawHierarchyPanel();
    drawInspectorPanel();
    drawEditorCameraPanel();
  }

  // ----------------------------------------------------------------
  // Camera swap helpers
  // ----------------------------------------------------------------
  private void activateEditorCamera() {
    savedGameCamera = world.getActiveCamera();
    world.setActiveCamera(editorCam);
    window.unlockMouse(); // editor needs free cursor
  }

  private void restoreGameCamera() {
    if (savedGameCamera != null) {
      world.setActiveCamera(savedGameCamera);
      savedGameCamera = null;
    }
    window.lockMouse();
  }

  // ----------------------------------------------------------------
  // ImGui panels
  // ----------------------------------------------------------------
  private void drawMenuBar() {
    ImGui.setNextWindowPos(0, 0, imgui.flag.ImGuiCond.Always);
    ImGui.setNextWindowSize(window.getWidth(), 24);
    int flags =
        ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.MenuBar;

    if (ImGui.begin("##EditorMenuBar", flags)) {
      if (ImGui.beginMenuBar()) {
        ImGui.textColored(0.4f, 0.9f, 0.5f, 1f, "  12th Engine — Scene Editor");
        ImGui.separator();
        if (ImGui.menuItem("Close (F2)")) {
          open = false;
          restoreGameCamera();
        }
        ImGui.endMenuBar();
      }
    }
    ImGui.end();
  }

  private void drawHierarchyPanel() {
    ImGui.setNextWindowPos(0, 28, imgui.flag.ImGuiCond.Always);
    ImGui.setNextWindowSize(280, window.getHeight() - 28);

    if (ImGui.begin("Hierarchy##editor", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize)) {

      ImGui.text("Filter");
      ImGui.sameLine();
      ImGui.setNextItemWidth(-1);
      ImGui.inputText("##filter", entityFilter);

      ImGui.separator();

      List<BasicEntity> entities = world.getEntities();
      String filter = entityFilter.get().toLowerCase();

      for (int i = 0; i < entities.size(); i++) {
        BasicEntity e = entities.get(i);
        if (e == editorCam) continue; // never show editor camera

        String label = e.getClass().getSimpleName() + " #" + i;
        if (!filter.isEmpty() && !label.toLowerCase().contains(filter)) continue;

        boolean isSelected = e == selectedEntity;
        int treeFlags =
            ImGuiTreeNodeFlags.Leaf
                | ImGuiTreeNodeFlags.NoTreePushOnOpen
                | (isSelected ? ImGuiTreeNodeFlags.Selected : 0);

        ImGui.treeNodeEx(label, treeFlags);
        if (ImGui.isItemClicked()) selectedEntity = e;

        // Right-click context menu
        if (ImGui.beginPopupContextItem("ctx##" + i)) {
          if (ImGui.menuItem("Focus camera")) focusCameraOn(e);
          if (ImGui.menuItem("Remove entity")) {
            world.removeEntity(e);
            if (selectedEntity == e) selectedEntity = null;
          }
          ImGui.endPopup();
        }
      }
    }
    ImGui.end();
  }

  private void drawInspectorPanel() {
    int panelW = 300;
    ImGui.setNextWindowPos(window.getWidth() - panelW, 28, imgui.flag.ImGuiCond.Always);
    ImGui.setNextWindowSize(panelW, window.getHeight() - 28);

    ImGuiStyle style = ImGui.getStyle();
    style.setWindowRounding(0f);

    if (ImGui.begin("Inspector##editor", ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize)) {

      if (selectedEntity == null) {
        ImGui.textDisabled("No entity selected.");
      } else {
        ImGui.text(selectedEntity.getClass().getSimpleName());
        ImGui.separator();

        // Position
        Vec3 p = selectedEntity.getPosition();
        pos3[0] = p.x();
        pos3[1] = p.y();
        pos3[2] = p.z();
        ImGui.text("Position");
        ImGui.setNextItemWidth(-1);
        if (ImGui.dragFloat3("##pos", pos3, 0.1f)) {
          selectedEntity.setPosition(new Vec3(pos3[0], pos3[1], pos3[2]));
        }

        // Rotation (if the entity exposes pitch/yaw/roll)
        if (selectedEntity instanceof CameraEntity cam) {
          rot3[0] = cam.getPitch();
          rot3[1] = cam.getYaw();
          rot3[2] = cam.getRoll();
          ImGui.text("Rotation (P/Y/R)");
          ImGui.setNextItemWidth(-1);
          if (ImGui.dragFloat3("##rot", rot3, 0.5f)) {
            cam.setRotation(rot3[0], rot3[1], rot3[2]);
          }
        }

        // Size (if the entity has a size field)
        try {
          var getSizeMethod = selectedEntity.getClass().getMethod("getSize");
          var setSizeMethod = selectedEntity.getClass().getMethod("setSize", float.class);
          float sz = (float) getSizeMethod.invoke(selectedEntity);
          float[] szArr = {sz};
          ImGui.text("Size");
          ImGui.setNextItemWidth(-1);
          if (ImGui.dragFloat("##size", szArr, 0.01f, 0.001f, 1000f)) {
            setSizeMethod.invoke(selectedEntity, szArr[0]);
          }
        } catch (Exception ignored) {
        }

        ImGui.spacing();
        ImGui.separator();

        // Focus button
        if (ImGui.button("Focus camera", -1, 0)) focusCameraOn(selectedEntity);
      }
    }
    ImGui.end();
  }

  private void drawEditorCameraPanel() {
    ImGui.setNextWindowPos(
        window.getWidth() / 2f - 170, window.getHeight() - 140, imgui.flag.ImGuiCond.Always);
    ImGui.setNextWindowSize(340, 110);

    if (ImGui.begin(
        "Editor Camera##edcam",
        ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse)) {

      camTgt[0] = editorCam.getTargetX();
      camTgt[1] = editorCam.getTargetY();
      camTgt[2] = editorCam.getTargetZ();
      ImGui.text("Orbit target");
      ImGui.setNextItemWidth(-1);
      if (ImGui.dragFloat3("##camtgt", camTgt, 0.1f)) {
        editorCam.setTarget(camTgt[0], camTgt[1], camTgt[2]);
      }

      camR[0] = editorCam.getOrbitRadius();
      ImGui.text("Orbit radius");
      ImGui.setNextItemWidth(-1);
      if (ImGui.sliderFloat("##camr", camR, 1f, 500f)) {
        editorCam.setOrbitRadius(camR[0]);
      }

      ImGui.textDisabled("RMB drag = orbit   MMB drag = pan   Scroll = zoom");
    }
    ImGui.end();
  }

  // ----------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------
  private void focusCameraOn(BasicEntity e) {
    Vec3 p = e.getPosition();
    editorCam.setTarget(p.x(), p.y(), p.z());
  }

  public boolean isOpen() {
    return open;
  }
}
