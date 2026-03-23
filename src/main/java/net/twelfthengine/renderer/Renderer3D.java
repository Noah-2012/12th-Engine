package net.twelfthengine.renderer;

import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.math.MatrixStack3D;
import net.twelfthengine.world.World;
import net.twelfthengine.coord.iab.IAB;
import net.twelfthengine.math.Mat4;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.mesh.PlaneMesh;
import net.twelfthengine.renderer.mesh.UnitCubeMesh;
import net.twelfthengine.renderer.obj.ObjLoader;
import net.twelfthengine.renderer.obj.ObjModel;
import net.twelfthengine.renderer.obj.VboModel;
import net.twelfthengine.renderer.shader.ShaderProgram;
import net.twelfthengine.renderer.shadow.ShadowFramebuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class Renderer3D {

    private final int width;
    private final int height;
    private float fovDegrees = 90f;
    private final MatrixStack3D modelStack = new MatrixStack3D();

    // Cache for loaded models to avoid reloading
    private final Map<String, ObjModel> modelCache = new HashMap<>();
    private final Map<String, VboModel> vboCache = new HashMap<>();

    private boolean shadowsEnabled;
    private ShaderProgram depthShader;
    private ShaderProgram litShader;
    private ShadowFramebuffer shadowFbo;
    private UnitCubeMesh unitCubeMesh;
    private PlaneMesh planeMesh;

    public MatrixStack3D getMatrices() {
        return modelStack;
    }

    public void setFovDegrees(float fovDegrees) {
        this.fovDegrees = Math.max(30f, Math.min(150f, fovDegrees));
    }

    public Renderer3D(int width, int height) {
        this.width = width;
        this.height = height;
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // Einfaches Licht-Setup
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL); // Damit setColor() weiterhin funktioniert

        // Position des Lichts (W=1.0 für Positionslicht, W=0.0 für gerichtetes Licht wie Sonne)
        float[] lightPos = {0f, 10f, 10f, 1.0f};
        java.nio.FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(4).put(lightPos);
        buffer.flip();
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, buffer);

        try {
            depthShader = new ShaderProgram("/shaders/shadow_depth.vert", "/shaders/shadow_depth.frag");
            litShader = new ShaderProgram("/shaders/lit_shadow.vert", "/shaders/lit_shadow.frag");
            shadowFbo = new ShadowFramebuffer();
            unitCubeMesh = new UnitCubeMesh();
            planeMesh = new PlaneMesh();
            shadowsEnabled = true;
        } catch (Exception e) {
            System.err.println("[Renderer3D] Shader shadows disabled: " + e.getMessage());
            shadowsEnabled = false;
        }
    }

    // =============================
    // MODEL LOADING
    // =============================

    private ObjModel loadObjModel(String path) {
        if (!modelCache.containsKey(path)) {
            try {
                ObjModel model = ObjLoader.load(path);
                modelCache.put(path, model);
            } catch (IOException e) {
                System.err.println("Failed to load model: " + path);
                return null;
            }
        }
        return modelCache.get(path);
    }

    private VboModel loadVboModel(String path) {
        if (!vboCache.containsKey(path)) {
            ObjModel objModel = loadObjModel(path);
            if (objModel != null) {
                VboModel vboModel = new VboModel(objModel);
                vboCache.put(path, vboModel);
            }
        }
        return vboCache.get(path);
    }

    // =============================
    // MAIN RENDER ENTRY
    // =============================

    public void render(World world) {
        CameraEntity cam = world.getActiveCamera();
        LightEntity shadowLight = world.getPrimaryShadowLight();
        Matrix4f lightSpaceMatrix = (shadowsEnabled && shadowLight != null && shadowLight.isCastShadows())
                ? computeLightSpaceMatrix(shadowLight)
                : null;

        if (lightSpaceMatrix != null) {
            renderShadowPass(world, lightSpaceMatrix);
            shadowFbo.unbind(width, height);
        }

        begin3D(cam);

        if (lightSpaceMatrix != null) {
            renderLitScene(world, shadowLight, lightSpaceMatrix);
        } else {
            renderLegacyScene(world);
        }

        System.out.println("shadowsEnabled=" + shadowsEnabled + " lightSpaceMatrix=" + lightSpaceMatrix);
    }

    private Matrix4f computeLightSpaceMatrix(LightEntity light) {
        Vec3 p = light.getPosition();
        Vec3 f = light.getForwardDirection();
        Vec3 sceneCenter = new Vec3(0f, 0f, 0f);
        Matrix4f lightView = new Matrix4f().lookAt(
            p.x(), p.y(), p.z(),
            sceneCenter.x(), sceneCenter.y(), sceneCenter.z(),
            0f, 1f, 0f);
        float s = light.getShadowOrthoHalfSize();
        Matrix4f lightProj = new Matrix4f().ortho(-s, s, -s, s, light.getShadowNear(), light.getShadowFar());
        return new Matrix4f(lightProj).mul(lightView);
    }

    private void renderShadowPass(World world, Matrix4f lightSpace) {
        shadowFbo.bindForShadowPass();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_FRONT);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(4f, 16f);

        depthShader.use();

        for (BasicEntity e : world.getEntities()) {
            if (e instanceof LightEntity || e instanceof CameraEntity) continue;
            if (e instanceof BasicPlaneEntity plane) {
                Matrix4f model = modelMatrixForPlane(plane);
                Matrix4f mvp = new Matrix4f(lightSpace).mul(model);
                planeMesh.drawDepth(depthShader, mvp);
            } else if (e instanceof ModelEntity me) {
                String path = me.getModelPath();
                if (path == null || path.isEmpty()) continue;
                VboModel vbo = loadVboModel(path);
                if (vbo == null) continue;
                Matrix4f model = modelMatrixForModel(me, vbo);
                Matrix4f mvp = new Matrix4f(lightSpace).mul(model);
                System.out.println("Rendering model to shadow map at: " + me.getPosition().x() + " " + me.getPosition().y() + " " + me.getPosition().z());
                System.out.println("Lit render position: " + me.getPosition().x() + " " + me.getPosition().y() + " " + me.getPosition().z());
                vbo.renderDepth(depthShader, mvp);
            }
        }

        depthShader.unbind();
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glCullFace(GL11.GL_BACK);

        //this is new 
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private Matrix4f modelMatrixForPlane(BasicPlaneEntity plane) {
        float y = plane.getTop();
        float w = plane.getWidth();
        float l = plane.getLength();
        return new Matrix4f()
                .translate(0f, y, 0f)
                .scale(w, 1f, l);
    }

    private Matrix4f modelMatrixForModel(ModelEntity me, VboModel vbo) {
        Vec3 p = me.getPosition();
        float s = me.getSize();
        return new Matrix4f().translate(p.x(), p.y(), p.z()).scale(s);
    }

    private void renderLitScene(World world, LightEntity light, Matrix4f lightSpace) {
        GL11.glDisable(GL11.GL_LIGHTING);
        litShader.use();

        FloatBuffer vb = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, vb);
        Matrix4f view = new Matrix4f(
            vb.get(0), vb.get(1), vb.get(2), vb.get(3),
            vb.get(4), vb.get(5), vb.get(6), vb.get(7),
            vb.get(8), vb.get(9), vb.get(10), vb.get(11),
            vb.get(12), vb.get(13), vb.get(14), vb.get(15)
        );

        FloatBuffer pb = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, pb);
        Matrix4f proj = new Matrix4f(
            pb.get(0), pb.get(1), pb.get(2), pb.get(3),
            pb.get(4), pb.get(5), pb.get(6), pb.get(7),
            pb.get(8), pb.get(9), pb.get(10), pb.get(11),
            pb.get(12), pb.get(13), pb.get(14), pb.get(15)
        );

        Vec3 toLight = light.getDirectionToLightWorld().mul(-1f);
        Vec3 lc = light.getColor();
        float inten = light.getIntensity();
        litShader.setUniform3f("uLightDirWorld", toLight.x(), toLight.y(), toLight.z());
        litShader.setUniform3f("uLightColor", lc.x() * inten, lc.y() * inten, lc.z() * inten);
        litShader.setUniform3f("uAmbient", 0.1f, 0.11f, 0.14f);
        litShader.setUniform1i("uDiffuseTex", 0);
        litShader.setUniform1i("uShadowMap", 1);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowFbo.getDepthTextureId());

        for (BasicEntity e : world.getEntities()) {
            if (e instanceof LightEntity || e instanceof CameraEntity) continue;
            if (e instanceof BasicPlaneEntity plane) {
                //Matrix4f model = modelMatrixForPlane(plane);
                //unitCubeMesh.drawLit(litShader, model, view, proj, lightSpace);
                // ^^^^^ This is really bad and wont work on the 4 Corner planes, we need to draw the plane itself, not a cube
                Matrix4f model = modelMatrixForPlane(plane);
                planeMesh.drawLit(litShader, model, view, proj, lightSpace);
            } else if (e instanceof ModelEntity me) {
                String path = me.getModelPath();
                if (path == null || path.isEmpty()) continue;
                VboModel vbo = loadVboModel(path);
                ObjModel obj = loadObjModel(path);
                if (vbo == null || obj == null) continue;
                Matrix4f model = modelMatrixForModel(me, vbo);
                vbo.renderLit(obj, litShader, model, view, proj, lightSpace);
            }
        }

        litShader.unbind();
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private void renderLegacyScene(World world) {
        for (BasicEntity e : world.getEntities()) {
            if (e instanceof LightEntity || e instanceof CameraEntity) continue;

            if (e instanceof BasicPlaneEntity plane) {
                /* 
                float y = plane.getTop();
                float w = plane.getWidth() / 2f;
                float l = plane.getLength() / 2f;
                setColor(0.7f, 0.7f, 0.7f, 1f);
                drawFilledBox(
                        new Vec3(-w, y - 0.05f, -l),
                        new Vec3(w, y, l)
                );
                */
                drawPlane(plane);
            } else if (e instanceof ModelEntity modelEntity) {
                renderModelEntity(modelEntity);
            }
        }
    }

    // =============================
    // MODEL ENTITY RENDERING
    // =============================

    private void renderModelEntity(ModelEntity modelEntity) {
        String modelPath = modelEntity.getModelPath();
        if (modelPath == null || modelPath.isEmpty()) return;

        VboModel vboModel = loadVboModel(modelPath);
        ObjModel objModel = loadObjModel(modelPath);

        if (vboModel == null || objModel == null) return;

        GL11.glPushMatrix();

        // Apply transformations
        Vec3 position = modelEntity.getPosition();
        GL11.glTranslatef(position.x(), position.y(), position.z());

        // Apply rotation if needed (you might want to add rotation to ModelEntity)
        // GL11.glRotatef(rotationAngle, rotationX, rotationY, rotationZ);

        // Apply scale based on size
        float size = modelEntity.getSize();
        GL11.glScalef(size, size, size);

        // Render the model
        vboModel.render(objModel);

        GL11.glPopMatrix();
    }

    // =============================
    // BEGIN FRAME
    // =============================

    public void begin3D(CameraEntity cam) {

        float aspect = (float) width / height;
        Mat4 proj = Mat4.perspective(fovDegrees, aspect, 0.1f, 1000f);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrixf(proj.m);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        applyCamera(cam);
    }

    // =============================
    // CAMERA
    // =============================

    private void applyCamera(CameraEntity cam) {

        Vec3 pos = cam.getPosition();

        GL11.glRotatef(cam.getPitch(), 1, 0, 0);
        GL11.glRotatef(cam.getYaw(), 0, 1, 0);
        GL11.glRotatef(cam.getRoll(), 0, 0, 1);

        GL11.glTranslatef(-pos.x(), -pos.y(), -pos.z());
    }

    // =============================
    // UTILITY: COLORS & STATE & ANTIALIASING
    // =============================

    public void setColor(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    public void setPointSize(float size) {
        GL11.glPointSize(size);
    }

    public void setLineWidth(float width) {
        GL11.glLineWidth(width);
    }

    public void setAntialiasing(boolean enabled) {
        if (enabled) {
            // Aktiviert das Glätten von Kanten (besonders für Linien und Punkte wichtig)
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glEnable(GL11.GL_POINT_SMOOTH);
            GL11.glEnable(GL11.GL_BLEND); // Blend benötigt für weiche Kanten
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Sagt der Grafikkarte, sie soll auf Qualität statt Geschwindigkeit setzen
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
        } else {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_POINT_SMOOTH);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    public void setMultisampling(boolean enabled) {
        // Funktioniert nur, wenn das Window mit Samples initialisiert wurde!
        if (enabled) {
            GL11.glEnable(0x809D); // GL_MULTISAMPLE
        } else {
            GL11.glDisable(0x809D);
        }
    }

    // =============================
    // DRAWING PRIMITIVES
    // =============================

    /** Draws a simple point in 3D space */
    public void drawPoint(Vec3 p) {
        GL11.glBegin(GL11.GL_POINTS);
        GL11.glVertex3f(p.x(), p.y(), p.z());
        GL11.glEnd();
    }

    public void drawLine(Vec3 a, Vec3 b) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(a.x(), a.y(), a.z());
        GL11.glVertex3f(b.x(), b.y(), b.z());
        GL11.glEnd();
    }

    /** Draws a wireframe cube at a specific position with a specific scale */
    public void drawWireCube(Vec3 pos, float size) {
        float s = size / 2f;
        GL11.glPushMatrix();
        GL11.glTranslatef(pos.x(), pos.y(), pos.z());

        GL11.glBegin(GL11.GL_LINE_LOOP); // Top
        GL11.glVertex3f(-s, s, -s); GL11.glVertex3f(s, s, -s);
        GL11.glVertex3f(s, s, s); GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP); // Bottom
        GL11.glVertex3f(-s, -s, -s); GL11.glVertex3f(s, -s, -s);
        GL11.glVertex3f(s, -s, s); GL11.glVertex3f(-s, -s, s);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES); // Verticals
        GL11.glVertex3f(-s, -s, -s); GL11.glVertex3f(-s, s, -s);
        GL11.glVertex3f(s, -s, -s); GL11.glVertex3f(s, s, -s);
        GL11.glVertex3f(s, -s, s); GL11.glVertex3f(s, s, s);
        GL11.glVertex3f(-s, -s, s); GL11.glVertex3f(-s, s, s);
        GL11.glEnd();

        GL11.glPopMatrix();
    }

    /** Draws a filled box (useful for simple blocks/entities) */
    public void drawFilledBox(Vec3 min, Vec3 max) {
        GL11.glBegin(GL11.GL_QUADS);
        // Front Face (Z+)
        GL11.glNormal3d(0.0, 0.0, 1.0);
        GL11.glVertex3f(min.x(), min.y(), max.z()); GL11.glVertex3f(max.x(), min.y(), max.z());
        GL11.glVertex3f(max.x(), max.y(), max.z()); GL11.glVertex3f(min.x(), max.y(), max.z());
        // Back Face (Z-)
        GL11.glNormal3d(0.0, 0.0, -1.0);
        GL11.glVertex3f(min.x(), min.y(), min.z()); GL11.glVertex3f(min.x(), max.y(), min.z());
        GL11.glVertex3f(max.x(), max.y(), min.z()); GL11.glVertex3f(max.x(), min.y(), min.z());
        // Top Face (Y+)
        GL11.glNormal3d(0.0, 1.0, 0.0);
        GL11.glVertex3f(min.x(), max.y(), min.z()); GL11.glVertex3f(min.x(), max.y(), max.z());
        GL11.glVertex3f(max.x(), max.y(), max.z()); GL11.glVertex3f(max.x(), max.y(), min.z());
        // Bottom Face (Y-)
        GL11.glNormal3d(0.0, -1.0, 0.0);
        GL11.glVertex3f(min.x(), min.y(), min.z()); GL11.glVertex3f(max.x(), min.y(), min.z());
        GL11.glVertex3f(max.x(), min.y(), max.z()); GL11.glVertex3f(min.x(), min.y(), max.z());
        // Right Face (X+)
        GL11.glNormal3d(1.0, 0.0, 0.0);
        GL11.glVertex3f(max.x(), min.y(), min.z()); GL11.glVertex3f(max.x(), max.y(), min.z());
        GL11.glVertex3f(max.x(), max.y(), max.z()); GL11.glVertex3f(max.x(), min.y(), max.z());
        // Left Face (X-)
        GL11.glNormal3d(-1.0, 0.0, 0.0);
        GL11.glVertex3f(min.x(), min.y(), min.z()); GL11.glVertex3f(min.x(), min.y(), max.z());
        GL11.glVertex3f(min.x(), max.y(), max.z()); GL11.glVertex3f(min.x(), max.y(), min.z());
        GL11.glEnd();
    }

    // =============================
    // DEBUG HELPERS
    // =============================

    /** Draws the world axes (X=Red, Y=Green, Z=Blue) */
    public void drawAxes(float length) {
        setLineWidth(2.0f);
        // X
        setColor(1, 0, 0, 1);
        drawLine(new Vec3(0,0,0), new Vec3(length, 0, 0));
        // Y
        setColor(0, 1, 0, 1);
        drawLine(new Vec3(0,0,0), new Vec3(0, length, 0));
        // Z
        setColor(0, 0, 1, 1);
        drawLine(new Vec3(0,0,0), new Vec3(0, 0, length));
        setColor(1, 1, 1, 1); // Reset to white
    }

    // =============================
    // DEBUG WORLD BOUNDS
    // =============================

    private void drawIAB(IAB box) {

        float w = box.getWidth() / 2f;
        float h = box.getHeight() / 2f;
        float d = box.getDepth() / 2f;

        Vec3[] p = {
                new Vec3(-w,-h,-d),
                new Vec3(w,-h,-d),
                new Vec3(w,h,-d),
                new Vec3(-w,h,-d),

                new Vec3(-w,-h,d),
                new Vec3(w,-h,d),
                new Vec3(w,h,d),
                new Vec3(-w,h,d)
        };

        int[][] edges = {
                {0,1},{1,2},{2,3},{3,0},
                {4,5},{5,6},{6,7},{7,4},
                {0,4},{1,5},{2,6},{3,7}
        };

        for (int[] e : edges)
            drawLine(p[e[0]], p[e[1]]);
    }


    public void drawPlane(BasicPlaneEntity plane) {
        float w = plane.getWidth() / 2f;
        float l = plane.getLength() / 2f;
        float y = plane.getTop();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0f, 1f, 0f); // Oberseite zeigt nach oben
        GL11.glVertex3f(-w, y, -l);
        GL11.glVertex3f(w, y, -l);
        GL11.glVertex3f(w, y, l);
        GL11.glVertex3f(-w, y, l);
        GL11.glEnd();
    }
}
