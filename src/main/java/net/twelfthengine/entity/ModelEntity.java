package net.twelfthengine.entity;

import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.obj.ObjLoader;
import net.twelfthengine.renderer.obj.ObjModel;
import java.io.IOException;

public class ModelEntity extends BasicEntity {

    private String modelPath;
    private float size = 1.0f;
    private ObjModel cachedModel = null;

    public ModelEntity(float x, float y, float z, String modelPath) {
        super(x, y, z);
        this.modelPath = modelPath;
        loadModelData();
    }

    private void loadModelData() {
        try {
            this.cachedModel = ObjLoader.load(modelPath);
        } catch (IOException e) {
            System.err.println("Failed to load model for physics: " + modelPath);
            this.cachedModel = null;
        }
    }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String path) {
        this.modelPath = path;
        loadModelData();
    }

    // Size methods
    public float getSize() { return size; }
    public void setSize(float size) {
        this.size = Math.max(0.001f, size);
    }

    // Get model bounds for physics
    public Vec3 getModelMinBounds() {
        if (cachedModel != null) {
            return cachedModel.getMinBounds().mul(size);
        }
        return new Vec3(-0.5f, -0.5f, -0.5f); // Default fallback
    }

    public Vec3 getModelMaxBounds() {
        if (cachedModel != null) {
            return cachedModel.getMaxBounds().mul(size);
        }
        return new Vec3(0.5f, 0.5f, 0.5f); // Default fallback
    }

    public Vec3 getModelCenter() {
        if (cachedModel != null) {
            return cachedModel.getCenter().mul(size);
        }
        return new Vec3(0, 0, 0); // Default fallback
    }

    public float getModelBoundingRadius() {
        if (cachedModel != null) {
            return cachedModel.getBoundingRadius() * size;
        }
        return 0.5f; // Default fallback
    }

    public ObjModel getModelData() {
        return cachedModel;
    }

    // Rigidbody convenience methods
    public void enableRigidbody() {
        this.rigidBodyEnabled = true;
    }

    public void disableRigidbody() {
        this.rigidBodyEnabled = false;
    }

    // Rigidbody property accessors
    public void setMass(float mass) {
        if (rigidBody != null) {
            rigidBody.setMass(mass);
        }
    }

    public void setDrag(float drag) {
        if (rigidBody != null) {
            rigidBody.setDrag(drag);
        }
    }

    public void setRestitution(float restitution) {
        if (rigidBody != null) {
            rigidBody.setRestitution(restitution);
        }
    }

    public float getMass() {
        return rigidBody != null ? rigidBody.getMass() : 1.0f;
    }

    public float getDrag() {
        return rigidBody != null ? rigidBody.getDrag() : 0.98f;
    }

    public float getRestitution() {
        return rigidBody != null ? rigidBody.getRestitution() : 0.5f;
    }
}
