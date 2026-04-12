package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class FogEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  private float density = 0.04f;
  private float near = 0.1f;
  private float far = 1000f;

  // FIX: Flat floats instead of float[] — no allocation on color() setter call.
  private float fogR = 0.6f, fogG = 0.65f, fogB = 0.7f;

  // FIX: Cached uniform locations.
  private final int uColorTex;
  private final int uDepthTex;
  private final int uNear;
  private final int uFar;
  private final int uFogDensity;
  private final int uFogColor;

  public FogEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/fog.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uDepthTex = GL20.glGetUniformLocation(prog, "uDepthTex");
    uNear = GL20.glGetUniformLocation(prog, "uNear");
    uFar = GL20.glGetUniformLocation(prog, "uFar");
    uFogDensity = GL20.glGetUniformLocation(prog, "uFogDensity");
    uFogColor = GL20.glGetUniformLocation(prog, "uFogColor");

    // FIX: Sampler slots are constants — set once.
    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    GL20.glUniform1i(uDepthTex, 1);
    shader.unbind();
  }

  public FogEffect density(float d) {
    this.density = d;
    return this;
  }

  public FogEffect near(float n) {
    this.near = n;
    return this;
  }

  public FogEffect far(float f) {
    this.far = f;
    return this;
  }

  public FogEffect color(float r, float g, float b) {
    fogR = r;
    fogG = g;
    fogB = b;
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    // FIX: Removed glPushMatrix / glPopMatrix on both projection and modelview.
    //      fullscreen.vert works entirely in clip space using gl_VertexID or
    //      a pre-transformed VBO — it never reads the GL matrix stack.
    //      Those 4 fixed-function calls were pure overhead with zero effect on
    //      the rendered output.

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);

    GL20.glUniform1f(uNear, near);
    GL20.glUniform1f(uFar, far);
    GL20.glUniform1f(uFogDensity, density);
    GL20.glUniform3f(uFogColor, fogR, fogG, fogB);

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
