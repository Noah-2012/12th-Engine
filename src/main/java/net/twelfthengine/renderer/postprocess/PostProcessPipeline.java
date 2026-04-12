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

  // FIX: Cache the uniform location once after linking — glGetUniformLocation
  //      is a driver round-trip and must not be called every frame.
  private int uSceneLocation;

  public PostProcessPipeline(int width, int height) {
    fboA = new PostProcessFbo(width, height);
    fboB = new PostProcessFbo(width, height);
    initBlitShader();
  }

  public List<PostProcessEffect> getEffects() {
    return effects;
  }

  public int getSceneColorTexId() {
    return fboA.getColorTexId();
  }

  public int getSceneTexWidth() {
    return fboA.width();
  }

  public int getSceneTexHeight() {
    return fboA.height();
  }

  public int getFboAId() {
    return fboA.fboId();
  }

  private void initBlitShader() {
    // Vertex shader — generates a fullscreen triangle from gl_VertexID.
    // No VBO needed; a dummy VAO satisfies the core profile requirement.
    String vert =
        """
            #version 330 core
            out vec2 uv;
            void main() {
                vec2 pos = vec2(
                    (gl_VertexID == 1) ? 4.0 : -1.0,
                    (gl_VertexID == 2) ? 4.0 : -1.0
                );
                uv = pos * 0.5 + 0.5;
                gl_Position = vec4(pos, 0.0, 1.0);
            }
            """;

    String frag =
        """
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

    // FIX: Query and cache the uniform location once — never at draw time.
    uSceneLocation = GL20.glGetUniformLocation(blitProgram, "uScene");

    // Dummy VAO (required in core profile even with no vertex attributes)
    blitVao = GL30.glGenVertexArrays();
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

  /**
   * Call after scene rendering to apply all effects in sequence and blit the final result to the
   * default framebuffer.
   *
   * <p>Ping-pong strategy: fboA holds the scene after {@link #bind()} / scene render. Each effect
   * reads from {@code readFbo} and writes to {@code writeFbo}; the two are then swapped. After the
   * loop the last written result sits in {@code readFbo} and is blit to the screen.
   */
  public void present() {
    PostProcessFbo readFbo = fboA;
    PostProcessFbo writeFbo = fboB;

    for (PostProcessEffect effect : effects) {
      // Bind the write target and clear only the color buffer.
      // FIX: Do NOT clear GL_DEPTH_BUFFER_BIT — post-process passes never
      //      need the depth buffer, and clearing it wastes GPU bandwidth.
      writeFbo.bind();
      GL11.glClearColor(0f, 0f, 0f, 1f);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

      effect.apply(readFbo.getColorTexId(), readFbo.getDepthTexId());

      // FIX: Removed the redundant second writeFbo.bind() that appeared after
      //      the apply() call — writeFbo is already bound, and calling bind()
      //      again just issues an unnecessary glBindFramebuffer + glViewport.

      // Swap ping-pong buffers.
      PostProcessFbo temp = readFbo;
      readFbo = writeFbo;
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

    // FIX: Use the cached location — no driver round-trip.
    GL20.glUniform1i(uSceneLocation, 0);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.getColorTexId());

    // FIX: Removed the duplicate glDisable(GL_DEPTH_TEST) that immediately
    //      followed this one — it was dead code.
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    GL30.glBindVertexArray(blitVao);
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    GL30.glBindVertexArray(0);

    GL20.glUseProgram(0);

    // Restore 2D ortho state for all subsequent UI / debug drawing.
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    GL11.glMatrixMode(GL11.GL_PROJECTION);
    GL11.glLoadIdentity();
    GL11.glOrtho(0, fbo.width(), fbo.height(), 0, -1, 1);

    GL11.glMatrixMode(GL11.GL_MODELVIEW);
    GL11.glLoadIdentity();

    GL11.glColor4f(1f, 1f, 1f, 1f);
  }

  public void dispose() {
    fboA.dispose();
    fboB.dispose();
    effects.forEach(PostProcessEffect::dispose);
    GL20.glDeleteProgram(blitProgram);
    GL30.glDeleteVertexArrays(blitVao);
  }
}
