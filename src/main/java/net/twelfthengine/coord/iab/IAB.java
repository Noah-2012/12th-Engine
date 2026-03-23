package net.twelfthengine.coord.iab;

import net.twelfthengine.math.Vec3;

public class IAB {

  private Vec3 center; // Nullpunkt der Box
  private float width, height, depth;

  public IAB(float width, float height, float depth) {
    this.width = width;
    this.height = height;
    this.depth = depth;
    this.center = new Vec3(width / 2f, height / 2f, depth / 2f); // Mitte der Box = 0,0,0
  }

  public Vec3 getCenter() {
    return center;
  }

  // Umrechnung von Weltkoordinaten -> relativ zur Box
  public Vec3 toLocal(Vec3 worldPos) {
    return new Vec3(
        worldPos.x() - center.x(), worldPos.y() - center.y(), worldPos.z() - center.z());
  }

  public Vec3 toWorld(Vec3 localPos) {
    return new Vec3(
        localPos.x() + center.x(), localPos.y() + center.y(), localPos.z() + center.z());
  }

  public float getWidth() {
    return width;
  }

  public float getHeight() {
    return height;
  }

  public float getDepth() {
    return depth;
  }
}
