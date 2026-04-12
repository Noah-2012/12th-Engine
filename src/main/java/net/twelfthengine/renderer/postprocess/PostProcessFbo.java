package net.twelfthengine.renderer.postprocess;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public class PostProcessFbo {

  private final int fboId;
  private final int colorTexId;
  private final int depthTexId;
  private final int width;
  private final int height;

  /**
   * Creates a post-process FBO with sensible defaults:
   * <ul>
   *   <li>Color: {@code GL_RGBA8} / {@code GL_UNSIGNED_BYTE} — sufficient for LDR effects.</li>
   *   <li>Depth: {@code GL_DEPTH_COMPONENT24} — faster than 32-bit on most hardware and
   *       accurate enough for all standard post-process depth reads (fog, DoF, etc.).</li>
   * </ul>
   *
   * <p>For HDR pipelines, use {@link #PostProcessFbo(int, int, int, int)} and pass
   * {@code GL_RGBA16F} / {@code GL_FLOAT} as the color format arguments.
   */
  public PostProcessFbo(int width, int height) {
    // GL_DEPTH_COMPONENT24 chosen over 32 for better fill-rate on most GPUs.
    // Swap to GL_DEPTH_COMPONENT32 if you need extreme far-plane precision.
    this(width, height, GL30.GL_RGBA8, GL14.GL_DEPTH_COMPONENT24);
  }

  /**
   * Full-control constructor — lets you opt into HDR ({@code GL_RGBA16F}) or
   * higher-precision depth ({@code GL_DEPTH_COMPONENT32}) without changing the default path.
   *
   * @param colorInternalFormat e.g. {@code GL30.GL_RGBA8} or {@code GL30.GL_RGBA16F}
   * @param depthInternalFormat e.g. {@code GL14.GL_DEPTH_COMPONENT24} or {@code GL14.GL_DEPTH_COMPONENT32}
   */
  public PostProcessFbo(int width, int height, int colorInternalFormat, int depthInternalFormat) {
    this.width  = width;
    this.height = height;

    fboId = GL30.glGenFramebuffers();
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);

    // ---------------------------------------------------------------
    // Color attachment
    // FIX: Added GL_CLAMP_TO_EDGE on both axes.  Without this, UV sampling
    //      at or beyond the [0,1] boundary (which happens with some blur
    //      kernels) wraps around and bleeds the opposite screen edge into
    //      the result, producing thin coloured lines at screen borders.
    // ---------------------------------------------------------------
    colorTexId = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexId);

    // Determine the correct pixel transfer format from the internal format.
    boolean isHdr = (colorInternalFormat == GL30.GL_RGBA16F
            || colorInternalFormat == GL30.GL_RGBA32F);
    int pixelType   = isHdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;

    GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0,
            colorInternalFormat,
            width, height, 0,
            GL11.GL_RGBA, pixelType,
            (ByteBuffer) null);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexId, 0);

    // ---------------------------------------------------------------
    // Depth attachment
    // FIX: Added GL_CLAMP_TO_EDGE for the same reason as the color texture —
    //      depth-based effects (fog, DoF) that sample near screen edges
    //      should not wrap to the opposite side.
    // ---------------------------------------------------------------
    depthTexId = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexId);
    GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0,
            depthInternalFormat,
            width, height, 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT,
            (ByteBuffer) null);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexId, 0);

    // Validate once at construction — never in the render loop.
    int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
      throw new RuntimeException(
              "PostProcessFbo is not complete! status=0x" + Integer.toHexString(status));
    }

    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
  }

  // =============================
  // ACCESSORS
  // =============================

  public int fboId()       { return fboId;       }
  public int width()       { return width;        }
  public int height()      { return height;       }
  public int getColorTexId() { return colorTexId; }
  public int getDepthTexId() { return depthTexId; }

  // =============================
  // BIND / UNBIND
  // =============================

  public void bind() {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
    GL11.glViewport(0, 0, width, height);
  }

  public void unbind() {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
  }

  // =============================
  // CLEANUP
  // =============================

  public void dispose() {
    GL30.glDeleteFramebuffers(fboId);
    GL11.glDeleteTextures(colorTexId);
    GL11.glDeleteTextures(depthTexId);
  }
}