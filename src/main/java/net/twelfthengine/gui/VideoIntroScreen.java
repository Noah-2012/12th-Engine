package net.twelfthengine.gui;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import net.twelfthengine.core.resources.ResourceExtractor;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.window.Window;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class VideoIntroScreen {

  private final Window window;
  private final Renderer2D renderer;
  private final TextRenderer textRenderer;
  private FFmpegFrameGrabber grabber;
  private boolean playing = true;
  private int videoTexture;

  public VideoIntroScreen(
      Window window, Renderer2D renderer, TextRenderer textRenderer, String resourcePath)
      throws Exception {
    this.window = window;
    this.renderer = renderer;
    this.textRenderer = textRenderer;

    String filePath = ResourceExtractor.extract(resourcePath);

    grabber = new FFmpegFrameGrabber(filePath);
    grabber.start();

    videoTexture = GL11.glGenTextures();
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, videoTexture);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
  }

  public boolean isPlaying() {
    return playing;
  }

  public void update(float deltaTime) throws Exception {
    if (!playing) {
      return;
    }

    Frame frame = grabber.grabImage();
    if (frame == null) {
      playing = false;
      grabber.stop();
      return;
    }

    // Convert frame to BufferedImage
    Java2DFrameConverter converter = new Java2DFrameConverter();
    BufferedImage image = converter.convert(frame);
    int width = image.getWidth();
    int height = image.getHeight();

    // Convert BufferedImage to byte buffer (RGB)
    ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 3);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = image.getRGB(x, y);
        buffer.put((byte) ((rgb >> 16) & 0xFF)); // R
        buffer.put((byte) ((rgb >> 8) & 0xFF)); // G
        buffer.put((byte) (rgb & 0xFF)); // B
      }
    }
    buffer.flip();

    GL11.glBindTexture(GL11.GL_TEXTURE_2D, videoTexture);
    GL11.glTexImage2D(
        GL11.GL_TEXTURE_2D,
        0,
        GL11.GL_RGB,
        width,
        height,
        0,
        GL11.GL_RGB,
        GL11.GL_UNSIGNED_BYTE,
        buffer);
  }

  public void render() {
    if (!playing) return;

    // Render direkt auf Default Framebuffer
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    GL11.glViewport(0, 0, window.getWidth(), window.getHeight());
    GL11.glClearColor(0f, 0f, 0f, 1f);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    renderer.begin2D();
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, videoTexture);

    int sw = window.getWidth();
    int sh = window.getHeight();

    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2f(0f, 0f); GL11.glVertex2i(0, 0);
    GL11.glTexCoord2f(1f, 0f); GL11.glVertex2i(sw, 0);
    GL11.glTexCoord2f(1f, 1f); GL11.glVertex2i(sw, sh);
    GL11.glTexCoord2f(0f, 1f); GL11.glVertex2i(0, sh);
    GL11.glEnd();

    GL11.glDisable(GL11.GL_TEXTURE_2D);
    renderer.end2D();
  }
}
