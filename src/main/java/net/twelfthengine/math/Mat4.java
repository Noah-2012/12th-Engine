package net.twelfthengine.math;

public class Mat4 {

  public float[] m = new float[16];

  public Mat4() {
    identity();
  }

  public Mat4 identity() {
    for (int i = 0; i < 16; i++) m[i] = 0;
    m[0] = m[5] = m[10] = m[15] = 1;
    return this;
  }

  public static Mat4 perspective(float fov, float aspect, float near, float far) {

    Mat4 mat = new Mat4();

    float tanFOV = (float) Math.tan(Math.toRadians(fov / 2f));

    mat.m[0] = 1f / (aspect * tanFOV);
    mat.m[5] = 1f / tanFOV;
    mat.m[10] = -(far + near) / (far - near);
    mat.m[11] = -1f;
    mat.m[14] = -(2f * far * near) / (far - near);
    mat.m[15] = 0f;

    return mat;
  }

  public static Mat4 translation(Vec3 v) {
    Mat4 mat = new Mat4();
    mat.m[12] = v.x();
    mat.m[13] = v.y();
    mat.m[14] = v.z();
    return mat;
  }

  public static Mat4 rotationY(float angle) {
    Mat4 mat = new Mat4();
    float r = (float) Math.toRadians(angle);

    mat.m[0] = (float) Math.cos(r);
    mat.m[2] = (float) -Math.sin(r);
    mat.m[8] = (float) Math.sin(r);
    mat.m[10] = (float) Math.cos(r);

    return mat;
  }

  // Add this to your Mat4 class
  public static Mat4 ortho(
      float left, float right, float bottom, float top, float near, float far) {
    Mat4 mat = new Mat4();
    mat.m[0] = 2f / (right - left);
    mat.m[5] = 2f / (top - bottom);
    mat.m[10] = -2f / (far - near);
    mat.m[12] = -(right + left) / (right - left);
    mat.m[13] = -(top + bottom) / (top - bottom);
    mat.m[14] = -(far + near) / (far - near);
    return mat;
  }

  public Mat4 multiply(Mat4 other) {
    Mat4 res = new Mat4();
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        res.m[i * 4 + j] =
            m[i * 4 + 0] * other.m[0 * 4 + j]
                + m[i * 4 + 1] * other.m[1 * 4 + j]
                + m[i * 4 + 2] * other.m[2 * 4 + j]
                + m[i * 4 + 3] * other.m[3 * 4 + j];
      }
    }
    return res;
  }
}
