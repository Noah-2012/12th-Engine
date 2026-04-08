package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class BloomEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float threshold = 0.8f;
  private float intensity = 1.2f;
  private int blurRadius = 4;

  public BloomEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/bloom.frag");
    quad = new FullscreenQuad();
  }

  public BloomEffect threshold(float t) {
    this.threshold = t;
    return this;
  }

  public BloomEffect intensity(float i) {
    this.intensity = i;
    return this;
  }

  public BloomEffect blurRadius(int r) {
    this.blurRadius = r;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    shader.setUniform1f("uThreshold", threshold);
    shader.setUniform1f("uIntensity", intensity);
    shader.setUniform1i("uBlurRadius", blurRadius);

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
