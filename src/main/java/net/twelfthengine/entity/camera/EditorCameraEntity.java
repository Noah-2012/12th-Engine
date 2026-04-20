package net.twelfthengine.entity.camera;

import net.twelfthengine.math.Vec3;
import org.lwjgl.glfw.GLFW;

public class EditorCameraEntity extends CameraEntity {

  private final long windowHandle;

  // Orbit state — these are the ONLY source of truth, never read back from the entity
  private float targetX = 0f;
  private float targetY = 0f;
  private float targetZ = 0f;
  private float orbitRadius = 20f;
  private float orbitYaw = 45f;
  private float orbitPitch = 30f;

  // Cached computed position — set once per frame, never derived from entity state
  private float camX = 0f;
  private float camY = 0f;
  private float camZ = 0f;

  private double prevMouseX = Double.MIN_VALUE;
  private double prevMouseY = Double.MIN_VALUE;

  public EditorCameraEntity(long windowHandle) {
    super(0f, 0f, 0f);
    this.windowHandle = windowHandle;
    this.setGravityEnabled(false);
    computeAndApply();
  }

  @Override
  public void update(float deltaTime) {
    // Intentionally do NOT call super.update() — we manage position ourselves.
    // Calling super would let BasicEntity/physics move this camera.
  }

  public void editorUpdate(float delta) {
    double[] mx = new double[1];
    double[] my = new double[1];
    GLFW.glfwGetCursorPos(windowHandle, mx, my);

    boolean rmb =
        GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    boolean mmb =
        GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

    if (prevMouseX != Double.MIN_VALUE && (rmb || mmb)) {
      double dx = mx[0] - prevMouseX;
      double dy = my[0] - prevMouseY;

      if (rmb) {
        orbitYaw += (float) dx * 0.4f;
        orbitPitch -= (float) dy * 0.4f;
        orbitPitch = Math.max(-89f, Math.min(89f, orbitPitch));
      }

      if (mmb) {
        float panSpeed = orbitRadius * 0.0015f;
        float yawRad = (float) Math.toRadians(orbitYaw);

        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);

        targetX -= rightX * (float) dx * panSpeed;
        targetZ -= rightZ * (float) dx * panSpeed;
        targetY += (float) dy * panSpeed;
      }
    }

    prevMouseX = mx[0];
    prevMouseY = my[0];

    computeAndApply();
  }

  public void zoom(float scrollAmount) {
    orbitRadius -= scrollAmount * (orbitRadius * 0.1f);
    orbitRadius = Math.max(0.5f, Math.min(2000f, orbitRadius));
    computeAndApply();
  }

  /**
   * Computes camera world position and look-at rotation from orbit state, then writes directly into
   * the entity. This is the only place that touches entity position/rotation — nothing ever reads
   * them back.
   */
  private void computeAndApply() {
    float pitchRad = (float) Math.toRadians(orbitPitch);
    float yawRad = (float) Math.toRadians(orbitYaw);

    float offsetX = orbitRadius * (float) (Math.cos(pitchRad) * Math.sin(yawRad));
    float offsetY = orbitRadius * (float) Math.sin(pitchRad);
    float offsetZ = orbitRadius * (float) (Math.cos(pitchRad) * Math.cos(yawRad));

    // Store locally so we never need to read position back from the entity
    camX = targetX + offsetX;
    camY = targetY + offsetY;
    camZ = targetZ + offsetZ;

    setPosition(new Vec3(camX, camY, camZ));

    // Look-at: point toward target
    float dx = targetX - camX;
    float dy = targetY - camY;
    float dz = targetZ - camZ;
    float hLen = (float) Math.sqrt(dx * dx + dz * dz);

    float lookPitch = (float) Math.toDegrees(Math.atan2(dy, hLen));
    float lookYaw = (float) Math.toDegrees(Math.atan2(dx, dz));

    setRotation(lookPitch, lookYaw, 0f);
  }

  // Accessors
  public float getTargetX() {
    return targetX;
  }

  public float getTargetY() {
    return targetY;
  }

  public float getTargetZ() {
    return targetZ;
  }

  public float getOrbitRadius() {
    return orbitRadius;
  }

  public void setTarget(float x, float y, float z) {
    targetX = x;
    targetY = y;
    targetZ = z;
    computeAndApply();
  }

  public void setOrbitRadius(float r) {
    orbitRadius = Math.max(0.5f, Math.min(2000f, r));
    computeAndApply();
  }
}
