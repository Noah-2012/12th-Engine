package net.twelfthengine.renderer.shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

public class ShaderProgram {

  private final int programId;

  // Uniform location cache — glGetUniformLocation is a driver string-lookup,
  // so we pay that cost only once per name and store the result.
  private final Map<String, Integer> uniformCache = new HashMap<>();

  // FIX: One shared FloatBuffer for all matrix uploads.
  //      matrixToBuffer() previously allocated a NEW off-heap FloatBuffer on
  //      every call — with two matrix uploads per frame in MotionBlurEffect
  //      alone that was two garbage-collected off-heap allocations per frame.
  //      A single 16-float buffer reused via clear() is sufficient because
  //      glUniformMatrix4fv copies the data synchronously before returning.
  private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

  public ShaderProgram(String vertexResource, String fragmentResource) throws IOException {
    int vs = compile(GL20.GL_VERTEX_SHADER,   loadSource(vertexResource));
    int fs = compile(GL20.GL_FRAGMENT_SHADER, loadSource(fragmentResource));

    programId = GL20.glCreateProgram();
    GL20.glAttachShader(programId, vs);
    GL20.glAttachShader(programId, fs);
    GL20.glBindAttribLocation(programId, 0, "aPos");
    GL20.glLinkProgram(programId);

    if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
      throw new RuntimeException("Shader link failed: " + GL20.glGetProgramInfoLog(programId));
    }

    GL20.glDeleteShader(vs);
    GL20.glDeleteShader(fs);
  }

  // =============================
  // SOURCE LOADING & COMPILATION
  // =============================

  private static String loadSource(String resourcePath) throws IOException {
    InputStream in = ShaderProgram.class.getResourceAsStream(resourcePath);
    if (in == null) throw new IOException("Shader not found: " + resourcePath);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  private static int compile(int type, String source) {
    int id = GL20.glCreateShader(type);
    GL20.glShaderSource(id, source);
    GL20.glCompileShader(id);
    if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
      throw new RuntimeException("Shader compile failed: " + GL20.glGetShaderInfoLog(id));
    }
    return id;
  }

  // =============================
  // BIND / UNBIND
  // =============================

  public void use()   { GL20.glUseProgram(programId); }
  public void unbind(){ GL20.glUseProgram(0); }

  // =============================
  // ACCESSORS
  // =============================

  public int getProgramId() { return programId; }

  // =============================
  // UNIFORM LOCATION (cached)
  // =============================

  public int getUniformLocation(String name) {
    return uniformCache.computeIfAbsent(name, n -> GL20.glGetUniformLocation(programId, n));
  }

  // =============================
  // UNIFORM SETTERS
  // =============================

  public void setUniform1i(String name, int v) {
    GL20.glUniform1i(getUniformLocation(name), v);
  }

  public void setUniform1f(String name, float v) {
    GL20.glUniform1f(getUniformLocation(name), v);
  }

  public void setUniform2f(String name, float x, float y) {
    GL20.glUniform2f(getUniformLocation(name), x, y);
  }

  public void setUniform3f(String name, float x, float y, float z) {
    GL20.glUniform3f(getUniformLocation(name), x, y, z);
  }

  public void setUniform4f(String name, float x, float y, float z, float w) {
    GL20.glUniform4f(getUniformLocation(name), x, y, z, w);
  }

  /**
   * Uploads a 4×4 matrix to a named uniform.
   *
   * <p>FIX: Uses the instance-level cached {@link #matrixBuffer} instead of
   * allocating a new {@code FloatBuffer} on every call. Safe because
   * {@code glUniformMatrix4fv} copies the buffer contents synchronously —
   * the driver does not hold a reference to the buffer after returning.
   */
  public void setUniformMatrix4fv(String name, boolean transpose, Matrix4f matrix) {
    matrixBuffer.clear();
    matrix.get(matrixBuffer);
    GL20.glUniformMatrix4fv(getUniformLocation(name), transpose, matrixBuffer);
  }

  /**
   * Raw overload for callers that already have a {@link FloatBuffer} (e.g. from
   * a cached field in an effect class). Prefer {@link #setUniformMatrix4fv(String, boolean, Matrix4f)}.
   */
  public void setUniformMatrix4fv(String name, boolean transpose, FloatBuffer buffer) {
    GL20.glUniformMatrix4fv(getUniformLocation(name), transpose, buffer);
  }

  // =============================
  // CLEANUP
  // =============================

  public void delete() {
    GL20.glDeleteProgram(programId);
  }

  // =============================
  // DEPRECATED
  // =============================

  /**
   * @deprecated Allocates a new off-heap {@link FloatBuffer} on every call.
   *             Use {@link #setUniformMatrix4fv(String, boolean, Matrix4f)} instead,
   *             which reuses a cached buffer internally.
   */
  @Deprecated
  public static FloatBuffer matrixToBuffer(Matrix4f m) {
    FloatBuffer fb = BufferUtils.createFloatBuffer(16);
    m.get(fb);
    fb.rewind();
    return fb;
  }
}