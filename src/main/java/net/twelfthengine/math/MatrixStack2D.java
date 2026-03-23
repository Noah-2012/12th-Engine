package net.twelfthengine.math;

import org.joml.Matrix3x2f;
import java.util.ArrayDeque;
import java.util.Deque;

public class MatrixStack2D {
    private final Deque<Matrix3x2f> stack = new ArrayDeque<>();
    private Matrix3x2f current = new Matrix3x2f();

    public void pushMatrix() {
        stack.push(new Matrix3x2f(current));
    }

    public void popMatrix() {
        if (stack.isEmpty()) throw new IllegalStateException("Stack underflow");
        current = stack.pop();
    }

    // Your specific requirement: public Matrix3x2f translate(float x, float y, Matrix3x2f dest)
    public Matrix3x2f translate(float x, float y, Matrix3x2f dest) {
        return current.translate(x, y, dest);
    }

    public void translate(float x, float y) {
        translate(x, y, current);
    }

    public Matrix3x2f getMatrix() {
        return current;
    }
}