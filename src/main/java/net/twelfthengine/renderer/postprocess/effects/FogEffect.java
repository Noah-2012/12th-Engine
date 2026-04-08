package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class FogEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private float density = 0.04f;
  private float near = 0.1f;
  private float far = 1000f;
  private float[] color = {0.6f, 0.65f, 0.7f};

  private final FullscreenQuad quad;

  public FogEffect() throws Exception {
    shader =
        new ShaderProgram("/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/fog.frag");
    this.quad = new FullscreenQuad();
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
    this.color = new float[] {r, g, b};
    return this;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    // Reset matrices — fullscreen.vert works in clip space, needs no transform
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glPushMatrix();
    GL11.glLoadIdentity();
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glPushMatrix();
    GL11.glLoadIdentity();

    shader.use();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    shader.setUniform1i("uColorTex", 0);

    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
    shader.setUniform1i("uDepthTex", 1);

    shader.setUniform1f("uNear", near);
    shader.setUniform1f("uFar", far);
    shader.setUniform1f("uFogDensity", density);
    shader.setUniform3f("uFogColor", color[0], color[1], color[2]);

    quad.draw();

    shader.unbind();

    // Restore matrix state
    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glPopMatrix();
    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glPopMatrix();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
