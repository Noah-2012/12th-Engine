package net.twelfthengine.renderer.postprocess.effects;

import net.twelfthengine.renderer.postprocess.FullscreenQuad;
import net.twelfthengine.renderer.postprocess.PostProcessEffect;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class MotionBlurEffect implements PostProcessEffect {

    private final ShaderProgram shader;

    // Temporal matrices (fixed timing version)
    private Matrix4f prevVP = null;
    private Matrix4f currVP = null;

    private boolean historyValid = false;
    private boolean firstFrame = true;
    private int debugMode = 0;

    public void resetHistory() {
        historyValid = false;
        prevVP = null;
    }

    private float blurStrength = 0.5f;
    private int   samples      = 8;

    private final FullscreenQuad quad;

    public MotionBlurEffect() throws Exception {
        shader = new ShaderProgram(
                "/shaders/postprocess/fullscreen.vert",
                "/shaders/postprocess/motionblur.frag");
        this.quad = new FullscreenQuad();
    }

    public MotionBlurEffect strength(float s) { this.blurStrength = s; return this; }
    public MotionBlurEffect samples(int s)    { this.samples = s;      return this; }
    public MotionBlurEffect debug(int mode) { this.debugMode = mode; return this; }

    /** Called every frame from renderer after world render */
    public void updateMatrices(Matrix4f currentVP) {
        this.currVP = new Matrix4f(currentVP);
    }

    @Override
    public void apply(int colorTex, int depthTex) {

        // Always need a valid currVP before we can do real motion blur.
        // If we don't have one yet, just blit the color through unchanged.
        if (currVP == null || firstFrame) {
            firstFrame = false;
            blitPassthrough(colorTex, depthTex);
            // Seed history so the very next frame can blur correctly
            if (currVP != null) prevVP = new Matrix4f(currVP);
            return;
        }

        if (!historyValid) {
            blitPassthrough(colorTex, depthTex);
            prevVP = new Matrix4f(currVP);
            historyValid = true;
            return;
        }

        // --- Normal motion blur path ---
        if (prevVP == null) prevVP = new Matrix4f(currVP);

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        shader.use();

        Matrix4f invVP = new Matrix4f(currVP).invert();

        if (!isMatrixValid(prevVP)) {
            prevVP = new Matrix4f(currVP);
        }

        shader.setUniformMatrix4fv("uInvVP",  false, ShaderProgram.matrixToBuffer(invVP));
        shader.setUniformMatrix4fv("uPrevVP", false, ShaderProgram.matrixToBuffer(prevVP));

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        shader.setUniform1i("uColorTex", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        shader.setUniform1i("uDepthTex", 1);

        shader.setUniform1f("uBlurStrength", blurStrength);
        shader.setUniform1i("uSamples",      samples);
        shader.setUniform1f("uNear",         0.1f);
        shader.setUniform1f("uFar",          1000f);

        shader.setUniform1i("uDebugMode", debugMode);

        quad.draw();
        shader.unbind();

        // Update history AFTER blur consumed prevVP
        prevVP = new Matrix4f(currVP);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Copies colorTex to the current write FBO unchanged.
     * Used for warmup frames — guarantees the output is never black.
     */
    private void blitPassthrough(int colorTex, int depthTex) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        shader.use();

        // With strength=0 and samples=1 the shader just samples colorTex
        // at the current UV with zero velocity offset — a clean copy.
        Matrix4f identity = new Matrix4f();
        shader.setUniformMatrix4fv("uInvVP",  false, ShaderProgram.matrixToBuffer(identity));
        shader.setUniformMatrix4fv("uPrevVP", false, ShaderProgram.matrixToBuffer(identity));

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        shader.setUniform1i("uColorTex", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        shader.setUniform1i("uDepthTex", 1);

        shader.setUniform1f("uBlurStrength", 0f);
        shader.setUniform1i("uSamples",      1);
        shader.setUniform1f("uNear",         0.1f);
        shader.setUniform1f("uFar",          1000f);

        quad.draw();
        shader.unbind();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // In MotionBlurEffect.java — add this helper
    private static boolean isMatrixValid(Matrix4f m) {
        float det = m.determinant();
        if (Math.abs(det) < 1e-6f) {
            System.err.println("[MOTIONBLUR] Rejected matrix: det=" + det);
            return false;
        }
        float[] arr = new float[16];
        m.get(arr);
        for (float v : arr) {
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