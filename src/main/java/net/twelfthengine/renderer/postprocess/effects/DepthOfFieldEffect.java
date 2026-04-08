package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class DepthOfFieldEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  /** Linear depth (world units) of the focal plane. */
  private float focalDistance = 10.0f;

  /** Distance over which the blur transitions in (world units). */
  private float focalRange = 6.0f;

  /** Maximum blur radius in pixels. */
  private float maxBlur = 8.0f;

  private float near = 0.1f;
  private float far = 1000f;

  /** Number of blur samples (performance vs quality). */
  private int samples = 12;

  public DepthOfFieldEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/dof.frag");
    quad = new FullscreenQuad();
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
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
    shader.setUniform1i("uDepthTex", 1);

    shader.setUniform1f("uFocalDistance", focalDistance);
    shader.setUniform1f("uFocalRange", focalRange);
    shader.setUniform1f("uMaxBlur", maxBlur);
    shader.setUniform1f("uNear", near);
    shader.setUniform1f("uFar", far);
    shader.setUniform1i("uSamples", samples);

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
