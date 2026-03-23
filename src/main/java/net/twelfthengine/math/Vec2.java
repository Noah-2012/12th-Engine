package net.twelfthengine.math;

public record Vec2(int x, int y) {

    public Vec2 add(Vec2 v) {
        return new Vec2(this.x + v.x, this.y + v.y);
    }

    public Vec2 sub(Vec2 v) {
        return new Vec2(this.x - v.x, this.y - v.y);
    }

    public Vec2 mul(int scalar) {
        return new Vec2(this.x * scalar, this.y * scalar);
    }

    public Vec2 div(int scalar) {
        return new Vec2(this.x / scalar, this.y / scalar);
    }

    public int length() {
        return (int) Math.sqrt(x * x + y * y);
    }

    public Vec2 normalize() {
        float len = length();
        return len == 0 ? new Vec2(0, 0) : div((int) len);
    }

    public static Vec2 lerp(Vec2 a, Vec2 b, int t) {
        return a.mul(1 - t).add(b.mul(t));
    }

    @Override
    public String toString() {
        return "Vec2(" + x + "," + y + ")";
    }
}