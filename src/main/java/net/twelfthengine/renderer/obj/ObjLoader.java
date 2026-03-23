package net.twelfthengine.renderer.obj;

import net.twelfthengine.math.Vec2f;
import net.twelfthengine.math.Vec3;
import java.io.*;

public class ObjLoader {
    public static ObjModel load(String resourcePath) throws IOException {
        ObjModel model = new ObjModel();

        // Lade die Datei als Stream aus den Resources
        InputStream in = ObjLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Resource nicht gefunden: " + resourcePath);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String currentMaterial = "default";
        String line;

        while ((line = reader.readLine()) != null) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length < 2) continue;

            switch (tokens[0]) {
                case "v":  model.vertices.add(new Vec3(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3]))); break;
                case "vn": model.normals.add(new Vec3(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3]))); break;
                case "vt": model.uvs.add(new Vec2f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]))); break;
                case "mtllib":
                    String folder = "";
                    int lastSlash = resourcePath.lastIndexOf('/');
                    if (lastSlash != -1) folder = resourcePath.substring(0, lastSlash + 1);

                    MtlLoader.load(folder + tokens[1], model);
                    break;
                case "usemtl":
                    currentMaterial = tokens[1];
                    break;
                case "f":
                    parseFace(model, tokens, currentMaterial);
                    break;
            }
        }
        reader.close();

        // Calculate bounds after loading is complete
        model.calculateBounds();

        return model;
    }

    private static void parseFace(ObjModel model, String[] tokens, String matName) {
        int vCount = tokens.length - 1;
        if (vCount == 3) {
            model.faces.add(buildFace(tokens[1], tokens[2], tokens[3], matName));
        } else if (vCount == 4) {
            // Quad-Support: Splitting in zwei Dreiecke
            model.faces.add(buildFace(tokens[1], tokens[2], tokens[3], matName));
            model.faces.add(buildFace(tokens[1], tokens[3], tokens[4], matName));
        }
    }

    private static ObjModel.Face buildFace(String v1, String v2, String v3, String matName) {
        ObjModel.Face face = new ObjModel.Face();
        face.materialName = matName;
        String[] vns = {v1, v2, v3};
        for (int i = 0; i < 3; i++) {
            String[] parts = vns[i].split("/");
            face.vertexIndices[i] = Integer.parseInt(parts[0]) - 1;
            if (parts.length > 1 && !parts[1].isEmpty()) {
                face.uvIndices[i] = Integer.parseInt(parts[1]) - 1;
                face.hasUVs = true;
            }
            if (parts.length > 2 && !parts[2].isEmpty()) {
                face.normalIndices[i] = Integer.parseInt(parts[2]) - 1;
                face.hasNormals = true;
            }
        }
        return face;
    }
}
