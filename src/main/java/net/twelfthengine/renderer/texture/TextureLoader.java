package net.twelfthengine.renderer.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class TextureLoader {
    public static int loadTexture(String path) {
        int textureID;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // Datei in Buffer laden
            ByteBuffer rawData = ResourceUtils.ioResourceToByteBuffer(path);

            // STBImage dekodiert das Bild (PNG, JPG, etc.)
            STBImage.stbi_set_flip_vertically_on_load(true); // Korrigiert UV-Ausrichtung
            ByteBuffer image = STBImage.stbi_load_from_memory(rawData, w, h, channels, 4);

            if (image == null) {
                throw new RuntimeException("STBImage konnte Textur nicht laden: " + STBImage.stbi_failure_reason());
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
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w.get(), h.get(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);

            STBImage.stbi_image_free(image);
        } catch (Exception e) {
            System.err.println("Texture Fehler: " + e.getMessage());
            return -1;
        }

        return textureID;
    }
}