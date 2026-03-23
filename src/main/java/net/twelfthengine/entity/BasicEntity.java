package net.twelfthengine.entity;

import net.twelfthengine.math.Vec3;
import net.twelfthengine.physics.RigidBody;

public abstract class BasicEntity {

    protected Vec3 position;
    protected Vec3 velocity;
    protected boolean affectedByGravity;
    protected float speed;
    protected float gravity;

    // Rigid Body Integration
    protected RigidBody rigidBody;
    protected boolean rigidBodyEnabled;

    // Collision properties
    protected CollisionShape collisionShape;
    protected float collisionRadius = 0.5f;

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
        } else {
            // Simple Kinematic movement
            if (affectedByGravity) {
                velocity = velocity.add(new Vec3(0, -gravity * deltaTime, 0));
            }
        }

        // Apply final velocity to position
        position = position.add(velocity.mul(deltaTime));
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

    // Getters and Setters
    public Vec3 getPosition() { return position; }
    public void setPosition(Vec3 pos) { this.position = pos; }

    public Vec3 getVelocity() { return velocity; }
    public void setVelocity(Vec3 vel) { this.velocity = vel; }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    public float getGravity() { return gravity; }
    public void setGravity(float gravity) { this.gravity = gravity; }

    public void setGravityEnabled(boolean enabled) { this.affectedByGravity = enabled; }

    public boolean isRigidBodyEnabled() { return rigidBodyEnabled; }
    public void setRigidBodyEnabled(boolean enabled) { this.rigidBodyEnabled = enabled; }

    public RigidBody getRigidBody() { return rigidBody; }

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

    // Enum for different collision shapes
    public enum CollisionShape {
        SPHERE,
        AABB, // Axis Aligned Bounding Box
        CAPSULE
    }

    public float getCollisionHeight() {
        return 0.0f; // default: point
    }

}
