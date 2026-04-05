package net.twelfthengine.renderer.postprocess;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class PostProcessPipeline {

    private final PostProcessFbo fboA;
    private final PostProcessFbo fboB;
    private final List<PostProcessEffect> effects = new ArrayList<>();

    // Fullscreen blit shader
    private int blitProgram;
    private int blitVao;
    private int blitVbo;

    public PostProcessPipeline(int width, int height) {
        fboA = new PostProcessFbo(width, height);
        fboB = new PostProcessFbo(width, height);
        initBlitShader();
    }

    // In PostProcessPipeline.java — add this getter:
    public int getSceneColorTexId() { return fboA.getColorTexId(); }
    public int getSceneTexWidth()   { return fboA.width(); }
    public int getSceneTexHeight()  { return fboA.height(); }

    public int getFboAId() { return fboA.fboId(); }

    private void initBlitShader() {
        // Vertex shader — generates a fullscreen triangle from gl_VertexID,
        // no VBO needed, but we bind a dummy VAO to satisfy core profile.
        String vert = """
                #version 330 core
                out vec2 uv;
                void main() {
                    // Triangle trick: covers [-1,1] clip space with 3 vertices
                    vec2 pos = vec2(
                        (gl_VertexID == 1) ? 4.0 : -1.0,
                        (gl_VertexID == 2) ? 4.0 : -1.0
                    );
                    uv = pos * 0.5 + 0.5;
                    gl_Position = vec4(pos, 0.0, 1.0);
                }
                """;

        String frag = """
                #version 330 core
                in  vec2 uv;
                out vec4 fragColor;
                uniform sampler2D uScene;
                void main() {
                    fragColor = texture(uScene, uv);
                }
                """;

        int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(v, vert);
        GL20.glCompileShader(v);
        checkShader(v, "Blit vertex");

        int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(f, frag);
        GL20.glCompileShader(f);
        checkShader(f, "Blit fragment");

        blitProgram = GL20.glCreateProgram();
        GL20.glAttachShader(blitProgram, v);
        GL20.glAttachShader(blitProgram, f);
        GL20.glLinkProgram(blitProgram);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);

        // Dummy VAO (required in core profile even with no attributes)
        blitVao = org.lwjgl.opengl.GL30.glGenVertexArrays();
    }

    private void checkShader(int id, String name) {
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(name + " shader error:\n" + GL20.glGetShaderInfoLog(id));
        }
    }

    public void addEffect(PostProcessEffect effect) {
        effects.add(effect);
    }

    /** Bind before rendering your scene. */
    public void bind() {
        fboA.bind();
    }

    /** Call after scene rendering to apply effects and blit to screen. */
    public void present() {
        PostProcessFbo readFbo  = fboA;
        PostProcessFbo writeFbo = fboB;

        for (PostProcessEffect effect : effects) {
            writeFbo.bind();

            // Clear writeFbo before applying effect
            GL11.glClearColor(0f, 0f, 0f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            effect.apply(readFbo.getColorTexId(), readFbo.getDepthTexId());

            // Log what effect wrote
            /* THIS WAS FIXING IT WHY EVER
            java.nio.IntBuffer pb = org.lwjgl.BufferUtils.createIntBuffer(4);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, writeFbo.fboId());
            GL11.glReadPixels(writeFbo.width()/2, writeFbo.height()/2, 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pb);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);

             */
            GL11.glFinish();
            writeFbo.bind();

            PostProcessFbo temp = readFbo;
            readFbo  = writeFbo;
            writeFbo = temp;
        }

        blitToScreen(readFbo);
    }

    private void blitToScreen(PostProcessFbo fbo) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, fbo.width(), fbo.height());

        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL20.glUseProgram(blitProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(blitProgram, "uScene"), 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.getColorTexId());

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        org.lwjgl.opengl.GL30.glBindVertexArray(blitVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        org.lwjgl.opengl.GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);

        // ---------------------------------------------------------------
        // Restore 2D ortho state for all subsequent UI / debug drawing
        // ---------------------------------------------------------------
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        // Standard top-left origin ortho matching your Renderer2D convention
        GL11.glOrtho(0, fbo.width(), fbo.height(), 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glColor4f(1f, 1f, 1f, 1f); // reset fixed-function color
    }

    public void dispose() {
        fboA.dispose();
        fboB.dispose();
        effects.forEach(PostProcessEffect::dispose);
        GL20.glDeleteProgram(blitProgram);
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(blitVao);
    }
}