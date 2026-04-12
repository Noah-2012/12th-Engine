package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class SSAOEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float radius = 0.5f;
  private float intensity = 1.2f;
  private float bias = 0.025f;
  private int samples = 16;
  private int blurRadius = 2;
  private float near = 0.1f;
  private float far = 1000f;

  // FIX: Cached uniform locations.
  private final int uColorTex;
  private final int uDepthTex;
  private final int uRadius;
  private final int uIntensity;
  private final int uBias;
  private final int uSamples;
  private final int uBlurRadius;
  private final int uNear;
  private final int uFar;

  public SSAOEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/ssao.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uDepthTex = GL20.glGetUniformLocation(prog, "uDepthTex");
    uRadius = GL20.glGetUniformLocation(prog, "uRadius");
    uIntensity = GL20.glGetUniformLocation(prog, "uIntensity");
    uBias = GL20.glGetUniformLocation(prog, "uBias");
    uSamples = GL20.glGetUniformLocation(prog, "uSamples");
    uBlurRadius = GL20.glGetUniformLocation(prog, "uBlurRadius");
    uNear = GL20.glGetUniformLocation(prog, "uNear");
    uFar = GL20.glGetUniformLocation(prog, "uFar");

    // FIX: Sampler slots are constants — set once.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    GL20.glUniform1i(uDepthTex, 1);
    shader.unbind();
  }

  public SSAOEffect radius(float r) {
    this.radius = r;
    return this;
  }

  public SSAOEffect intensity(float i) {
    this.intensity = i;
    return this;
  }

  public SSAOEffect bias(float b) {
    this.bias = b;
    return this;
  }

  public SSAOEffect samples(int s) {
    this.samples = s;
    return this;
  }

  public SSAOEffect blurRadius(int r) {
    this.blurRadius = r;
    return this;
  }

  public SSAOEffect near(float n) {
    this.near = n;
    return this;
  }

  public SSAOEffect far(float f) {
    this.far = f;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);

    GL20.glUniform1f(uRadius, radius);
    GL20.glUniform1f(uIntensity, intensity);
    GL20.glUniform1f(uBias, bias);
    GL20.glUniform1i(uSamples, samples);
    GL20.glUniform1i(uBlurRadius, blurRadius);
    GL20.glUniform1f(uNear, near);
    GL20.glUniform1f(uFar, far);

    quad.draw();
    shader.unbind();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
