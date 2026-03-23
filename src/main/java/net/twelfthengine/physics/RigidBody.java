package net.twelfthengine.physics;

import net.twelfthengine.math.Vec3;

public class RigidBody {

    private float mass;
    private float drag;
    private float restitution; // Bounciness (0.0 to 1.0)
    private Vec3 forceAccumulator;

    public RigidBody() {
        this.mass = 1f;
        this.drag = 0.98f; // Slight air resistance
        this.restitution = 0.5f;
        this.forceAccumulator = new Vec3(0, 0, 0);
    }

    public void applyForce(Vec3 force) {
        this.forceAccumulator = this.forceAccumulator.add(force);
    }

    public Vec3 calculateNewVelocity(Vec3 currentVelocity, float gravity, boolean affectedByGravity, float deltaTime) {
        // Apply Gravity as a force: F = m * g
        if (affectedByGravity) {
            applyForce(new Vec3(0, -gravity * mass, 0));
        }

        // Acceleration = Force / Mass
        Vec3 acceleration = forceAccumulator.div(mass);

        // Update Velocity: v = v + a * dt
        Vec3 newVelocity = currentVelocity.add(acceleration.mul(deltaTime));

        float dragFactor = (float)Math.pow(drag, deltaTime * 60f);
        newVelocity = newVelocity.mul(dragFactor);

        // Reset forces for the next frame
        this.forceAccumulator = new Vec3(0, 0, 0);

        return newVelocity;
    }

    // Getters and Setters
    public float getMass() { return mass; }
    public void setMass(float mass) { this.mass = Math.max(0.001f, mass); }

    public float getDrag() { return drag; }
    public void setDrag(float drag) { this.drag = drag; }

    public float getRestitution() { return restitution; }
    public void setRestitution(float restitution) { this.restitution = restitution; }
}