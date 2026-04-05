package net.twelfthengine.renderer.postprocess;

public interface PostProcessEffect {
    /** Apply the effect. Read from inputColorTex/inputDepthTex, write to the currently bound FBO. */
    void apply(int inputColorTex, int inputDepthTex);

    /** Free GPU resources. */
    void dispose();
}