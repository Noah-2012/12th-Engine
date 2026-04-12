package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class DepthOfFieldEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float focalDistance = 10.0f;
  private float focalRange = 6.0f;
  private float maxBlur = 8.0f;
  private float near = 0.1f;
  private float far = 1000f;
  private int samples = 12;

  // FIX: Cached uniform locations.
  private final int uColorTex;
  private final int uDepthTex;
  private final int uFocalDistance;
  private final int uFocalRange;
  private final int uMaxBlur;
  private final int uNear;
  private final int uFar;
  private final int uSamples;

  public DepthOfFieldEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/dof.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uDepthTex = GL20.glGetUniformLocation(prog, "uDepthTex");
    uFocalDistance = GL20.glGetUniformLocation(prog, "uFocalDistance");
    uFocalRange = GL20.glGetUniformLocation(prog, "uFocalRange");
    uMaxBlur = GL20.glGetUniformLocation(prog, "uMaxBlur");
    uNear = GL20.glGetUniformLocation(prog, "uNear");
    uFar = GL20.glGetUniformLocation(prog, "uFar");
    uSamples = GL20.glGetUniformLocation(prog, "uSamples");

    // FIX: Sampler slots are constants — set once.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    GL20.glUniform1i(uDepthTex, 1);
    shader.unbind();
  }

  public DepthOfFieldEffect focalDistance(float d) {
    this.focalDistance = d;
    return this;
  }

  public DepthOfFieldEffect focalRange(float r) {
    this.focalRange = r;
    return this;
  }

  public DepthOfFieldEffect maxBlur(float m) {
    this.maxBlur = m;
    return this;
  }

  public DepthOfFieldEffect near(float n) {
    this.near = n;
    return this;
  }

  public DepthOfFieldEffect far(float f) {
    this.far = f;
    return this;
  }

  public DepthOfFieldEffect samples(int s) {
    this.samples = s;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);

    GL20.glUniform1f(uFocalDistance, focalDistance);
    GL20.glUniform1f(uFocalRange, focalRange);
    GL20.glUniform1f(uMaxBlur, maxBlur);
    GL20.glUniform1f(uNear, near);
    GL20.glUniform1f(uFar, far);
    GL20.glUniform1i(uSamples, samples);

    quad.draw();
    shader.unbind();

    // Reset active texture unit back to 0 as a courtesy to subsequent effects.
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
