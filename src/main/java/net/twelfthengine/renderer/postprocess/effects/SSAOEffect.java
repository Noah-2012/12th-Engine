package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class SSAOEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  /** World-space radius of the occlusion hemisphere (tweak per scene scale). */
  private float radius = 0.5f;

  /** How strongly occluded areas are darkened (0–1). */
  private float intensity = 1.2f;

  /** Depth bias to prevent self-occlusion artifacts. */
  private float bias = 0.025f;

  /** Number of hemisphere samples (more = better quality, higher cost). */
  private int samples = 16;

  /** Blur radius applied to the raw AO term to soften noise (0 = no blur). */
  private int blurRadius = 2;

  private float near = 0.1f;
  private float far = 1000f;

  public SSAOEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/ssao.frag");
    quad = new FullscreenQuad();
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
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
    shader.setUniform1i("uDepthTex", 1);

    shader.setUniform1f("uRadius", radius);
    shader.setUniform1f("uIntensity", intensity);
    shader.setUniform1f("uBias", bias);
    shader.setUniform1i("uSamples", samples);
    shader.setUniform1i("uBlurRadius", blurRadius);
    shader.setUniform1f("uNear", near);
    shader.setUniform1f("uFar", far);

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
