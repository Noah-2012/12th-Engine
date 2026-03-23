package net.twelfthengine.math;

import org.joml.Matrix4f;
import java.util.Stack;

public class MatrixStack3D {
    private final Stack<Matrix4f> stack = new Stack<>();
    private Matrix4f current = new Matrix4f();

    public void pushMatrix() {
        stack.push(new Matrix4f(current));
    }

    public void popMatrix() {
        if (stack.isEmpty()) {
            throw new RuntimeException("Matrix stack underflow!");
        }
        current = stack.pop();
    }

    public void translate(float x, float y, float z) {
        current.translate(x, y, z);
    }

    public void rotate(float angle, float x, float y, float z) {
        current.rotate((float) Math.toRadians(angle), x, y, z);
    }

    public Matrix4f getDirect() {
        return current;
    }
}