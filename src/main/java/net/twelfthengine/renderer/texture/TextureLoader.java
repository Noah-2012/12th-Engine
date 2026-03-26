package net.twelfthengine.renderer.texture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.twelfthengine.core.resources.TwelfthPackage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class TextureLoader {

  /** Loads a texture from a standard file path. */
  public static int loadTexture(String path) {
    try {
      // Datei in Buffer laden (Original behavior)
      ByteBuffer rawData = ResourceUtils.ioResourceToByteBuffer(path);
      return loadTextureFromByteBuffer(rawData);
    } catch (Exception e) {
      System.err.println("Texture Fehler: " + e.getMessage());
      return -1;
    }
  }

  /** Loads a texture directly from a custom TwelfthPackage binary archive (.twm / .twa). */
  public static int loadTexture(TwelfthPackage pack, String internalPath) {
    byte[] data = pack.getFileData(internalPath);
    if (data == null) {
      System.err.println("Texture Fehler: File not found in archive -> " + internalPath);
      return -1;
    }

    // STBImage requires a direct ByteBuffer, so we must allocate native memory
    ByteBuffer directBuffer = MemoryUtil.memAlloc(data.length);
    try {
      directBuffer.put(data).flip();
      return loadTextureFromByteBuffer(directBuffer);
    } finally {
      // Free the native memory once the texture is sent to the GPU
      MemoryUtil.memFree(directBuffer);
    }
  }

  /** Core logic to decode an image from a direct ByteBuffer and send it to OpenGL. */
  private static int loadTextureFromByteBuffer(ByteBuffer rawData) {
    int textureID;
    ByteBuffer image = null;

    try (MemoryStack stack = MemoryStack.stackPush()) {
      IntBuffer w = stack.mallocInt(1);
      IntBuffer h = stack.mallocInt(1);
      IntBuffer channels = stack.mallocInt(1);

      // STBImage dekodiert das Bild (PNG, JPG, etc.)
      STBImage.stbi_set_flip_vertically_on_load(true); // Korrigiert UV-Ausrichtung
      image = STBImage.stbi_load_from_memory(rawData, w, h, channels, 4);

      if (image == null) {
        throw new RuntimeException(
            "STBImage konnte Textur nicht laden: " + STBImage.stbi_failure_reason());
      }

      // OpenGL ID generieren
      textureID = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);

      // Performance-Parameter
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

      // Daten an GPU schicken
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D,
          0,
          GL11.GL_RGBA,
          w.get(0),
          h.get(0),
          0,
          GL11.GL_RGBA,
          GL11.GL_UNSIGNED_BYTE,
          image);
    } catch (Exception e) {
      System.err.println("Texture Fehler: " + e.getMessage());
      return -1;
    } finally {
      if (image != null) {
        STBImage.stbi_image_free(image);
      }
    }

    return textureID;
  }
}
