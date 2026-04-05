package net.twelfthengine.renderer.pipeline;

/** Ordered render layers. Lower order renders first. */
public enum RenderLayer {
  OPAQUE_3D(0),
  DEBUG_3D(1),
  UI_2D(2),
  POST_BLIT(3),
  UI_2D_OVERLAY(4);

  private final int order;

  RenderLayer(int order) {
    this.order = order;
  }

  public int getOrder() {
    return order;
  }
}
