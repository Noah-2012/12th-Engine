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

  private final float jumpStrength = 5.5f;
  private final float moveSpeed = 4.0f;
  private final float sprintMultiplier = 1.8f;
  private final float airControl = 0.6f;

  public PlayerCameraEntity(float x, float y, float z) {
    super(x, y, z);
    this.pitch = 0;
    this.yaw = 0;
    this.roll = 0;

    this.setRigidBodyEnabled(true);
    this.setGravityEnabled(true);

    this.getRigidBody().setMass(1.0f);
    this.getRigidBody().setDrag(0.92f);
    this.getRigidBody().setRestitution(0.0f); // Prevent bouncing on landing

    this.setCollisionShape(CollisionShape.CAPSULE);
    this.setCollisionRadius(0.4f);

    this.gravity = 14.0f;
  }

  public void setRotation(float pitch, float yaw, float roll) {
    this.yaw = yaw;
    this.roll = roll;
    this.pitch = Math.max(-89, Math.min(89, pitch));
    this.rotation = new Vec3(this.pitch, this.yaw, this.roll);
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

    // Clamp pitch to prevent breaking your neck
    pitch = Math.max(-89, Math.min(89, pitch));
    setRotation(pitch, yaw, 0);

    // --------------------------
    // 2️⃣ Movement calculation
    // --------------------------
    // Disable air drag to allow smooth jumping
    this.getRigidBody().setDrag(isGrounded ? 0.95f : 1.0f);

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

    // STRAFING
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_A)) {
      input = input.add(new Vec3(-cosYaw, 0, -sinYaw));
    }
    if (InputManager.isKeyDown(GLFW.GLFW_KEY_D)) {
      input = input.add(new Vec3(cosYaw, 0, sinYaw));
    }

    // Jump input detection
    if (InputManager.isKeyPressed(GLFW.GLFW_KEY_SPACE) && isGrounded) {
      wantsToJump = true;
    }

    if (input.length() > 0.01f) {
      input = input.normalize();
    }

    return input;
  }

  private void applyMovement(Vec3 direction, float speed, float deltaTime) {
    Vec3 currentVel = getVelocity();

    if (this.isRigidBodyEnabled()) {
      if (direction.length() > 0.01f) {
        Vec3 targetVelocity = direction.mul(speed);
        Vec3 targetXz = new Vec3(targetVelocity.x(), currentVel.y(), targetVelocity.z());

        float t = Math.min(1.0f, (isGrounded ? 15.0f : 2.0f) * deltaTime);
        Vec3 smoothedVel = Vec3.lerp(currentVel, targetXz, t);

        // Preserve exact vertical velocity to avoid messing with gravity/jumping
        setVelocity(new Vec3(smoothedVel.x(), currentVel.y(), smoothedVel.z()));
      } else if (isGrounded) {
        // Apply ground friction when no input
        float t = Math.min(1.0f, 15.0f * deltaTime);
        Vec3 zeroXz = new Vec3(0, currentVel.y(), 0);
        setVelocity(Vec3.lerp(currentVel, zeroXz, t));
      }
    } else {
      // Kinematic approach
      if (direction.length() > 0.01f) {
        this.setPosition(this.getPosition().add(direction.mul(speed * deltaTime)));
      }
    }
  }

  private void handleJump() {
    if (wantsToJump) {
      Vec3 vel = getVelocity();
      // Only jump if we aren't already moving up too fast
      if (vel.y() < jumpStrength * 0.5f) {
        setVelocity(new Vec3(vel.x(), jumpStrength, vel.z()));
      }
      wantsToJump = false;
      isGrounded = false;
    }
  }

  @Override
  public float getCollisionHeight() {
    return 1.8f; // typical player height
  }
}
