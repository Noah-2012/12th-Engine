package net.twelfthengine.entity;

import java.io.IOException;
import net.twelfthengine.core.resources.TwelfthPackage;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.Renderable3D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.obj.ObjLoader;
import net.twelfthengine.renderer.obj.ObjModel;
import net.twelfthengine.renderer.obj.VboModel;
import net.twelfthengine.renderer.shader.ShaderProgram;
import org.joml.Matrix4f;

public class ModelEntity extends BasicEntity implements Renderable3D {

  private String modelPath;
  private String archivePath; // Internal path of the OBJ inside the TWA
  private float size = 1.0f;
  private ObjModel cachedModel = null;
  private TwelfthPackage twmPackage = null;

  public ModelEntity(float x, float y, float z, String modelPath) {
    super(x, y, z);
    this.modelPath = modelPath;
    loadModelData();
  }

  public ModelEntity(float x, float y, float z, String archivePath, TwelfthPackage twmPackage) {
    super(x, y, z);
    this.archivePath = archivePath;
    this.twmPackage = twmPackage;
    loadModelDataFromPackage();
  }

  private void loadModelData() {
    try {
      this.cachedModel = ObjLoader.load(modelPath);
    } catch (IOException e) {
      System.err.println("Failed to load model for physics: " + modelPath);
      this.cachedModel = null;
    }
  }

  private void loadModelDataFromPackage() {
    try {
      this.cachedModel = ObjLoader.loadFromPackage(twmPackage, archivePath);
    } catch (IOException e) {
      System.err.println("Failed to load packaged model for physics: " + archivePath);
      this.cachedModel = null;
    }
  }

  public String getModelPath() {
    return modelPath;
  }

  public void setModelPath(String path) {
    this.modelPath = path;
    loadModelData();
  }

  // Size methods
  public float getSize() {
    return size;
  }

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

  public Vec3 getModelMaxBounds() {
    if (cachedModel != null) {
      return cachedModel.getMaxBounds().mul(size);
    }
    return new Vec3(0.5f, 0.5f, 0.5f); // Default fallback
  }

  @Override
  public Vec3 getMinBounds() {
    return position.add(getModelMinBounds());
  }

  @Override
  public Vec3 getMaxBounds() {
    return position.add(getModelMaxBounds());
  }

  public Vec3 getModelCenter() {
    if (cachedModel != null) {
      return cachedModel.getCenter().mul(size);
    }
    return new Vec3(0, 0, 0); // Default fallback
  }

  @Override
  public void renderLit(
      Renderer3D renderer,
      ShaderProgram shader,
      Matrix4f view,
      Matrix4f proj,
      Matrix4f lightSpace) {
    VboModel vbo;
    ObjModel obj;

    if (twmPackage != null) {
      String cacheKey = twmPackage.getArchiveName() + ":" + archivePath;
      vbo = renderer.loadVboModelFromPackage(twmPackage, archivePath);
      obj = renderer.loadObjModelFromPackage(twmPackage, archivePath);
    } else {
      if (modelPath == null || modelPath.isEmpty()) return;
      vbo = renderer.loadVboModel(modelPath);
      obj = renderer.loadObjModel(modelPath);
    }

    if (vbo == null || obj == null) return;
    Matrix4f model = renderer.modelMatrixForModel(this, vbo);
    vbo.renderLit(obj, shader, model, view, proj, lightSpace);
  }

  @Override
  public void renderShadow(Renderer3D renderer, ShaderProgram depthShader, Matrix4f lightSpace) {
    VboModel vbo;
    if (twmPackage != null) {
      vbo = renderer.loadVboModelFromPackage(twmPackage, archivePath);
    } else {
      if (modelPath == null || modelPath.isEmpty()) return;
      vbo = renderer.loadVboModel(modelPath);
    }
    if (vbo == null) return;

    Matrix4f model = renderer.modelMatrixForModel(this, vbo);
    // FIX: reuse Renderer3D's MVP scratch instead of `new Matrix4f(lightSpace)`
    //      every entity every shadow pass. `.set(...)` copies lightSpace in,
    //      `.mul(model)` applies the multiplication in place.
    Matrix4f mvp = renderer.getLightMvpScratch().set(lightSpace).mul(model);
    vbo.renderDepth(depthShader, mvp);
  }
}
