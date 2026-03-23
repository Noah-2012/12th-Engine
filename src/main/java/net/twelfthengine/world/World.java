package net.twelfthengine.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.twelfthengine.coord.iab.IAB;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.obj.ObjModel;

public class World {

  private final IAB bounds;
  private final List<BasicEntity> entities = new ArrayList<>();

  private CameraEntity activeCamera;

  public World(IAB bounds) {
    this.bounds = bounds;
  }

  // =========================
  // BOUNDS
  // =========================

  public IAB getBounds() {
    return bounds;
  }

  // =========================
  // CAMERA
  // =========================

  public void setActiveCamera(CameraEntity camera) {
    this.activeCamera = camera;
  }

  public CameraEntity getActiveCamera() {
    if (activeCamera == null) throw new IllegalStateException("World has no active camera!");
    return activeCamera;
  }

  /** First light that casts shadows, or null if none. */
  public LightEntity getPrimaryShadowLight() {
    for (BasicEntity e : entities) {
      if (e instanceof LightEntity light && light.isCastShadows()) {
        return light;
      }
    }
    return null;
  }

  // =========================
  // ENTITIES
  // =========================

  public void addEntity(BasicEntity entity) {
    entities.add(entity);
  }

  public void removeEntity(BasicEntity entity) {
    entities.remove(entity);
  }

  public List<BasicEntity> getEntities() {
    return Collections.unmodifiableList(entities);
  }

  public void clearEntitiesExcept(BasicEntity camera) {
    this.entities.clear();
    this.entities.add(camera);
  }

  // =========================
  // UPDATE
  // =========================

  public void update(float deltaTime) {
    // First update all entities normally
    for (BasicEntity e : entities) {
      e.update(deltaTime);
    }

    // Then handle collisions
    handleCollisions();
  }

  private void handleCollisions() {
    List<BasicPlaneEntity> planes = new ArrayList<>();
    List<ModelEntity> models = new ArrayList<>();
    for (BasicEntity entity : entities) {
      if (entity instanceof BasicPlaneEntity) {
        planes.add((BasicPlaneEntity) entity);
        ((BasicPlaneEntity) entity).render();
      } else if (entity instanceof ModelEntity modelEntity) {
        models.add(modelEntity);
      }
    }

    // Collide normal entities
    for (BasicEntity entity : entities) {
      if (entity.isRigidBodyEnabled() && !(entity instanceof BasicPlaneEntity)) {
        for (BasicPlaneEntity plane : planes) {
          if (plane.handleCollision(entity)) break;
        }
      }
    }

    // Collide camera
    if (activeCamera != null && activeCamera.isRigidBodyEnabled()) {
      boolean grounded = false;
      for (BasicPlaneEntity plane : planes) {
        if (plane.handleCollision(activeCamera)) {
          grounded = true;
          break;
        }
      }

      if (activeCamera instanceof PlayerCameraEntity playerCamera) {
        playerCamera.setGrounded(grounded);
        for (ModelEntity model : models) {
          resolveCameraModelCollision(playerCamera, model);
        }
      }
    }
  }

