package net.twelfthengine.renderer.shader;

import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.lwjgl.BufferUtils;

public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public ShaderProgram(String vertexResource, String fragmentResource) throws IOException {
        int vs = compile(GL20.GL_VERTEX_SHADER, loadSource(vertexResource));
        int fs = compile(GL20.GL_FRAGMENT_SHADER, loadSource(fragmentResource));
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vs);
        GL20.glAttachShader(programId, fs);
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Shader link failed: " + GL20.glGetProgramInfoLog(programId));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private static String loadSource(String resourcePath) throws IOException {
        InputStream in = ShaderProgram.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Shader not found: " + resourcePath);
        }
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

    public void use() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        return uniformCache.computeIfAbsent(name, n -> GL20.glGetUniformLocation(programId, n));
    }

    public void setUniformMatrix4fv(String name, boolean transpose, FloatBuffer buffer) {
        GL20.glUniformMatrix4fv(getUniformLocation(name), transpose, buffer);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        GL20.glUniform3f(getUniformLocation(name), x, y, z);
    }

    public void setUniform1i(String name, int v) {
        GL20.glUniform1i(getUniformLocation(name), v);
    }

    public static FloatBuffer matrixToBuffer(org.joml.Matrix4f m) {
        FloatBuffer fb = BufferUtils.createFloatBuffer(16);
        m.get(fb);
        fb.rewind();
        return fb;
    }

    public void delete() {
        GL20.glDeleteProgram(programId);
    }
}
