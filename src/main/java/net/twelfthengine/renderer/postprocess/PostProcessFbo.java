package net.twelfthengine.renderer.postprocess;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public class PostProcessFbo {

  private final int fboId;
  private final int colorTexId;
  private final int depthTexId;
  private final int width;
  private final int height;

  public int fboId() {
    return fboId;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public PostProcessFbo(int width, int height) {
    this.width = width;
    this.height = height;

    fboId = GL30.glGenFramebuffers();
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);

    // Color texture
    colorTexId = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexId);
    GL11.glTexImage2D(
        GL11.GL_TEXTURE_2D,
        0,
        GL11.GL_RGBA8, // <-- was GL_RGB
        width,
        height,
        0,
        GL11.GL_RGBA,
        GL11.GL_UNSIGNED_BYTE, // <-- was GL_RGB
        (java.nio.ByteBuffer) null);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL30.glFramebufferTexture2D(
        GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexId, 0);

    // Depth texture (needed for fog and motion blur reconstruction)
    depthTexId = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexId);
    GL11.glTexImage2D(
        GL11.GL_TEXTURE_2D,
        0,
        GL14.GL_DEPTH_COMPONENT32,
        width,
        height,
        0,
        GL11.GL_DEPTH_COMPONENT,
        GL11.GL_FLOAT,
        (java.nio.ByteBuffer) null);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    GL30.glFramebufferTexture2D(
        GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexId, 0);

    if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
      throw new RuntimeException("PostProcessFbo is not complete!");
    }

    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
  }

  public void bind() {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
    GL11.glViewport(0, 0, width, height);
  }

  public void unbind() {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
  }

  public int getColorTexId() {
    return colorTexId;
  }

  public int getDepthTexId() {
    return depthTexId;
  }

  public void dispose() {
    GL30.glDeleteFramebuffers(fboId);
    GL11.glDeleteTextures(colorTexId);
    GL11.glDeleteTextures(depthTexId);
  }
}
