package net.twelfthengine.renderer.pipeline;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.legacy.LegacyRenderer;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;

public class RenderContext {
  private final Window window;
  private final World world;
  private final Renderer2D renderer2D;
  private final Renderer3D renderer3D;
  private final LegacyRenderer legacy;
  private final float delta;

  public RenderContext(
      Window window, World world, Renderer2D renderer2D, Renderer3D renderer3D, float delta) {
    this.window = window;
    this.world = world;
    this.renderer2D = renderer2D;
    this.renderer3D = renderer3D;
    this.legacy = renderer3D.getLegacy(); // derived — no extra argument needed
    this.delta = delta;
  }

  public Window window() {
    return window;
  }

  public World world() {
    return world;
  }

  public Renderer2D renderer2D() {
    return renderer2D;
  }

  public Renderer3D renderer3D() {
    return renderer3D;
  }

  public LegacyRenderer legacy() {
    return legacy;
  }

  public float delta() {
    return delta;
  }
}
