package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class VignetteEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float radius = 0.75f;
  private float softness = 0.45f;
  private float strength = 0.85f;

  // FIX: Flat floats instead of float[] — no allocation on color() setter call.
  private float colorR = 0f, colorG = 0f, colorB = 0f;

  // FIX: Cached uniform locations.
  private final int uColorTex;
  private final int uRadius;
  private final int uSoftness;
  private final int uStrength;
  private final int uColor;

  public VignetteEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/vignette.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uRadius = GL20.glGetUniformLocation(prog, "uRadius");
    uSoftness = GL20.glGetUniformLocation(prog, "uSoftness");
    uStrength = GL20.glGetUniformLocation(prog, "uStrength");
    uColor = GL20.glGetUniformLocation(prog, "uColor");

    // FIX: Sampler slot is constant — set once.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    shader.unbind();
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
    colorR = r;
    colorG = g;
    colorB = b;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);

    GL20.glUniform1f(uRadius, radius);
    GL20.glUniform1f(uSoftness, softness);
    GL20.glUniform1f(uStrength, strength);
    GL20.glUniform3f(uColor, colorR, colorG, colorB);

    quad.draw();
    shader.unbind();
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
