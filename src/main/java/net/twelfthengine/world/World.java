package net.twelfthengine.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Frame-rate update: entity integration, mouse/keyboard input reading (inside entity updates),
   * and the CHEAP collisions (entity-entity, entity-plane, camera-plane / grounded).
   *
   * <p>Mesh-precise sphere-vs-triangle collision is NOT done here — see {@link
   * #updateMeshCollisions(float)} for that. The mesh path is O(n² × tris) and must stay on the
   * fixed-timestep tick to avoid tanking FPS with many entities, while everything else (including
   * input handling inside PlayerCameraEntity.update) needs to run at frame rate so mouse look, jump
   * edge-trigger (isKeyPressed), and camera motion don't feel capped at the tick rate.
   */
  public void update(float deltaTime) {
    // First update all entities normally (reads input, integrates physics)
    for (BasicEntity e : entities) {
      e.update(deltaTime);
      if (e instanceof net.twelfthengine.entity.world.TextureEntity te) {
        te.faceCamera(activeCamera);
      }
    }

    // Cheap collisions only — run every frame.
    handleCheapCollisions();
  }

  /**
   * Tick-rate update: runs the expensive mesh-precise sphere-vs-triangle collision resolver. This
   * is called from the fixed-timestep accumulator in EngineBootstrap instead of per frame, because
   * at 20+ rigid entities this scales O(n² × tris) and would otherwise dominate the frame budget.
   *
   * <p>Tradeoff: at very high speeds an entity could penetrate a mesh by up to {@code speed *
   * tickTime} before being pushed out. At the default {@code moveSpeed = 14} and {@code tickRate =
   * 20}, that's ~0.7 units — smaller than the player capsule radius × 2, so tunneling is unlikely
   * for player-speed motion.
   */
  public void updateMeshCollisions(float deltaTime) {
    handleMeshCollisions();
  }

  // =========================
  // COLLISION — TRANSFORMED MESH CACHE
  // =========================

  /**
   * World-space triangle data for a ModelEntity, computed once per tick and reused across every
   * (entity, model) collision pair. Previously the entire model was re-transformed inside every
   * iteration of every entity-vs-model call — with 15 rigid bodies and 15 models, that was on the
   * order of half a million triangle-vertex transforms per tick (plus 6 sin/cos per vertex). Now it
   * is computed at most once per model per tick, and only lazily (only if something actually gets
   * close enough to clear the broad-phase).
   */
  private static final class TransformedMesh {
    /** World-space triangle vertices, laid out as [v0x,v0y,v0z, v1x,v1y,v1z, v2x,v2y,v2z] × N. */
    final float[] verts;

    /** Per-triangle AABB mins, laid out as [minX,minY,minZ] × N. */
    final float[] triMin;

    /** Per-triangle AABB maxes, laid out as [maxX,maxY,maxZ] × N. */
    final float[] triMax;

    final int faceCount;

    TransformedMesh(int faceCount) {
      this.faceCount = faceCount;
      this.verts = new float[faceCount * 9];
      this.triMin = new float[faceCount * 3];
      this.triMax = new float[faceCount * 3];
    }
  }

  /**
   * Per-tick transformed-mesh cache. Cleared at the start of each {@link #handleMeshCollisions()}
   * and populated lazily. IdentityHashMap because keys are ModelEntity references — equality by
   * identity is correct and faster than hash-based equals.
   */
  private final Map<ModelEntity, TransformedMesh> transformedMeshCache = new IdentityHashMap<>();

  private TransformedMesh getOrBuildTransformedMesh(ModelEntity model, ObjModel modelData) {
    TransformedMesh cached = transformedMeshCache.get(model);
    if (cached != null) return cached;

    int faceCount = modelData.faces.size();
    TransformedMesh mesh = new TransformedMesh(faceCount);

    // Precompute sin/cos exactly ONCE per model per tick. The previous
    // applyTransform() computed 6 trig calls per vertex × 3 vertices × N faces ×
    // 3 iterations × every colliding entity — easily tens of millions per second
    // at 20 entities. This loop does 6 trig calls total.
    Vec3 rot = model.getRotation();
    float rx = (float) Math.toRadians(rot.x());
    float ry = (float) Math.toRadians(rot.y());
    float rz = (float) Math.toRadians(rot.z());
    float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
    float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
    float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);

    float s = model.getSize();
    Vec3 modelPos = model.getPosition();
    float px = modelPos.x(), py = modelPos.y(), pz = modelPos.z();

    float[] verts = mesh.verts;
    float[] triMin = mesh.triMin;
    float[] triMax = mesh.triMax;

    for (int f = 0; f < faceCount; f++) {
      ObjModel.Face face = modelData.faces.get(f);
      int baseV = f * 9;
      int baseB = f * 3;

      float tMinX = Float.POSITIVE_INFINITY,
          tMinY = Float.POSITIVE_INFINITY,
          tMinZ = Float.POSITIVE_INFINITY;
      float tMaxX = Float.NEGATIVE_INFINITY,
          tMaxY = Float.NEGATIVE_INFINITY,
          tMaxZ = Float.NEGATIVE_INFINITY;

      for (int i = 0; i < 3; i++) {
        Vec3 v = modelData.vertices.get(face.vertexIndices[i]);
        float vx = v.x() * s, vy = v.y() * s, vz = v.z() * s;

        // Rotate X (Y,Z plane)
        float y1 = vy * cx - vz * sx;
        float z1 = vy * sx + vz * cx;
        vy = y1;
        vz = z1;

        // Rotate Y (X,Z plane)
        float x2 = vx * cy + vz * sy;
        float z2 = -vx * sy + vz * cy;
        vx = x2;
        vz = z2;

        // Rotate Z (X,Y plane)
        float x3 = vx * cz - vy * sz;
        float y3 = vx * sz + vy * cz;
        vx = x3;
        vy = y3;

        float wx = px + vx;
        float wy = py + vy;
        float wz = pz + vz;

        verts[baseV + i * 3] = wx;
        verts[baseV + i * 3 + 1] = wy;
        verts[baseV + i * 3 + 2] = wz;

        if (wx < tMinX) tMinX = wx;
        if (wy < tMinY) tMinY = wy;
        if (wz < tMinZ) tMinZ = wz;
        if (wx > tMaxX) tMaxX = wx;
        if (wy > tMaxY) tMaxY = wy;
        if (wz > tMaxZ) tMaxZ = wz;
      }

      triMin[baseB] = tMinX;
      triMin[baseB + 1] = tMinY;
      triMin[baseB + 2] = tMinZ;
      triMax[baseB] = tMaxX;
      triMax[baseB + 1] = tMaxY;
      triMax[baseB + 2] = tMaxZ;
    }

    transformedMeshCache.put(model, mesh);
    return mesh;
  }

  /**
   * Cheap collisions: entity-entity, entity-plane, camera-plane (grounded). Called every frame from
   * {@link #update(float)}. Does NOT touch mesh-precise triangle collision.
   */
  private void handleCheapCollisions() {
    List<BasicPlaneEntity> planes = new ArrayList<>();
    for (BasicEntity entity : entities) {
      if (entity instanceof BasicPlaneEntity) {
        planes.add((BasicPlaneEntity) entity);
        ((BasicPlaneEntity) entity).render();
      }
    }

    for (int i = 0; i < entities.size(); i++) {
      BasicEntity entityA = entities.get(i);
      for (int j = i + 1; j < entities.size(); j++) {
        BasicEntity entityB = entities.get(j);
        entityA.checkAndResolveCollision(entityB);
      }
    }

    // Collide normal entities against planes
    for (BasicEntity entity : entities) {
      if (entity.isRigidBodyEnabled() && !(entity instanceof BasicPlaneEntity)) {
        for (BasicPlaneEntity plane : planes) {
          if (plane.handleCollision(entity)) break;
        }
      }
    }

    // Collide camera against planes (also determines grounded state)
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
      }
    }
  }

  /**
   * Expensive mesh-precise sphere-vs-triangle collisions. Called at the tick rate from {@link
   * #updateMeshCollisions(float)}. Does the per-model world-space transform once (cached for the
   * duration of this tick), then broad-phases every (entity, model) pair with a single dot-product
   * before descending into the triangle loop.
   */
  private void handleMeshCollisions() {
    // Per-tick state: invalidate the transformed mesh cache so moved/rotated
    // models are re-transformed on demand.
    transformedMeshCache.clear();

    List<ModelEntity> models = new ArrayList<>();
    for (BasicEntity entity : entities) {
      if (entity instanceof ModelEntity modelEntity) {
        models.add(modelEntity);
      }
    }

    // FIX: broad-phase sphere-vs-sphere test is done HERE in the outer loop
    //      before any per-triangle work or cache population. Most (entity,
    //      model) pairs bail out after a single dot product.
    for (BasicEntity entity : entities) {
      if (!entity.isRigidBodyEnabled() && !(entity instanceof PlayerCameraEntity)) continue;

      Vec3 entityPos = entity.getPosition();
      float camRadius = entity.getCollisionRadius();
      float camHalfHeight = entity.getCollisionHeight() * 0.5f;

      for (ModelEntity model : models) {
        if (entity == model) continue;

        Vec3 modelPos = model.getPosition();
        Vec3 modelCenterOffset = model.getModelCenter();
        float mcx = modelPos.x() + modelCenterOffset.x();
        float mcy = modelPos.y() + modelCenterOffset.y();
        float mcz = modelPos.z() + modelCenterOffset.z();

        float broadPhase = model.getModelBoundingRadius() + camHalfHeight + camRadius + 0.5f;
        float dx = entityPos.x() - mcx;
        float dy = entityPos.y() - mcy;
        float dz = entityPos.z() - mcz;
        if (dx * dx + dy * dy + dz * dz > broadPhase * broadPhase) continue;

        resolveSphereModelCollision(entity, model);
      }
    }
  }

  private void resolveSphereModelCollision(BasicEntity entity, ModelEntity model) {
    ObjModel modelData = model.getModelData();
    if (modelData == null || modelData.faces.isEmpty()) return;

    float camRadius = entity.getCollisionRadius();
    float camHalfHeight = entity.getCollisionHeight() * 0.5f;

    // FIX: Triangles are transformed into world-space ONCE per model per tick
    //      (cached across iterations and across all entities) instead of being
    //      re-transformed every iteration of every entity-model pair with six
    //      fresh trig calls per vertex.
    TransformedMesh mesh = getOrBuildTransformedMesh(model, modelData);
    float[] verts = mesh.verts;
    float[] triMin = mesh.triMin;
    float[] triMax = mesh.triMax;
    int faceCount = mesh.faceCount;

    float radiusSq = camRadius * camRadius;

    for (int iteration = 0; iteration < 3; iteration++) {
      boolean collided = false;

      Vec3 camPos = entity.getPosition();
      float cpx = camPos.x(), cpy = camPos.y(), cpz = camPos.z();
      float camBottomY = cpy - camHalfHeight + camRadius;
      float camCenterY = cpy;
      float camTopY = cpy + camHalfHeight - camRadius;

      float camMinX = cpx - camRadius;
      float camMaxX = cpx + camRadius;
      float camMinY = cpy - camHalfHeight;
      float camMaxY = cpy + camHalfHeight;
      float camMinZ = cpz - camRadius;
      float camMaxZ = cpz + camRadius;

      for (int f = 0; f < faceCount; f++) {
        int baseB = f * 3;
        float tMinX = triMin[baseB];
        float tMinY = triMin[baseB + 1];
        float tMinZ = triMin[baseB + 2];
        float tMaxX = triMax[baseB];
        float tMaxY = triMax[baseB + 1];
        float tMaxZ = triMax[baseB + 2];

        // Inline AABB test — no Vec3 allocation.
        if (camMaxX < tMinX || camMinX > tMaxX) continue;
        if (camMaxY < tMinY || camMinY > tMaxY) continue;
        if (camMaxZ < tMinZ || camMinZ > tMaxZ) continue;

        int baseV = f * 9;
        Vec3 a = new Vec3(verts[baseV], verts[baseV + 1], verts[baseV + 2]);
        Vec3 b = new Vec3(verts[baseV + 3], verts[baseV + 4], verts[baseV + 5]);
        Vec3 c = new Vec3(verts[baseV + 6], verts[baseV + 7], verts[baseV + 8]);

        // Test each of the three vertical samples.
        for (int si = 0; si < 3; si++) {
          float scy;
          if (si == 0) scy = camBottomY;
          else if (si == 1) scy = camCenterY;
          else scy = camTopY;

          Vec3 sampleCenter = new Vec3(cpx, scy, cpz);
          Vec3 closest = closestPointOnTriangle(sampleCenter, a, b, c);
          Vec3 delta = sampleCenter.sub(closest);
          float distSq = delta.dot(delta);
          if (distSq < radiusSq) {
            float dist = (float) Math.sqrt(Math.max(distSq, 0.000001f));
            Vec3 normal = dist > 0.0001f ? delta.div(dist) : computeTriangleNormal(a, b, c);
            float pushOut = camRadius - dist + 0.001f;

            Vec3 newPos =
                entity.getPosition().add(new Vec3(normal.x() * pushOut, 0f, normal.z() * pushOut));
            entity.setPosition(newPos);

            Vec3 currentVel = entity.getVelocity();
            float inwardSpeed = currentVel.x() * normal.x() + currentVel.z() * normal.z();
            if (inwardSpeed < 0f) {
              entity.setVelocity(
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
