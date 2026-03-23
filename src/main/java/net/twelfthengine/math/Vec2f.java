package net.twelfthengine.math;

public record Vec2f(float x, float y) {

  public Vec2f add(Vec2f v) {
    return new Vec2f(this.x + v.x, this.y + v.y);
  }

  public Vec2f sub(Vec2f v) {
    return new Vec2f(this.x - v.x, this.y - v.y);
  }

  public Vec2f mul(int scalar) {
    return new Vec2f(this.x * scalar, this.y * scalar);
  }

  public Vec2f div(int scalar) {
    return new Vec2f(this.x / scalar, this.y / scalar);
  }

  public int length() {
    return (int) Math.sqrt(x * x + y * y);
  }

  public Vec2f normalize() {
    float len = length();
    return len == 0 ? new Vec2f(0, 0) : div((int) len);
  }

  public static Vec2f lerp(Vec2f a, Vec2f b, int t) {
    return a.mul(1 - t).add(b.mul(t));
  }

  @Override
  public String toString() {
    return "Vec2f(" + x + "," + y + ")";
  }
}
