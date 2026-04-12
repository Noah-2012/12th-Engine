package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class ChromaticAberrationEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float strength = 0.005f;
  private float falloff  = 1.0f;

  // FIX: Cached uniform locations — never call glGetUniformLocation per frame.
  private final int uColorTex;
  private final int uStrength;
  private final int uFalloff;

  public ChromaticAberrationEffect() throws Exception {
    shader = new ShaderProgram(
        "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/chromatic.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uStrength = GL20.glGetUniformLocation(prog, "uStrength");
    uFalloff  = GL20.glGetUniformLocation(prog, "uFalloff");

    // FIX: Sampler slot is constant — set once.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    shader.unbind();
  }

  public ChromaticAberrationEffect strength(float s) { this.strength = s; return this; }
  public ChromaticAberrationEffect falloff(float f)  { this.falloff  = f; return this; }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);

    GL20.glUniform1f(uStrength, strength);
    GL20.glUniform1f(uFalloff,  falloff);

    quad.draw();
    shader.unbind();
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
