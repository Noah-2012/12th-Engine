package net.twelfthengine.math;

public record Vec3(float x, float y, float z) {

  public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);

  public Vec3 add(Vec3 v) {
    return new Vec3(x + v.x, y + v.y, z + v.z);
  }

  public Vec3 sub(Vec3 v) {
    return new Vec3(x - v.x, y - v.y, z - v.z);
  }

  public Vec3 mul(float s) {
    return new Vec3(x * s, y * s, z * s);
  }

  public Vec3 div(float s) {
    return new Vec3(x / s, y / s, z / s);
  }

  public float length() {
    return (float) Math.sqrt(x * x + y * y + z * z);
  }

  public Vec3 normalize() {
    float len = length();
    return len == 0 ? new Vec3(0, 0, 0) : div(len);
  }

  public float dot(Vec3 v) {
    return x * v.x + y * v.y + z * v.z;
  }

  public Vec3 cross(Vec3 v) {
    return new Vec3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
  }

  public static Vec3 lerp(Vec3 a, Vec3 b, float t) {
    return a.mul(1 - t).add(b.mul(t));
  }

  @Override
  public String toString() {
    return "Vec3(" + x + "," + y + "," + z + ")";
  }
}
