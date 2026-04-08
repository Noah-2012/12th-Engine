package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class VignetteEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  /** Radius of the clear center (0–1). Higher = larger clear area. */
  private float radius = 0.75f;

  /** Softness of the vignette falloff. Higher = softer edge. */
  private float softness = 0.45f;

  /** Darkness of the vignette (0 = invisible, 1 = fully black). */
  private float strength = 0.85f;

  /** Vignette tint color (default black). */
  private float[] color = {0f, 0f, 0f};

  public VignetteEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/vignette.frag");
    quad = new FullscreenQuad();
  }

  public VignetteEffect radius(float r) {
    this.radius = r;
    return this;
  }

  public VignetteEffect softness(float s) {
    this.softness = s;
    return this;
  }

  public VignetteEffect strength(float s) {
    this.strength = s;
    return this;
  }

  public VignetteEffect color(float r, float g, float b) {
    this.color = new float[] {r, g, b};
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    shader.setUniform1f("uRadius", radius);
    shader.setUniform1f("uSoftness", softness);
    shader.setUniform1f("uStrength", strength);
    shader.setUniform3f("uColor", color[0], color[1], color[2]);

    quad.draw();
    shader.unbind();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
