package net.twelfthengine.renderer.obj;

import java.util.*;
import net.twelfthengine.math.Vec2f;
import net.twelfthengine.math.Vec3;

public class ObjModel {
  public final List<Vec3> vertices = new ArrayList<>();
  public final List<Vec3> normals = new ArrayList<>();
  public final List<Vec2f> uvs = new ArrayList<net.twelfthengine.math.Vec2f>();
  public final List<Face> faces = new ArrayList<>();
  public final Map<String, Material> materials = new HashMap<>();

  // Bounding information for physics
  public Vec3 minBounds = new Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
  public Vec3 maxBounds = new Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
  public Vec3 center = new Vec3(0, 0, 0);
  public float boundingRadius = 0.0f;

  public static class Face {
    public int[] vertexIndices = new int[3];
    public int[] normalIndices = new int[3];
    public int[] uvIndices = new int[3];
    public String materialName = "default";
    public boolean hasNormals = false;
    public boolean hasUVs = false;
  }

  public static class Material {
    public Vec3 diffuseColor = new Vec3(1, 1, 1);
    public int textureID = -1;
  }

  // Calculate bounding information after loading
  public void calculateBounds() {
    if (vertices.isEmpty()) return;

    // Reset bounds
    minBounds = new Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    maxBounds = new Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

    // Find min/max bounds
    for (Vec3 vertex : vertices) {
      minBounds =
          new Vec3(
              Math.min(minBounds.x(), vertex.x()),
              Math.min(minBounds.y(), vertex.y()),
              Math.min(minBounds.z(), vertex.z()));
      maxBounds =
          new Vec3(
              Math.max(maxBounds.x(), vertex.x()),
              Math.max(maxBounds.y(), vertex.y()),
              Math.max(maxBounds.z(), vertex.z()));
    }

    // Calculate center
    center =
        new Vec3(
            (minBounds.x() + maxBounds.x()) / 2.0f,
            (minBounds.y() + maxBounds.y()) / 2.0f,
            (minBounds.z() + maxBounds.z()) / 2.0f);

    // Calculate bounding radius (distance from center to farthest vertex)
    float maxDistanceSq = 0;
    for (Vec3 vertex : vertices) {
      float dx = vertex.x() - center.x();
      float dy = vertex.y() - center.y();
      float dz = vertex.z() - center.z();
      float distanceSq = dx * dx + dy * dy + dz * dz;
      maxDistanceSq = Math.max(maxDistanceSq, distanceSq);
    }
    boundingRadius = (float) Math.sqrt(maxDistanceSq);
  }

  // Get axis-aligned bounding box
  public Vec3 getMinBounds() {
    return minBounds;
  }

  public Vec3 getMaxBounds() {
    return maxBounds;
  }

  public Vec3 getCenter() {
    return center;
  }

  public float getBoundingRadius() {
    return boundingRadius;
  }
}
