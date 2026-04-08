package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class ChromaticAberrationEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  /** How far apart the channels split at the screen edges (in UV units, e.g. 0.005). */
  private float strength = 0.005f;

  /** 0 = uniform split, 1 = only at screen edges. */
  private float falloff = 1.0f;

  public ChromaticAberrationEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/chromatic.frag");
    quad = new FullscreenQuad();
  }

  public ChromaticAberrationEffect strength(float s) {
    this.strength = s;
    return this;
  }

  public ChromaticAberrationEffect falloff(float f) {
    this.falloff = f;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    shader.setUniform1f("uStrength", strength);
    shader.setUniform1f("uFalloff", falloff);

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
