package net.twelfthengine.renderer.postprocess;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public abstract class BasePostProcessEffect implements PostProcessEffect {

  private boolean enabled = true;

  // Shared fullscreen copy shader (für disabled effects)
  private static int copyProgram = -1;
  private static int copyVao = -1;

  static {
    initCopyShader();
  }

  // ------------------------------------------------------------
  // Copy shader erstellen (Fullscreen Triangle)
  // ------------------------------------------------------------
  private static void initCopyShader() {
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
            in vec2 uv;
            out vec4 fragColor;
            uniform sampler2D uTex;
            void main() {
                fragColor = texture(uTex, uv);
            }
        """;

    int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
    GL20.glShaderSource(v, vert);
    GL20.glCompileShader(v);
    checkShader(v, "Copy vertex");

    int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
    GL20.glShaderSource(f, frag);
    GL20.glCompileShader(f);
    checkShader(f, "Copy fragment");

    copyProgram = GL20.glCreateProgram();
    GL20.glAttachShader(copyProgram, v);
    GL20.glAttachShader(copyProgram, f);
    GL20.glLinkProgram(copyProgram);

    GL20.glDeleteShader(v);
    GL20.glDeleteShader(f);

    copyVao = GL30.glGenVertexArrays();
  }

  private static void checkShader(int id, String name) {
    if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
      throw new RuntimeException(name + " shader error:\n" + GL20.glGetShaderInfoLog(id));
    }
  }

  // ------------------------------------------------------------
  // APPLY LOGIC (mit Pass-Through Fix)
  // ------------------------------------------------------------
  @Override
  public final void apply(int colorTex, int depthTex) {
    if (!enabled) {
      blit(colorTex); // 🔥 WICHTIG: Bild weiterreichen!
      return;
    }
    applyEffect(colorTex, depthTex);
  }

  // ------------------------------------------------------------
  // Fullscreen Copy (PassThrough)
  // ------------------------------------------------------------
  private void blit(int tex) {
    GL11.glDisable(GL11.GL_DEPTH_TEST);

    GL20.glUseProgram(copyProgram);
    GL20.glUniform1i(GL20.glGetUniformLocation(copyProgram, "uTex"), 0);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

    GL30.glBindVertexArray(copyVao);
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    GL30.glBindVertexArray(0);

    GL20.glUseProgram(0);
  }

  // ------------------------------------------------------------
  // Von Effekten implementieren
  // ------------------------------------------------------------
  protected abstract void applyEffect(int colorTex, int depthTex);

  // ------------------------------------------------------------
  // Enable / Disable
  // ------------------------------------------------------------
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void dispose() {
    // optional überschreiben
  }
}
