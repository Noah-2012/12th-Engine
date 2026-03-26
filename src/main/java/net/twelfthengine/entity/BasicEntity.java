package net.twelfthengine.entity;

import net.twelfthengine.math.Vec3;
import net.twelfthengine.physics.RigidBody;

public abstract class BasicEntity {

  protected Vec3 position;
  protected Vec3 rotation = new Vec3(0, 0, 0); // Pitch, Yaw, Roll in degrees
  protected Vec3 velocity;
  protected Vec3 angularVelocity = new Vec3(0, 0, 0);
  protected boolean affectedByGravity;
  protected float speed;
  protected float gravity;

  protected CollisionShape collisionShape;
  protected float collisionRadius = 0.5f;
  protected boolean collidable = true;
  protected boolean pushable = true;

  // Rigid Body Integration
  protected RigidBody rigidBody;
  protected boolean rigidBodyEnabled;

  public BasicEntity(float x, float y, float z) {
    this.position = new Vec3(x, y, z);
    this.velocity = new Vec3(0, 0, 0);
    this.affectedByGravity = true;
    this.speed = 1.0f;
    this.gravity = 9.18f;

    // Initialize RigidBody but keep it disabled by default
    this.rigidBody = new RigidBody();
    this.rigidBodyEnabled = false;
    this.collisionShape = CollisionShape.SPHERE; // Default
  }

  public void update(float deltaTime) {
    if (rigidBodyEnabled) {
      // Use RigidBody physics to calculate velocity
      velocity = rigidBody.calculateNewVelocity(velocity, gravity, affectedByGravity, deltaTime);
      angularVelocity = rigidBody.calculateNewAngularVelocity(angularVelocity, deltaTime);
    } else {
      // Simple Kinematic movement
      if (affectedByGravity) {
        velocity = velocity.add(new Vec3(0, -gravity * deltaTime, 0));
      }
    }

    // Apply final velocity to position
    position = position.add(velocity.mul(deltaTime));
    rotation = rotation.add(angularVelocity.mul(deltaTime));
  }

  // Physics helper methods
  public void applyForce(Vec3 force) {
    if (rigidBodyEnabled) {
      rigidBody.applyForce(force);
    } else {
      // If physics is off, force just acts as an instant velocity change (Impulse)
      velocity = velocity.add(force.div(1.0f));
    }
  }

  public void applyTorque(Vec3 torque) {
    if (rigidBodyEnabled) {
      rigidBody.applyTorque(torque);
    }
  }

  public void applyImpulse(Vec3 impulse) {
    if (rigidBodyEnabled) {
      velocity = velocity.add(impulse.div(rigidBody.getMass()));
    } else {
      velocity = velocity.add(impulse);
    }
  }

  public void applyAngularImpulse(Vec3 impulse) {
    angularVelocity = angularVelocity.add(impulse);
  }

  // Getters and Setters
  public Vec3 getPosition() {
    return position;
  }

  public void setPosition(Vec3 pos) {
    this.position = pos;
  }

  public Vec3 getRotation() {
    return rotation;
  }

  public void setRotation(Vec3 rot) {
    this.rotation = rot;
  }

  public Vec3 getVelocity() {
    return velocity;
  }

  public void setVelocity(Vec3 vel) {
    this.velocity = vel;
  }

  public Vec3 getAngularVelocity() {
    return angularVelocity;
  }

  public void setAngularVelocity(Vec3 angVel) {
    this.angularVelocity = angVel;
  }

  public float getSpeed() {
    return speed;
  }

  public void setSpeed(float speed) {
    this.speed = speed;
  }

  public float getGravity() {
    return gravity;
  }

  public void setGravity(float gravity) {
    this.gravity = gravity;
  }

  public void setGravityEnabled(boolean enabled) {
    this.affectedByGravity = enabled;
  }

  public boolean isRigidBodyEnabled() {
    return rigidBodyEnabled;
  }

  public void setRigidBodyEnabled(boolean enabled) {
    this.rigidBodyEnabled = enabled;
  }

  public RigidBody getRigidBody() {
    return rigidBody;
  }

