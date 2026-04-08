package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class ColorGradingEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float brightness = 0.0f; // additive, -1..1
  private float contrast = 1.0f; // multiplicative around midpoint
  private float saturation = 1.0f; // 0 = grayscale, 1 = normal, >1 = vivid
  private float gamma = 1.0f; // >1 = brighter midtones, <1 = darker
  private float[] shadows = {1.0f, 1.0f, 1.0f}; // RGB tint for shadows
  private float[] midtones = {1.0f, 1.0f, 1.0f}; // RGB tint for midtones
  private float[] highlights = {1.0f, 1.0f, 1.0f}; // RGB tint for highlights

  public ColorGradingEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/colorgrading.frag");
    quad = new FullscreenQuad();
  }

  public ColorGradingEffect brightness(float b) {
    this.brightness = b;
    return this;
  }

  public ColorGradingEffect contrast(float c) {
    this.contrast = c;
    return this;
  }

  public ColorGradingEffect saturation(float s) {
    this.saturation = s;
    return this;
  }

  public ColorGradingEffect gamma(float g) {
    this.gamma = g;
    return this;
  }

  public ColorGradingEffect shadows(float r, float g, float b) {
    shadows = new float[] {r, g, b};
    return this;
  }

  public ColorGradingEffect midtones(float r, float g, float b) {
    midtones = new float[] {r, g, b};
    return this;
  }

  public ColorGradingEffect highlights(float r, float g, float b) {
    highlights = new float[] {r, g, b};
    return this;
  }

  /** Convenience: warm cinematic preset */
  public ColorGradingEffect presetCinematic() {
    return brightness(0.02f)
        .contrast(1.1f)
        .saturation(0.85f)
        .gamma(1.05f)
        .shadows(0.9f, 0.95f, 1.05f)
        .midtones(1.0f, 0.98f, 0.92f)
        .highlights(1.05f, 1.02f, 0.95f);
  }

  /** Convenience: cold horror preset */
  public ColorGradingEffect presetHorror() {
    return brightness(-0.05f)
        .contrast(1.2f)
        .saturation(0.6f)
        .gamma(0.9f)
        .shadows(0.8f, 0.9f, 1.1f)
        .midtones(0.9f, 0.95f, 1.0f)
        .highlights(0.95f, 1.0f, 1.05f);
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    shader.setUniform1f("uBrightness", brightness);
    shader.setUniform1f("uContrast", contrast);
    shader.setUniform1f("uSaturation", saturation);
    shader.setUniform1f("uGamma", gamma);
    shader.setUniform3f("uShadows", shadows[0], shadows[1], shadows[2]);
    shader.setUniform3f("uMidtones", midtones[0], midtones[1], midtones[2]);
    shader.setUniform3f("uHighlights", highlights[0], highlights[1], highlights[2]);

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