  private void resolveCameraModelCollision(PlayerCameraEntity camera, ModelEntity model) {
    ObjModel modelData = model.getModelData();
    if (modelData == null || modelData.faces.isEmpty()) return;

    Vec3 camPos = camera.getPosition();
    Vec3 camVel = camera.getVelocity();
    float camRadius = camera.getCollisionRadius();
    float camHalfHeight = camera.getCollisionHeight() * 0.5f;

    Vec3 modelPos = model.getPosition();
    Vec3 modelCenter = modelPos.add(model.getModelCenter());
    float modelBoundingRadius = model.getModelBoundingRadius();
    float broadPhase = modelBoundingRadius + camHalfHeight + camRadius + 0.5f;
    Vec3 toModel = camPos.sub(modelCenter);
    if (toModel.dot(toModel) > broadPhase * broadPhase) return;

    float camBottom = camPos.y() - camHalfHeight + camRadius;
    float camCenterY = camPos.y();
    float camTop = camPos.y() + camHalfHeight - camRadius;
    Vec3[] sampleCenters = {
      new Vec3(camPos.x(), camBottom, camPos.z()),
      new Vec3(camPos.x(), camCenterY, camPos.z()),
      new Vec3(camPos.x(), camTop, camPos.z())
    };

    for (int iteration = 0; iteration < 3; iteration++) {
      boolean collided = false;

      camPos = camera.getPosition();
      sampleCenters =
          new Vec3[] {
            new Vec3(camPos.x(), camPos.y() - camHalfHeight + camRadius, camPos.z()),
            new Vec3(camPos.x(), camPos.y(), camPos.z()),
            new Vec3(camPos.x(), camPos.y() + camHalfHeight - camRadius, camPos.z())
          };

      for (ObjModel.Face face : modelData.faces) {
        Vec3 a = modelPos.add(modelData.vertices.get(face.vertexIndices[0]).mul(model.getSize()));
        Vec3 b = modelPos.add(modelData.vertices.get(face.vertexIndices[1]).mul(model.getSize()));
        Vec3 c = modelPos.add(modelData.vertices.get(face.vertexIndices[2]).mul(model.getSize()));

        Vec3 triMin =
            new Vec3(
                Math.min(a.x(), Math.min(b.x(), c.x())),
                Math.min(a.y(), Math.min(b.y(), c.y())),
                Math.min(a.z(), Math.min(b.z(), c.z())));
        Vec3 triMax =
            new Vec3(
                Math.max(a.x(), Math.max(b.x(), c.x())),
                Math.max(a.y(), Math.max(b.y(), c.y())),
                Math.max(a.z(), Math.max(b.z(), c.z())));

        Vec3 cameraMin =
            new Vec3(camPos.x() - camRadius, camPos.y() - camHalfHeight, camPos.z() - camRadius);
        Vec3 cameraMax =
            new Vec3(camPos.x() + camRadius, camPos.y() + camHalfHeight, camPos.z() + camRadius);

        if (!aabbIntersects(cameraMin, cameraMax, triMin, triMax)) continue;

        for (Vec3 sampleCenter : sampleCenters) {
          Vec3 closest = closestPointOnTriangle(sampleCenter, a, b, c);
          Vec3 delta = sampleCenter.sub(closest);
          float distSq = delta.dot(delta);
          if (distSq < camRadius * camRadius) {
            float dist = (float) Math.sqrt(Math.max(distSq, 0.000001f));
            Vec3 normal = dist > 0.0001f ? delta.div(dist) : computeTriangleNormal(a, b, c);
            float pushOut = camRadius - dist + 0.001f;

            Vec3 newPos =
                camera.getPosition().add(new Vec3(normal.x() * pushOut, 0f, normal.z() * pushOut));
            camera.setPosition(newPos);

            Vec3 currentVel = camera.getVelocity();
            float inwardSpeed = currentVel.x() * normal.x() + currentVel.z() * normal.z();
            if (inwardSpeed < 0f) {
              camera.setVelocity(
                  new Vec3(
                      currentVel.x() - inwardSpeed * normal.x(),
                      currentVel.y(),
                      currentVel.z() - inwardSpeed * normal.z()));
            }

            collided = true;
            break;
          }
        }

        if (collided) break;
      }

      if (!collided) break;
    }
  }

  private boolean aabbIntersects(Vec3 minA, Vec3 maxA, Vec3 minB, Vec3 maxB) {
    return maxA.x() >= minB.x()
        && minA.x() <= maxB.x()
        && maxA.y() >= minB.y()
        && minA.y() <= maxB.y()
        && maxA.z() >= minB.z()
        && minA.z() <= maxB.z();
  }

  private Vec3 closestPointOnTriangle(Vec3 p, Vec3 a, Vec3 b, Vec3 c) {
    Vec3 ab = b.sub(a);
    Vec3 ac = c.sub(a);
    Vec3 ap = p.sub(a);

    float d1 = ab.dot(ap);
    float d2 = ac.dot(ap);
    if (d1 <= 0f && d2 <= 0f) return a;

    Vec3 bp = p.sub(b);
    float d3 = ab.dot(bp);
    float d4 = ac.dot(bp);
    if (d3 >= 0f && d4 <= d3) return b;

    float vc = d1 * d4 - d3 * d2;
    if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
      float v = d1 / (d1 - d3);
      return a.add(ab.mul(v));
    }

    Vec3 cp = p.sub(c);
    float d5 = ab.dot(cp);
    float d6 = ac.dot(cp);
    if (d6 >= 0f && d5 <= d6) return c;

    float vb = d5 * d2 - d1 * d6;
    if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
      float w = d2 / (d2 - d6);
      return a.add(ac.mul(w));
    }

    float va = d3 * d6 - d5 * d4;
    if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
      Vec3 bc = c.sub(b);
      float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
      return b.add(bc.mul(w));
    }

    float denom = 1f / (va + vb + vc);
    float v = vb * denom;
    float w = vc * denom;
    return a.add(ab.mul(v)).add(ac.mul(w));
  }

  private Vec3 computeTriangleNormal(Vec3 a, Vec3 b, Vec3 c) {
    Vec3 n = b.sub(a).cross(c.sub(a)).normalize();
    if (n.length() < 0.0001f) return new Vec3(1f, 0f, 0f);
    return n;
  }
}
