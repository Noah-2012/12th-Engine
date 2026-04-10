package net.twelfthengine.api;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;

public abstract class TwelfthApp {

  private World world;

  // ------------------------------------------------------------------
  // Lifecycle hooks — override in your game
  // ------------------------------------------------------------------

  /**
   * Called once after the engine, window, and world are initialised. Spawn your entities and set
   * the active camera here.
   */
  public abstract void onInit(World world, AppConfig config) throws Exception;

  /**
   * Called once after onInit. Build and return your full RenderPipeline here. The engine passes you
   * pre-constructed Renderer2D, Renderer3D, and TextRenderer so you don't have to create them
   * yourself.
   *
   * <p>The returned pipeline is handed straight to the main loop — you own every RenderLayer step
   * inside it.
   */
  public abstract RenderPipeline onSetupRenderer(
      World world,
      AppConfig config,
      Window window,
      Renderer2D renderer2D,
      Renderer3D renderer3D,
      TextRenderer textRenderer)
      throws Exception;

  /**
   * Called every fixed game tick at the rate defined in AppConfig.
   *
   * @param deltaTime fixed tick time in seconds
   */
  public void onTick(double deltaTime) {}

  /** Called on shutdown — free any game-specific resources here. */
  public void onDispose() {}

  // ------------------------------------------------------------------
  // Engine access for subclasses
  // ------------------------------------------------------------------

  protected World getWorld() {
    return world;
  }

  // Internal — called by EngineBootstrap only, never by games
  public final void _setWorld(World world) {
    this.world = world;
  }
}
