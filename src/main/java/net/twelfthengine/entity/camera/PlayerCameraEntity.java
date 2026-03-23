// net/twelfthengine/entity/camera/PlayerCameraEntity.java
package net.twelfthengine.entity.camera;

import net.twelfthengine.controls.InputManager;
import net.twelfthengine.math.Vec3;
import org.lwjgl.glfw.GLFW;

public class PlayerCameraEntity extends CameraEntity {

  private float pitch; // X Rotation
  private float yaw; // Y Rotation
  private float roll; // Z Rotation

  private boolean isGrounded = false;
  private boolean wantsToJump = false;
  private float jumpStrength = 5.5f;
  private float moveSpeed = 2.2f;
  private float sprintMultiplier = 1.6f;
  private float airControl = 0.35f;

  public PlayerCameraEntity(float x, float y, float z) {
    super(x, y, z);
    this.pitch = 0;
    this.yaw = 0;
    this.roll = 0;

    this.setRigidBodyEnabled(true);
    this.setGravityEnabled(true);

    this.getRigidBody().setMass(1.0f);
    this.getRigidBody().setDrag(0.92f);

    this.setCollisionShape(CollisionShape.CAPSULE);
    this.setCollisionRadius(0.4f);

    this.gravity = 14.0f;
  }

  public void setRotation(float pitch, float yaw, float roll) {
    this.yaw = yaw;
    this.roll = roll;
    this.pitch = Math.max(-89, Math.min(89, pitch));
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

  public void setGrounded(boolean grounded) {
    this.isGrounded = grounded;
  }

  @Override
  public void update(float deltaTime) {
    // --------------------------
    // 1️⃣ Mouse rotation
    // --------------------------
    float mouseSensitivity = 0.1f;
    yaw += InputManager.getMouseDeltaX() * mouseSensitivity;
    pitch += InputManager.getMouseDeltaY() * mouseSensitivity;
    pitch = Math.max(-89, Math.min(89, pitch));
    setRotation(pitch, yaw, 0);

    // --------------------------
    // 3️⃣ Movement calculation
    // --------------------------
    if (isGrounded) {
      // Apply extra friction when grounded
      this.getRigidBody().setDrag(0.92f);
    } else {
      // Reduce friction when in the air
      this.getRigidBody().setDrag(1f);
    }

    Vec3 movementInput = getMovementInput();

    // Apply movement based on grounded state
    float currentMoveSpeed = moveSpeed;
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) && isGrounded) {
      currentMoveSpeed *= sprintMultiplier;
    }

    if (!isGrounded) {
      currentMoveSpeed *= airControl;
    }

    applyMovement(movementInput, currentMoveSpeed, deltaTime);

    // --------------------------
    // 4️⃣ Jump handling
    // --------------------------
    handleJump();

    // --------------------------
    // 5️⃣ Physics update
    // --------------------------
    super.update(deltaTime);
  }

  private Vec3 getMovementInput() {
    Vec3 input = new Vec3(0, 0, 0);

    float yawRad = (float) Math.toRadians(yaw);
    float sinYaw = (float) Math.sin(yawRad);
    float cosYaw = (float) Math.cos(yawRad);

    // FORWARD/BACKWARD
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_W)) {
      input = input.add(new Vec3(sinYaw, 0, -cosYaw));
    }
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_S)) {
      input = input.add(new Vec3(-sinYaw, 0, cosYaw));
    }

    // STRAFING - FIXED VERSION
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_A)) {
      // LEFT strafe: perpendicular to forward, rotated 90 degrees counter-clockwise
      input = input.add(new Vec3(-cosYaw, 0, -sinYaw));
    }
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_D)) {
      // RIGHT strafe: perpendicular to forward, rotated 90 degrees clockwise
      input = input.add(new Vec3(cosYaw, 0, sinYaw));
    }

    // Jump input detection
    if (InputManager.isKeyPressed(GLFW.GLFW_KEY_SPACE) && isGrounded) {
      wantsToJump = true;
    }

    if (input.length() > 0) {
      input = input.normalize();
    }

    return input;
  }

  private void applyMovement(Vec3 direction, float speed, float deltaTime) {
    if (direction.length() > 0.01f) {
      Vec3 targetVelocity = direction.mul(speed);

      if (this.isRigidBodyEnabled()) {
        // Use proper lerp function call
        Vec3 currentVel = getVelocity();
        Vec3 newVel = new Vec3(targetVelocity.x(), currentVel.y(), targetVelocity.z());

        // Manual linear interpolation since Vec3.lerp signature is different
        float t = 0.1f; // Interpolation factor
        Vec3 smoothedVel =
            new Vec3(
                currentVel.x() + (newVel.x() - currentVel.x()) * t,
                currentVel.y() + (newVel.y() - currentVel.y()) * t,
                currentVel.z() + (newVel.z() - currentVel.z()) * t);

        setVelocity(smoothedVel);
      } else {
        // Kinematic approach
        this.setPosition(this.getPosition().add(targetVelocity.mul(deltaTime)));
      }
    } else {
      // Apply friction when no input
      if (this.isRigidBodyEnabled()) {
        Vec3 currentVel = getVelocity();
        Vec3 frictionVel = new Vec3(currentVel.x() * 0.8f, currentVel.y(), currentVel.z() * 0.8f);
        setVelocity(frictionVel);
      }
    }
  }

  private void handleJump() {
    if (wantsToJump) {
      Vec3 vel = getVelocity();
      setVelocity(new Vec3(vel.x(), jumpStrength, vel.z()));
      wantsToJump = false;
      isGrounded = false;
    }
  }

  @Override
  public float getCollisionHeight() {
    return 1.8f; // typical player height
  }
}