  public float getCollisionRadius() {
    return collisionRadius;
  }

  public void setCollisionRadius(float radius) {
    this.collisionRadius = radius;
  }

  public CollisionShape getCollisionShape() {
    return collisionShape;
  }

  public void setCollisionShape(CollisionShape shape) {
    this.collisionShape = shape;
  }

  public float getCollisionHeight() {
    return 0.0f; // default: point
  }

  public boolean isCollidable() {
    return collidable;
  }

  public void setCollidable(boolean collidable) {
    this.collidable = collidable;
  }

  public boolean isPushable() {
    return pushable;
  }

  public void setPushable(boolean pushable) {
    this.pushable = pushable;
  }

  public Vec3 getMinBounds() {
    return position.sub(new Vec3(collisionRadius, collisionRadius, collisionRadius));
  }

  public Vec3 getMaxBounds() {
    return position.add(new Vec3(collisionRadius, collisionRadius, collisionRadius));
  }

  public void checkAndResolveCollision(BasicEntity other) {
    if (this instanceof ModelEntity || other instanceof ModelEntity) {
      return; // Handled by World mesh collision
    }

    if (!this.collidable || !other.collidable) {
      return;
    }

    if (this.collisionShape == CollisionShape.AABB && other.collisionShape == CollisionShape.AABB) {
      resolveAABBCollision(other);
    } else if (this.collisionShape == CollisionShape.AABB
        || other.collisionShape == CollisionShape.AABB) {
      resolveAABBSphereCollision(other);
    } else {
      resolveSphereCollision(other);
    }
  }

