package net.twelfthengine.math;

public record Vec4(float x, float y, float z, float w) {

    public Vec4 add(Vec4 v) {
        return new Vec4(x + v.x, y + v.y, z + v.z, w + v.w);
    }

    public Vec4 sub(Vec4 v) {
        return new Vec4(x - v.x, y - v.y, z - v.z, w - v.w);
    }

    public Vec4 mul(float s) {
        return new Vec4(x * s, y * s, z * s, w * s);
    }

    public Vec4 div(float s) {
        return new Vec4(x / s, y / s, z / s, w / s);
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z + w * w);
    }

    public Vec4 normalize() {
        float len = length();
        return len == 0 ? new Vec4(0, 0, 0, 0) : div(len);
    }

    public static Vec4 lerp(Vec4 a, Vec4 b, float t) {
        return a.mul(1 - t).add(b.mul(t));
    }

    @Override
    public String toString() {
        return "Vec4(" + x + "," + y + "," + z + "," + w + ")";
    }
}