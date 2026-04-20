package net.twelfthengine.scripting;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.twelfthengine.core.logger.Logger;

public class LuaScriptEditor {
  private final ImBoolean visible = new ImBoolean(false);
  private final ImString scriptText;
  private String lastError = "";
  private final LuaSystem luaSystem;

  public LuaScriptEditor(LuaSystem luaSystem) {
    this.luaSystem = luaSystem;
    // Allocate a large buffer (64 KB) and set initial text
    this.scriptText = new ImString(65536);
    this.scriptText.set("-- Write your Lua code here\nprint(\"Hello from Lua!\")");
  }

  public void toggleVisible() {
    visible.set(!visible.get());
  }

  public boolean isVisible() {
    return visible.get();
  }

  public void render() {
    if (!visible.get()) return;

    ImGui.setNextWindowSize(800, 600, ImGuiCond.FirstUseEver);
    if (ImGui.begin("Lua Script Editor")) {
      ImGui.text("Script:");
      float inputHeight = ImGui.getWindowHeight() - 120;
      if (inputHeight < 150) inputHeight = 150;

      ImGui.inputTextMultiline("##source", scriptText, ImGui.getWindowWidth() - 20, inputHeight);

      ImGui.spacing();

      if (ImGui.button("Run")) {
        executeScript();
      }
      ImGui.sameLine();
      if (ImGui.button("Clear")) {
        scriptText.set("");
        lastError = "";
      }
      ImGui.sameLine();
      if (ImGui.button("Load...")) {
        Logger.info("LuaEditor", "Load not implemented in this example");
      }
      ImGui.sameLine();
      if (ImGui.button("Save...")) {
        Logger.info("LuaEditor", "Save not implemented in this example");
      }

      if (!lastError.isEmpty()) {
        ImGui.spacing();
        ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Error: " + lastError);
      }
    }
    ImGui.end();
  }

  private void executeScript() {
    lastError = "";
    try {
      luaSystem.run(scriptText.get());
      Logger.info("LuaEditor", "Script executed successfully.");
    } catch (Exception e) {
      lastError = e.getMessage();
      Logger.error("LuaEditor", "Execution error: " + lastError);
    }
  }
}