  private void resolveAABBSphereCollision(BasicEntity other) {
    BasicEntity aabb = this.collisionShape == CollisionShape.AABB ? this : other;
    BasicEntity sphere = this.collisionShape == CollisionShape.AABB ? other : this;

    Vec3 minA = aabb.getMinBounds();
    Vec3 maxA = aabb.getMaxBounds();
    Vec3 spherePos = sphere.getPosition();
    float radius = sphere.getCollisionRadius();

    float closestX = Math.max(minA.x(), Math.min(spherePos.x(), maxA.x()));
    float closestY = Math.max(minA.y(), Math.min(spherePos.y(), maxA.y()));
    float closestZ = Math.max(minA.z(), Math.min(spherePos.z(), maxA.z()));

    Vec3 closestPoint = new Vec3(closestX, closestY, closestZ);
    Vec3 diff = spherePos.sub(closestPoint);
    float distanceSq = diff.dot(diff);

    if (distanceSq < radius * radius) {
      Vec3 normal;
      float overlap;

      if (distanceSq < 0.0001f) {
        float overXMin = spherePos.x() - minA.x();
        float overXMax = maxA.x() - spherePos.x();
        float overYMin = spherePos.y() - minA.y();
        float overYMax = maxA.y() - spherePos.y();
        float overZMin = spherePos.z() - minA.z();
        float overZMax = maxA.z() - spherePos.z();

        float minOverX = Math.min(overXMin, overXMax);
        float minOverY = Math.min(overYMin, overYMax);
        float minOverZ = Math.min(overZMin, overZMax);

        if (minOverX < minOverY && minOverX < minOverZ) {
          normal = overXMin < overXMax ? new Vec3(-1, 0, 0) : new Vec3(1, 0, 0);
          overlap = minOverX + radius;
        } else if (minOverY < minOverX && minOverY < minOverZ) {
          normal = overYMin < overYMax ? new Vec3(0, -1, 0) : new Vec3(0, 1, 0);
          overlap = minOverY + radius;
        } else {
          normal = overZMin < overZMax ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1);
          overlap = minOverZ + radius;
        }
      } else {
        float distance = (float) Math.sqrt(distanceSq);
        overlap = radius - distance;
        normal = diff.div(distance);
      }

      if (this == aabb) {
        normal = normal.mul(-1);
      }

      applyCollisionResolution(other, normal, overlap);
    }
  }

  private void resolveSphereCollision(BasicEntity other) {
    Vec3 diff = this.position.sub(other.position);
    float distance = diff.length();
    float minDistance = this.collisionRadius + other.collisionRadius;

    if (distance < minDistance && distance > 0.0001f) {
      float overlap = minDistance - distance;
      Vec3 normal = diff.normalize();
      applyCollisionResolution(other, normal, overlap);
    }
  }

  private void resolveAABBCollision(BasicEntity other) {
    Vec3 minA = this.getMinBounds();
    Vec3 maxA = this.getMaxBounds();
    Vec3 minB = other.getMinBounds();
    Vec3 maxB = other.getMaxBounds();

    boolean overlapX = maxA.x() > minB.x() && minA.x() < maxB.x();
    boolean overlapY = maxA.y() > minB.y() && minA.y() < maxB.y();
    boolean overlapZ = maxA.z() > minB.z() && minA.z() < maxB.z();

    if (overlapX && overlapY && overlapZ) {
      float overX = Math.min(maxA.x() - minB.x(), maxB.x() - minA.x());
      float overY = Math.min(maxA.y() - minB.y(), maxB.y() - minA.y());
      float overZ = Math.min(maxA.z() - minB.z(), maxB.z() - minA.z());

      Vec3 normal;
      float overlap;

      if (overX < overY && overX < overZ) {
        overlap = overX;
        normal = (this.position.x() > other.position.x()) ? new Vec3(1, 0, 0) : new Vec3(-1, 0, 0);
      } else if (overY < overX && overY < overZ) {
        overlap = overY;
        normal = (this.position.y() > other.position.y()) ? new Vec3(0, 1, 0) : new Vec3(0, -1, 0);
      } else {
        overlap = overZ;
        normal = (this.position.z() > other.position.z()) ? new Vec3(0, 0, 1) : new Vec3(0, 0, -1);
      }

      applyCollisionResolution(other, normal, overlap);
    }
  }

  private void applyCollisionResolution(BasicEntity other, Vec3 normal, float overlap) {
    float m1 = this.rigidBodyEnabled ? this.rigidBody.getMass() : 1.0f;
    float m2 = other.rigidBodyEnabled ? other.rigidBody.getMass() : 1.0f;

    if (this.pushable && other.pushable) {
      float totalMass = m1 + m2;
      float ratio1 = m2 / totalMass;
      float ratio2 = m1 / totalMass;

      this.position = this.position.add(normal.mul(overlap * ratio1));
      other.position = other.position.sub(normal.mul(overlap * ratio2));
    } else if (this.pushable && !other.pushable) {
      this.position = this.position.add(normal.mul(overlap));
    } else if (!this.pushable && other.pushable) {
      other.position = other.position.sub(normal.mul(overlap));
    } else {
      return;
    }

    if (this.rigidBodyEnabled && other.rigidBodyEnabled) {
      Vec3 relativeVelocity = this.velocity.sub(other.velocity);
      float velAlongNormal = relativeVelocity.dot(normal);

      if (velAlongNormal < 0) {
        float restitution =
            Math.min(this.rigidBody.getRestitution(), other.rigidBody.getRestitution());
        float invM1 = this.pushable ? 1.0f / m1 : 0.0f;
        float invM2 = other.pushable ? 1.0f / m2 : 0.0f;

        float j = -(1 + restitution) * velAlongNormal;
        j /= (invM1 + invM2);

        Vec3 impulse = normal.mul(j);

        if (this.pushable) this.applyImpulse(impulse);
        if (other.pushable) other.applyImpulse(impulse.mul(-1));

        // Calculate a rough contact point to apply torque
        Vec3 r1 = normal.mul(overlap * -0.5f);
        Vec3 r2 = normal.mul(overlap * 0.5f);

        if (this.pushable) this.applyAngularImpulse(r1.cross(impulse).mul(0.2f));
        if (other.pushable) other.applyAngularImpulse(r2.cross(impulse.mul(-1)).mul(0.2f));
      }
    }
  }

  // Enum for different collision shapes
  public enum CollisionShape {
    SPHERE,
    AABB, // Axis Aligned Bounding Box
    CAPSULE,
  }
}
