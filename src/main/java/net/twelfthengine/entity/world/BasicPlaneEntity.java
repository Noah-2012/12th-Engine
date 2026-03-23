package net.twelfthengine.entity.world;

import org.lwjgl.opengl.GL11;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.math.Vec3;

public class BasicPlaneEntity extends BasicEntity {
    private float width, length;
    private float[] color = {0.8f, 0.8f, 0.8f};

    public BasicPlaneEntity(float x, float y, float z, float width, float length) {
        super(x, y, z);
        this.width = width;
        this.length = length;
        this.setRigidBodyEnabled(false);
        this.setGravityEnabled(false);
    }

    public boolean intersects(Vec3 point, float radius) {
        float halfW = width / 2f;
        float halfL = length / 2f;
        return point.x() + radius >= position.x() - halfW &&
                point.x() - radius <= position.x() + halfW &&
                point.z() + radius >= position.z() - halfL &&
                point.z() - radius <= position.z() + halfL &&
                point.y() - radius <= position.y();
    }

    public float getTop() {
        return position.y();
    }

    public float getWidth() {
        return width;
    }

    public float getLength() {
        return length;
    }

    public void setColor(float r, float g, float b) {
        color[0] = r;
        color[1] = g;
        color[2] = b;
    }

    public void render() {
        float halfW = width / 2f;
        float halfL = length / 2f;

        GL11.glPushMatrix();
        GL11.glTranslatef(position.x(), position.y(), position.z());
        GL11.glColor3f(color[0], color[1], color[2]);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0f, 1f, 0f); // Normale nach oben

        GL11.glVertex3f(-halfW, 0f, -halfL);
        GL11.glVertex3f( halfW, 0f, -halfL);
        GL11.glVertex3f( halfW, 0f,  halfL);
        GL11.glVertex3f(-halfW, 0f,  halfL);
        GL11.glEnd();

        GL11.glPopMatrix();
    }

    // Handle collision with any entity using its actual model bounds
    public boolean handleCollision(BasicEntity entity) {
        Vec3 entityPos = entity.getPosition();
        Vec3 entityVel = entity.getVelocity();

        float halfW = width / 2f;
        float halfL = length / 2f;

        // Check if entity is within plane bounds
        if (entityPos.x() >= position.x() - halfW && entityPos.x() <= position.x() + halfW &&
                entityPos.z() >= position.z() - halfL && entityPos.z() <= position.z() + halfL) {

            // Get entity's actual collision bounds
            float entityBottom = getEntityBottom(entity, entityPos);

            // Check collision with plane surface
            if (entityBottom <= position.y()) {
                // Move entity to rest on top of plane
                float newY = position.y() - (entityBottom - entityPos.y());
                entity.setPosition(new Vec3(entityPos.x(), newY, entityPos.z()));

                // Apply bounce physics
                if (entityVel.y() < 0) {
                    float restitution = entity.getRigidBody().getRestitution();
                    entity.setVelocity(new Vec3(entityVel.x(), -entityVel.y() * restitution, entityVel.z()));
                } else {
                    entity.setVelocity(new Vec3(entityVel.x(), 0, entityVel.z()));
                }
                return true;
            }
        }
        return false;
    }

    private static float getEntityBottom(BasicEntity entity, Vec3 entityPos) {
        float entityBottom = entityPos.y();
        float entityHeight = 1.0f; // Default height
        float height = entity.getCollisionHeight();
        float radius = entity.getCollisionRadius();

        // If it's a ModelEntity, get actual model bounds
        if (entity instanceof ModelEntity) {
            ModelEntity modelEntity = (ModelEntity) entity;
            Vec3 minBounds = modelEntity.getModelMinBounds();
            Vec3 maxBounds = modelEntity.getModelMaxBounds();
            entityHeight = (maxBounds.y() - minBounds.y());
            entityBottom = entityPos.y() + minBounds.y();
        }

        if (entity instanceof PlayerCameraEntity) {
            return entityPos.y() - (height * 0.5f) - radius;
        }

        return entityBottom;
    }
}
