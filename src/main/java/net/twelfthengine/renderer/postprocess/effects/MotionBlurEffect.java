package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.BasePostProcessEffect;
import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class MotionBlurEffect extends BasePostProcessEffect {

  private final ShaderProgram shader;
  private final FullscreenQuad quad;

  // ---------------------------------------------------------------
  // Temporal matrices
  //
  // ORDERING CONTRACT (critical for correctness):
  //
  //   Each frame must follow this exact sequence:
  //     1. Render scene
  //     2. effect.updateMatrices(currentVP)   <- records currVP
  //     3. pipeline.present()                 <- calls applyEffect()
  //
  //   Inside applyEffect(), prevVP is already holding last frame's matrix.
  //   At the END of applyEffect() we copy currVP into prevVP for next frame.
  //
  //   If updateMatrices() is called AFTER present() the two matrices will
  //   be identical every frame -> zero velocity -> invisible blur.
  // ---------------------------------------------------------------
  private final Matrix4f prevVP = new Matrix4f();
  private final Matrix4f currVP = new Matrix4f();
  private final Matrix4f invVP = new Matrix4f();
  private final Matrix4f identity = new Matrix4f(); // never mutated

  private int warmupFrames = 2;
  private boolean hasCurrVP = false;

  private float blurStrength = 0.5f;
  private int samples = 8;
  private int debugMode = 0;

  // Cached uniform locations
  private final int uInvVP;
  private final int uPrevVP;
  private final int uColorTex;
  private final int uDepthTex;
  private final int uBlurStrength;
  private final int uSamples;
  private final int uNear;
  private final int uFar;
  private final int uDebugMode;

  private final java.nio.FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
  private final float[] matArr = new float[16];

  public MotionBlurEffect() throws Exception {
    shader =
        new ShaderProgram(
            "/shaders/postprocess/fullscreen.vert", "/shaders/postprocess/motionblur.frag");
    quad = new FullscreenQuad();

    int prog = shader.getProgramId();
    uInvVP = GL20.glGetUniformLocation(prog, "uInvVP");
    uPrevVP = GL20.glGetUniformLocation(prog, "uPrevVP");
    uColorTex = GL20.glGetUniformLocation(prog, "uColorTex");
    uDepthTex = GL20.glGetUniformLocation(prog, "uDepthTex");
    uBlurStrength = GL20.glGetUniformLocation(prog, "uBlurStrength");
    uSamples = GL20.glGetUniformLocation(prog, "uSamples");
    uNear = GL20.glGetUniformLocation(prog, "uNear");
    uFar = GL20.glGetUniformLocation(prog, "uFar");
    uDebugMode = GL20.glGetUniformLocation(prog, "uDebugMode");

    shader.use();
    GL20.glUniform1i(uColorTex, 0);
    GL20.glUniform1i(uDepthTex, 1);
    shader.unbind();
  }

  public MotionBlurEffect strength(float s) {
    this.blurStrength = s;
    return this;
  }

  public MotionBlurEffect samples(int s) {
    this.samples = s;
    return this;
  }

  public MotionBlurEffect debug(int mode) {
    this.debugMode = mode;
    return this;
  }

  /**
   * Call every frame BEFORE pipeline.present(), after the scene has been rendered.
   *
   * <p>Correct order per frame: motionBlur.updateMatrices(renderer.getLastVP());
   * pipeline.present();
   */
  public void updateMatrices(Matrix4f currentVP) {
    currVP.set(currentVP);
    hasCurrVP = true;
  }

  public void resetHistory() {
    warmupFrames = 2;
    hasCurrVP = false;
  }

  @Override
  public void applyEffect(int colorTex, int depthTex) {
    if (!hasCurrVP || warmupFrames > 0) {
      if (hasCurrVP) {
        prevVP.set(currVP);
        warmupFrames--;
      }
      blitPassthrough(colorTex, depthTex);
      return;
    }

    // FIX: prevVP already holds the PREVIOUS frame's matrix on entry here.
    //      We use it for the blur, then update it to currVP at the end.
    //      The old code had prevVP.set(currVP) at the bottom but updateMatrices()
    //      was overwriting currVP after present(), making them equal every frame.
    if (!isMatrixValid(prevVP)) {
      prevVP.set(currVP);
      blitPassthrough(colorTex, depthTex);
      return;
    }

    currVP.invert(invVP);

    shader.use();

    uploadMatrix(uInvVP, invVP);
    uploadMatrix(uPrevVP, prevVP); // prevVP = genuine last-frame matrix

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);

    GL20.glUniform1f(uBlurStrength, blurStrength);
    GL20.glUniform1i(uSamples, samples);
    GL20.glUniform1f(uNear, 0.1f);
    GL20.glUniform1f(uFar, 1000f);
    GL20.glUniform1i(uDebugMode, debugMode);

    quad.draw();
    shader.unbind();

    // Advance history: this frame's currVP becomes next frame's prevVP.
    prevVP.set(currVP);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
  }

  private void blitPassthrough(int colorTex, int depthTex) {
    shader.use();

    uploadMatrix(uInvVP, identity);
    uploadMatrix(uPrevVP, identity);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);

    GL20.glUniform1f(uBlurStrength, 0f);
    GL20.glUniform1i(uSamples, 1);
    GL20.glUniform1f(uNear, 0.1f);
    GL20.glUniform1f(uFar, 1000f);

    quad.draw();
    shader.unbind();

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
  }

  private void uploadMatrix(int location, Matrix4f mat) {
    matBuf.clear();
    mat.get(matBuf);
    GL20.glUniformMatrix4fv(location, false, matBuf);
  }

  private boolean isMatrixValid(Matrix4f m) {
    float det = m.determinant();
    if (Math.abs(det) < 1e-6f) {
      System.err.println("[MOTIONBLUR] Rejected matrix: det=" + det);
      return false;
    }
    m.get(matArr);
    for (float v : matArr) {
      if (Float.isNaN(v) || Float.isInfinite(v)) {
        System.err.println("[MOTIONBLUR] Rejected matrix: NaN/Inf");
        return false;
      }
    }
    return true;
  }

  @Override
  public void dispose() {
    quad.dispose();
    shader.delete();
  }
}
