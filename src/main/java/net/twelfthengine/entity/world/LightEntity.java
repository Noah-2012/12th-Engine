package net.twelfthengine.entity.world;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.math.Vec3;

/**
 * World light with optional shadow mapping. Direction follows yaw (Y) and pitch (X), same convention as the player camera.
 */
public class LightEntity extends BasicEntity {

    private float yaw;
    private float pitch;
    private float roll;

    private Vec3 color = new Vec3(1f, 0.98f, 0.92f);
    private float intensity = 1.2f;
    private boolean castShadows = true;

    /** Orthographic half-extent for the shadow frustum (world units). */
    private float shadowOrthoHalfSize = 42f;
    private float shadowNear = 0.5f;
    private float shadowFar = 160f;

    public LightEntity(float x, float y, float z) {
        super(x, y, z);
        setGravityEnabled(false);
        setRigidBodyEnabled(false);
    }

    public void setRotation(float pitch, float yaw, float roll) {
        this.pitch = Math.max(-89f, Math.min(89f, pitch));
        this.yaw = yaw;
        this.roll = roll;
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

    /**
     * Normalized direction the light shines (into the scene), in world space.
     */
    public Vec3 getForwardDirection() {
        float yr = (float) Math.toRadians(yaw);
        float pr = (float) Math.toRadians(pitch);
        float x = (float) (Math.cos(pr) * Math.sin(yr));
        float y = (float) (-Math.sin(pr));
        float z = (float) (-Math.cos(pr) * Math.cos(yr));
        return new Vec3(x, y, z).normalize();
    }

    /**
     * Direction from a surface point toward this light (for diffuse / shadow bias).
     */
    public Vec3 getDirectionToLightWorld() {
        return getForwardDirection().mul(-1f).normalize();
    }

    public Vec3 getColor() {
        return color;
    }

    public void setColor(Vec3 color) {
        this.color = color;
    }

    public void setColor(float r, float g, float b) {
        this.color = new Vec3(r, g, b);
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public boolean isCastShadows() {
        return castShadows;
    }

    public void setCastShadows(boolean castShadows) {
        this.castShadows = castShadows;
    }

    public float getShadowOrthoHalfSize() {
        return shadowOrthoHalfSize;
    }

    public void setShadowOrthoHalfSize(float shadowOrthoHalfSize) {
        this.shadowOrthoHalfSize = shadowOrthoHalfSize;
    }

    public float getShadowNear() {
        return shadowNear;
    }

    public void setShadowNear(float shadowNear) {
        this.shadowNear = shadowNear;
    }

    public float getShadowFar() {
        return shadowFar;
    }

    public void setShadowFar(float shadowFar) {
        this.shadowFar = shadowFar;
    }
}
