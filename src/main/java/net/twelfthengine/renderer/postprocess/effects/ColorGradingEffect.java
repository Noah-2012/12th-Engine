package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class ColorGradingEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float brightness = 0.0f;
  private float contrast = 1.0f;
  private float saturation = 1.0f;
  private float gamma = 1.0f;

  // FIX: Store tints as flat floats instead of float[] arrays.
  //      The setter variants with new float[]{r,g,b} allocate on every call —
  //      with three tint values this is three heap objects per setter invocation.
  //      Flat fields are zero-allocation and cheaper to upload via glUniform3f.
  private float shadowR = 1f, shadowG = 1f, shadowB = 1f;
  private float midtoneR = 1f, midtoneG = 1f, midtoneB = 1f;
  private float highlightR = 1f, highlightG = 1f, highlightB = 1f;

  // FIX: Cached uniform locations.
  private final int uColorTex;
  private final int uBrightness;
  private final int uContrast;
  private final int uSaturation;
  private final int uGamma;
  private final int uShadows;
  private final int uMidtones;
  private final int uHighlights;

  public ColorGradingEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/colorgrading.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uBrightness = GL20.glGetUniformLocation(prog, "uBrightness");
    uContrast = GL20.glGetUniformLocation(prog, "uContrast");
    uSaturation = GL20.glGetUniformLocation(prog, "uSaturation");
    uGamma = GL20.glGetUniformLocation(prog, "uGamma");
    uShadows = GL20.glGetUniformLocation(prog, "uShadows");
    uMidtones = GL20.glGetUniformLocation(prog, "uMidtones");
    uHighlights = GL20.glGetUniformLocation(prog, "uHighlights");

    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    shader.unbind();
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
    shadowR = r;
    shadowG = g;
    shadowB = b;
    return this;
  }

  public ColorGradingEffect midtones(float r, float g, float b) {
    midtoneR = r;
    midtoneG = g;
    midtoneB = b;
    return this;
  }

  public ColorGradingEffect highlights(float r, float g, float b) {
    highlightR = r;
    highlightG = g;
    highlightB = b;
    return this;
  }

  public ColorGradingEffect presetCinematic() {
    return brightness(0.02f)
        .contrast(1.1f)
        .saturation(0.85f)
        .gamma(1.05f)
        .shadows(0.9f, 0.95f, 1.05f)
        .midtones(1.0f, 0.98f, 0.92f)
        .highlights(1.05f, 1.02f, 0.95f);
  }

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
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);

    GL20.glUniform1f(uBrightness, brightness);
    GL20.glUniform1f(uContrast, contrast);
    GL20.glUniform1f(uSaturation, saturation);
    GL20.glUniform1f(uGamma, gamma);
    GL20.glUniform3f(uShadows, shadowR, shadowG, shadowB);
    GL20.glUniform3f(uMidtones, midtoneR, midtoneG, midtoneB);
    GL20.glUniform3f(uHighlights, highlightR, highlightG, highlightB);

    quad.draw();
    shader.unbind();
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
