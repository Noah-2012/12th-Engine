package net.twelfthengine.renderer.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;

public class RenderPipeline {

  private Runnable preFrameHook = null;

  public void setPreFrameHook(Runnable hook) {
    this.preFrameHook = hook;
  }

  private final Map<RenderLayer, List<Consumer<RenderContext>>> layerSteps =
      new EnumMap<>(RenderLayer.class);

  public RenderPipeline() {
    for (RenderLayer layer : RenderLayer.values()) {
      layerSteps.put(layer, new ArrayList<>());
    }
  }

  public void addStep(RenderLayer layer, Consumer<RenderContext> step) {
    layerSteps.get(layer).add(step);
  }

  public void renderFrame(RenderContext ctx) {
    // 1️⃣ Pre-Frame Hook zuerst aufrufen (z.B. FBO binden)
    if (preFrameHook != null) preFrameHook.run();

    // 2️⃣ Framebuffer klar machen
    ctx.window().clear(0f, 0f, 0f, 0.98f);

    // 3️⃣ Layer in Reihenfolge rendern
    List<RenderLayer> orderedLayers = new ArrayList<>(List.of(RenderLayer.values()));
    orderedLayers.sort(Comparator.comparingInt(RenderLayer::getOrder));

    for (RenderLayer layer : orderedLayers) {
      applyLayerState(layer, ctx);
      for (Consumer<RenderContext> step : layerSteps.get(layer)) {
        step.accept(ctx);
      }
    }
  }

  private void applyLayerState(RenderLayer layer, RenderContext ctx) {
    switch (layer) {
      case OPAQUE_3D, DEBUG_3D -> {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        ctx.renderer3D().begin3D(ctx.world().getActiveCamera());
      }
      case UI_2D, UI_2D_OVERLAY -> {
        ctx.renderer2D().begin2D();
      }
      case POST_BLIT -> {
        // No state setup — the blit shader manages its own state
        // and restores 2D ortho afterward
      }
    }
  }
}
