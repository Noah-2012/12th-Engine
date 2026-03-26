package net.twelfthengine.entity.camera;

import net.twelfthengine.entity.BasicEntity;

public class CameraEntity extends BasicEntity {

  private float pitch; // X Rotation
  private float yaw; // Y Rotation
  private float roll; // Z Rotation

  private float cx;
  private float cy;
  private float cz;

  public CameraEntity(float x, float y, float z) {
    super(x, y, z);
    cx = x;
    cy = y;
    cz = z;
    this.pitch = 0;
    this.yaw = 0;
    this.roll = 0;
    this.setGravityEnabled(false); // Kamera nicht von Gravity betroffen
  }

  // Rotationen
  public void setRotation(float pitch, float yaw, float roll) {
    this.yaw = yaw;
    this.roll = roll;

    this.pitch = pitch;
  }

  public float getPitch() {
    return pitch;
  }

  public float getYaw() {
    return yaw;
  }

  public float getRoll() {
    return roll;
  }

  @Override
  public void update(float deltaTime) {
    super.update(deltaTime);
  }

  @Override
  public boolean isRigidBodyEnabled() {
    return true;
  }
}
