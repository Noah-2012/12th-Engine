package net.twelfthengine.renderer;

import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;

/**
 * Represents an entity or component that can be rendered in the 3D pipeline. Implement this
 * interface to allow the Renderer3D to render the object without needing explicit instanceof
 * checks.
 */
public interface Renderable3D {

  /**
   * Called during the main lit rendering pass.
   *
   * @param renderer The 3D renderer instance calling this method
   * @param shader The lit shader program currently in use
   * @param view The view matrix of the active camera
   * @param proj The projection matrix
   * @param lightSpace The light space matrix for shadow mapping
   */
  void renderLit(
      Renderer3D renderer, ShaderProgram shader, Matrix4f view, Matrix4f proj, Matrix4f lightSpace);

  /**
   * Called during the shadow depth map pass. Override this if the object should cast shadows.
   *
   * @param renderer The 3D renderer instance calling this method
   * @param depthShader The shadow depth shader program
   * @param lightSpace The light space matrix for calculating depth
   */
  default void renderShadow(Renderer3D renderer, ShaderProgram depthShader, Matrix4f lightSpace) {
    // Default implementation does nothing (no shadows cast)
  }
}
