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
  // Temporal state
  // FIX: All Matrix4f instances are now permanent fields reused each frame.
  //      Previously, new Matrix4f(...) was called 3-4 times per frame inside
  //      applyEffect() and blitPassthrough() — pure GC pressure.
  // ---------------------------------------------------------------
  private final Matrix4f prevVP = new Matrix4f();
  private final Matrix4f currVP = new Matrix4f();
  private final Matrix4f invVP = new Matrix4f();
  private final Matrix4f identity = new Matrix4f(); // permanent identity, never mutated

  // FIX: Merged firstFrame + historyValid into a single warmupFrames counter.
  //      The original two booleans expressed the same concept with an ambiguous
  //      interaction order — this is clearer and cheaper.
  private int warmupFrames = 2; // skip blur for 2 frames to seed prevVP cleanly
  private boolean hasCurrVP = false;

  private float blurStrength = 0.5f;
  private int samples = 8;
  private int debugMode = 0;

  // FIX: Cached uniform locations — no glGetUniformLocation per frame.
  private final int uInvVP;
  private final int uPrevVP;
  private final int uColorTex;
  private final int uDepthTex;
  private final int uBlurStrength;
  private final int uSamples;
  private final int uNear;
  private final int uFar;
  private final int uDebugMode;

  // FIX: Cached FloatBuffer for matrix upload — no BufferUtils.createFloatBuffer per frame.
  //      One buffer is enough because we upload one matrix at a time synchronously.
  private final java.nio.FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

  // FIX: Scratch float array for isMatrixValid — no new float[16] per call.
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

    // FIX: Sampler slots are constants — set once.
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
   * Call every frame from the renderer after the world render, before present(). FIX: Uses
   * Matrix4f.set() instead of new Matrix4f(src) — no allocation.
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
      // Seed prevVP so the next real frame has a valid history, then passthrough.
      if (hasCurrVP) {
        prevVP.set(currVP);
        warmupFrames--;
      }
      blitPassthrough(colorTex, depthTex);
      return;
    }

    // FIX: invert into the cached invVP field — no new Matrix4f allocation.
    currVP.invert(invVP);

    if (!isMatrixValid(prevVP)) {
      prevVP.set(currVP);
    }

    shader.use();

    // FIX: Upload matrices via the cached matBuf — no BufferUtils.createFloatBuffer.
    uploadMatrix(uInvVP, invVP);
    uploadMatrix(uPrevVP, prevVP);

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

    // Update history AFTER blur has consumed prevVP.
    prevVP.set(currVP);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
  }

  /**
   * Copies colorTex unchanged. Used during warmup frames so output is never black. FIX: Uses the
   * permanent identity field — no new Matrix4f() allocation.
   */
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

  /** Uploads a Matrix4f into a uniform location using the cached FloatBuffer. */
  private void uploadMatrix(int location, Matrix4f mat) {
    matBuf.clear();
    mat.get(matBuf);
    GL20.glUniformMatrix4fv(location, false, matBuf);
  }

  /**
   * Returns false if the matrix is degenerate (near-zero determinant) or contains NaN/Inf. FIX:
   * Uses the cached matArr scratch array — no new float[16] per call.
   */
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
