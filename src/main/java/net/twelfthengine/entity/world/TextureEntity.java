package net.twelfthengine.entity.world;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.math.Vec3;

public class TextureEntity extends BasicEntity implements net.twelfthengine.renderer.Renderable3D {

  @Override
  public void renderLit(
      net.twelfthengine.renderer.Renderer3D renderer,
      net.twelfthengine.renderer.shader.ShaderProgram shader,
      org.joml.Matrix4f view,
      org.joml.Matrix4f proj,
      org.joml.Matrix4f lightSpace) {
    String path = this.getTexturePath();
    if (path == null || path.isEmpty()) return;
    int texId = renderer.loadTextureId(path);

    org.joml.Matrix4f model =
        new org.joml.Matrix4f()
            .translate(this.getPosition().x(), this.getPosition().y(), this.getPosition().z())
            .rotateY((float) Math.toRadians(this.getRotation().y()))
            .rotateX((float) Math.toRadians(this.getRotation().x()))
            .scale(this.getWidth(), this.getHeight(), 1f);

    renderer.getTextureMesh().drawLit(shader, model, view, proj, lightSpace, texId);
  }

  @Override
  public void renderShadow(
      net.twelfthengine.renderer.Renderer3D renderer,
      net.twelfthengine.renderer.shader.ShaderProgram depthShader,
      org.joml.Matrix4f lightSpace) {
    org.joml.Matrix4f model =
        new org.joml.Matrix4f()
            .translate(this.getPosition().x(), this.getPosition().y(), this.getPosition().z())
            .rotateY((float) Math.toRadians(this.getRotation().y()))
            .rotateX((float) Math.toRadians(this.getRotation().x()))
            .scale(this.getWidth(), this.getHeight(), 1f);
    org.joml.Matrix4f mvp = new org.joml.Matrix4f(lightSpace).mul(model);
    renderer.getTextureMesh().drawDepth(depthShader, mvp);
  }

  private String texturePath;
  private float width;
  private float height;
  private boolean faceCamera = true;

  public TextureEntity(float x, float y, float z, String texturePath, float width, float height) {
    super(x, y, z);
    this.texturePath = texturePath;
    this.width = width;
    this.height = height;

    this.rigidBodyEnabled = false;
    this.collidable = false;
    this.affectedByGravity = false;
  }

  @Override
  public void update(float deltaTime) {
    super.update(deltaTime);
  }

  public void faceCamera(CameraEntity cam) {
    if (!faceCamera || cam == null) {
      return;
    }
    Vec3 dir = cam.getPosition().sub(this.getPosition()).normalize();
    float yaw = (float) Math.toDegrees(Math.atan2(dir.x(), dir.z()));
    float pitch = (float) Math.toDegrees(Math.asin(-dir.y()));
    this.setRotation(new Vec3(pitch, yaw, 0));
  }

  public String getTexturePath() {
    return texturePath;
  }

  public void setTexturePath(String texturePath) {
    this.texturePath = texturePath;
  }

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    this.width = width;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    this.height = height;
  }

  public boolean isFaceCamera() {
    return faceCamera;
  }

  public void setFaceCamera(boolean faceCamera) {
    this.faceCamera = faceCamera;
  }
}
