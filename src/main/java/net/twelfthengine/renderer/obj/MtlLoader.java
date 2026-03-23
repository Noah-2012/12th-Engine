package net.twelfthengine.renderer.obj;

import java.io.*;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.texture.TextureLoader;

public class MtlLoader {
  public static void load(String resourcePath, ObjModel model) throws IOException {
    InputStream in = MtlLoader.class.getResourceAsStream(resourcePath);
    if (in == null) {
      System.err.println("[MtlLoader] Konnte MTL nicht finden: " + resourcePath);
      return;
    }

    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    String line;
    ObjModel.Material currentMat = null;

    while ((line = reader.readLine()) != null) {
      String[] tokens = line.trim().split("\\s+");
      if (tokens.length < 2) continue;

      if (tokens[0].equals("newmtl")) {
        currentMat = new ObjModel.Material();
        model.materials.put(tokens[1], currentMat);
      } else if (currentMat != null) {
        if (tokens[0].equals("Kd")) {
          currentMat.diffuseColor =
              new Vec3(
                  Float.parseFloat(tokens[1]),
                  Float.parseFloat(tokens[2]),
                  Float.parseFloat(tokens[3]));
        }
      }
      // In MtlLoader.java in der Schleife:
      if (tokens[0].equals("map_Kd")) {
        String textureFile = tokens[1];
        // Wir nehmen den Ordnerpfad der MTL und hängen den Dateinamen der Textur an
        String folder = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
        String fullTexturePath = folder + textureFile;

        System.out.println("[MtlLoader] Lade Textur: " + fullTexturePath);
        currentMat.textureID = TextureLoader.loadTexture(fullTexturePath);
      }
    }
    reader.close();
  }
}
