package net.twelfthengine.entity.world;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.math.Vec3;
import org.lwjgl.opengl.GL11;

public class BasicPlaneEntity extends BasicEntity
    implements net.twelfthengine.renderer.Renderable3D {

  @Override
  public void renderLit(
      net.twelfthengine.renderer.Renderer3D renderer,
      net.twelfthengine.renderer.shader.ShaderProgram shader,
      org.joml.Matrix4f view,
      org.joml.Matrix4f proj,
      org.joml.Matrix4f lightSpace) {
    org.joml.Matrix4f model = renderer.modelMatrixForPlane(this);
    renderer.getPlaneMesh().drawLit(shader, model, view, proj, lightSpace);
  }

  @Override
  public void renderShadow(
      net.twelfthengine.renderer.Renderer3D renderer,
      net.twelfthengine.renderer.shader.ShaderProgram depthShader,
      org.joml.Matrix4f lightSpace) {
    org.joml.Matrix4f model = renderer.modelMatrixForPlane(this);
    org.joml.Matrix4f mvp = new org.joml.Matrix4f(lightSpace).mul(model);
    renderer.getPlaneMesh().drawDepth(depthShader, mvp);
  }

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
    return (point.x() + radius >= position.x() - halfW
        && point.x() - radius <= position.x() + halfW
        && point.z() + radius >= position.z() - halfL
        && point.z() - radius <= position.z() + halfL
        && point.y() - radius <= position.y());
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
    GL11.glVertex3f(halfW, 0f, -halfL);
    GL11.glVertex3f(halfW, 0f, halfL);
    GL11.glVertex3f(-halfW, 0f, halfL);
    GL11.glEnd();

    GL11.glPopMatrix();
  }

  private Vec3 getRotatedLowestPoint(ModelEntity modelEntity) {
    Vec3 rot = modelEntity.getRotation();
    float s = modelEntity.getSize();

    float rx = (float) Math.toRadians(rot.x());
    float ry = (float) Math.toRadians(rot.y());
    float rz = (float) Math.toRadians(rot.z());

    float minY = Float.MAX_VALUE;
    int count = 0;
    Vec3 sum = new Vec3(0, 0, 0);

    for (Vec3 v : modelEntity.getModelData().vertices) {
      // Apply scale
      float vx = v.x() * s, vy = v.y() * s, vz = v.z() * s;

      // Apply rotation X
      float y1 = vy * (float) Math.cos(rx) - vz * (float) Math.sin(rx);
      float z1 = vy * (float) Math.sin(rx) + vz * (float) Math.cos(rx);
      vy = y1;
      vz = z1;

      // Apply rotation Y
      float x2 = vx * (float) Math.cos(ry) + vz * (float) Math.sin(ry);
      float z2 = -vx * (float) Math.sin(ry) + vz * (float) Math.cos(ry);
      vx = x2;
      vz = z2;

      // Apply rotation Z
      float x3 = vx * (float) Math.cos(rz) - vy * (float) Math.sin(rz);
      float y3 = vx * (float) Math.sin(rz) + vy * (float) Math.cos(rz);
      vx = x3;
      vy = y3;

      if (vy < minY - 0.02f) {
        minY = vy;
        sum = new Vec3(vx, vy, vz);
        count = 1;
      } else if (vy < minY + 0.02f) {
        sum = sum.add(new Vec3(vx, vy, vz));
        count++;
      }
    }

    if (count > 0) {
      return sum.div((float) count);
    }
    return new Vec3(0, 0, 0);
  }

  // Handle collision with any entity using its actual model bounds
  public boolean handleCollision(BasicEntity entity) {
    Vec3 entityPos = entity.getPosition();
    Vec3 entityVel = entity.getVelocity();

    float halfW = width / 2f;
    float halfL = length / 2f;

    // Check if entity is roughly within plane bounds
    if (entityPos.x() >= position.x() - halfW
        && entityPos.x() <= position.x() + halfW
        && entityPos.z() >= position.z() - halfL
        && entityPos.z() <= position.z() + halfL) {
      float entityBottom;
      Vec3 contactPointOffset = new Vec3(0, 0, 0);

      if (entity instanceof ModelEntity modelEntity && modelEntity.getModelData() != null) {
        Vec3 lowest = getRotatedLowestPoint(modelEntity);
        entityBottom = entityPos.y() + lowest.y();
        contactPointOffset = lowest;
      } else {
        entityBottom = getEntityBottom(entity, entityPos);
      }

      // Check collision with plane surface
      if (entityBottom <= position.y()) {
        float penetration = position.y() - entityBottom;
        // Move entity up
        entity.setPosition(new Vec3(entityPos.x(), entityPos.y() + penetration, entityPos.z()));

        if (entity.isRigidBodyEnabled()) {
          // Convert angular velocity to radians for physics math
          Vec3 angVelRad =
              new Vec3(
                  (float) Math.toRadians(entity.getAngularVelocity().x()),
                  (float) Math.toRadians(entity.getAngularVelocity().y()),
                  (float) Math.toRadians(entity.getAngularVelocity().z()));

          // Velocity at the exact contact point: v_point = v_linear + (w x r)
          Vec3 pointVel = entityVel.add(angVelRad.cross(contactPointOffset));

          if (pointVel.y() < 0) {
            float mass = entity.getRigidBody().getMass();
            float invMass = 1.0f / mass;

            // Rough sphere inertia approximation: I = 2/5 * m * r^2
            float radius = entity.getCollisionRadius();
            if (entity instanceof ModelEntity me) radius = me.getModelBoundingRadius();
            float inertia = 0.4f * mass * radius * radius;
            if (inertia < 0.0001f) inertia = 1.0f;
            float invInertia = 1.0f / inertia;

            float restitution = entity.getRigidBody().getRestitution();

            // Normal is straight up
            Vec3 normal = new Vec3(0, 1, 0);
            Vec3 rCrossN = contactPointOffset.cross(normal);

            // Calculate impulse scalar j
            float angularFactor = rCrossN.dot(rCrossN) * invInertia;
            float j = -(1 + restitution) * pointVel.y();
            j /= (invMass + angularFactor);

            Vec3 impulse = normal.mul(j);
            entity.applyImpulse(impulse);

            // Apply torque impulse to angular velocity
            Vec3 angularImpulseRad = contactPointOffset.cross(impulse).mul(invInertia);
            Vec3 angularImpulseDeg =
                new Vec3(
                    (float) Math.toDegrees(angularImpulseRad.x()),
                    (float) Math.toDegrees(angularImpulseRad.y()),
                    (float) Math.toDegrees(angularImpulseRad.z()));
            entity.applyAngularImpulse(angularImpulseDeg);

            // High angular damping when hitting the ground to settle nicely and avoid infinite
            // jitter
            entity.setAngularVelocity(entity.getAngularVelocity().mul(0.85f));
          }

          // Add some horizontal friction so it doesn't slide forever
          Vec3 horizVel = new Vec3(entityVel.x(), 0, entityVel.z());
          if (horizVel.length() > 0.01f) {
            entity.applyImpulse(horizVel.mul(-0.2f));
          }
        } else {
          if (entityVel.y() < 0) {
            entity.setVelocity(new Vec3(entityVel.x(), 0, entityVel.z()));
          }
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
