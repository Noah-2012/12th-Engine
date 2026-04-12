package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class BloomEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float threshold = 0.8f;
  private float intensity = 1.2f;
  private int   blurRadius = 4;

  // FIX: Cache all uniform locations once after shader link.
  //      glGetUniformLocation is a driver string-lookup — never call it per frame.
  private final int uColorTex;
  private final int uThreshold;
  private final int uIntensity;
  private final int uBlurRadius;

  public BloomEffect() throws Exception {
    shader = new ShaderProgram(
        "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/bloom.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex  = GL20.glGetUniformLocation(prog, "uColorTex");
    uThreshold = GL20.glGetUniformLocation(prog, "uThreshold");
    uIntensity = GL20.glGetUniformLocation(prog, "uIntensity");
    uBlurRadius = GL20.glGetUniformLocation(prog, "uBlurRadius");

    // FIX: Sampler uniforms are constants — bind slot 0 once after linking.
    //      There is no reason to re-upload "uColorTex = 0" every frame.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    shader.unbind();
  }

  public BloomEffect threshold(float t) { this.threshold  = t; return this; }
  public BloomEffect intensity(float i) { this.intensity  = i; return this; }
  public BloomEffect blurRadius(int r)  { this.blurRadius = r; return this; }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    // FIX: Depth test is managed by the pipeline around the whole post-process
    //      pass — toggling it per effect is redundant and wastes GL state changes.
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);

    GL20.glUniform1f(uThreshold, threshold);
    GL20.glUniform1f(uIntensity, intensity);
    GL20.glUniform1i(uBlurRadius, blurRadius);

    quad.draw();
    shader.unbind();
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
