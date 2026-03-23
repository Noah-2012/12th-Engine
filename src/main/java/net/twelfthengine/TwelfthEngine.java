package net.twelfthengine;

import net.twelfthengine.controls.InputManager;
import net.twelfthengine.coord.iab.IAB;
import net.twelfthengine.core.EngineObject;
import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.core.tick.TickManager;
import net.twelfthengine.core.tick.TickPhase;
import net.twelfthengine.core.tick.TickProfiler;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.gui.PauseMenuScreen;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.camera.PlayerCameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.LightEntity;
import net.twelfthengine.math.Vec3;
import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.Renderer3D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.renderer.pipeline.RenderContext;
import net.twelfthengine.renderer.pipeline.RenderLayer;
import net.twelfthengine.renderer.pipeline.RenderPipeline;
import net.twelfthengine.test.TickTest;
import net.twelfthengine.window.Window;
import net.twelfthengine.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class TwelfthEngine {
    private static final double TICK_RATE = 20.0;
    private static final double TICK_TIME = 1.0 / TICK_RATE;
    private static int TICK_COUNTER = 0;
    private static volatile int CURRENT_TPS = 0;
    private static volatile int CURRENT_FPS = 0;
    private static volatile boolean SIMULATE_LAG = false;
    private static final int LAG_DELAY_MS = 200;

    public static void main(String[] args) {

        EngineObject engine = EngineObject.getInstance();
        engine.start();
        Logger.info("Startup", "Starting EngineObject");

        TickManager tickManager = engine.getTickManager();
        TickTest.register();
        Logger.info("Startup", "Starting TickManager");

        Window window = new Window(0, 1920, 1080, "12th Engine");
        window.init();
        window.lockMouse();
        InputManager.init(window.getHandle());
        Logger.info("Startup", "Initialize InputManager and Window");

        Renderer2D renderer = new Renderer2D(window);
        Renderer3D r3d = new Renderer3D(1920, 1080);
        TextRenderer textRenderer = new TextRenderer();
        RenderPipeline renderPipeline = new RenderPipeline();
        r3d.setFovDegrees(95f);

        CameraEntity cam = new PlayerCameraEntity(0f, 5f, 5f);
        cam.setPosition(new Vec3(0, 5, 0));
        cam.setRotation(0, 0, 0);
        Logger.info("Startup", "Initialize Renderer and make a Camera");

        World world = new World(new IAB(1000, 1000, 1000));
        engine.setWorld(world);
        world.addEntity(cam);
        world.setActiveCamera(cam);
        Logger.info("Startup", "Creating new World with new IAB");

        r3d.setAntialiasing(true);
        r3d.setMultisampling(true);

        world.addEntity(new BasicPlaneEntity(0, -3, 0, 50, 50));

        LightEntity mainLight = new LightEntity(28f, 42f, 18f);
        mainLight.setRotation(-48f, -32f, 0f);
        mainLight.setColor(1f, 0.96f, 0.88f);
        mainLight.setIntensity(1.2f);
        mainLight.setShadowOrthoHalfSize(60f);
        mainLight.setCastShadows(true);
        mainLight.setShadowNear(1f);
        mainLight.setShadowFar(200f);
        world.addEntity(mainLight);

        Logger.showConsole();

        // Your existing initialization code...
        Logger.info("Startup", "Starting 12th Engine v1.0");

        // --- OBJ ENTITY SETUP ---
        /*
        ModelEntity treeEntity1 = new ModelEntity(0, -2, 0, "/models/tree/Tree.obj");
        ModelEntity treeEntity2 = new ModelEntity(3, -2, 0, "/models/tree/Tree.obj");
        ModelEntity treeEntity3 = new ModelEntity(6, -2, 0, "/models/tree/Tree.obj");
        ModelEntity treeEntity4 = new ModelEntity(9, -2, 0, "/models/tree/Tree.obj");
        */
        ModelEntity bombEntity = new ModelEntity(-6, 600, 0, "/models/6ovcmof8fc56.obj");
        ModelEntity bombEntity2 = new ModelEntity(-8, 500, 0, "/models/6ovcmof8fc56.obj");
        ModelEntity bombEntity3 = new ModelEntity(-10, 400, 0, "/models/6ovcmof8fc56.obj");


        // Set sizes
        /*
        treeEntity1.setSize(1.0f);
        treeEntity2.setSize(1.0f);
        treeEntity3.setSize(1.0f);
        treeEntity4.setSize(1.0f);

         */
        bombEntity.setSize(1.0f);
        bombEntity2.setSize(1.0f);
        bombEntity3.setSize(1.0f);

        // Enable physics for bomb
        bombEntity.enableRigidbody();
        bombEntity.setMass(1.0f);
        bombEntity.setDrag(0.999f);
        bombEntity.setGravity(19.62f);
        
        bombEntity2.enableRigidbody();
        bombEntity2.setMass(1.0f);
        bombEntity2.setDrag(0.999f);
        bombEntity2.setGravity(19.62f);

        bombEntity3.enableRigidbody();
        bombEntity3.setMass(1.0f);
        bombEntity3.setDrag(0.999f);
        bombEntity3.setGravity(19.62f);


        // Add entities to world
        /*
        world.addEntity(treeEntity1);
        world.addEntity(treeEntity2);
        world.addEntity(treeEntity3);
        world.addEntity(treeEntity4);

         */
        world.addEntity(bombEntity);
        world.addEntity(bombEntity2);
        world.addEntity(bombEntity3);

        // --- Render pipeline setup ---
        renderPipeline.addStep(RenderLayer.OPAQUE_3D, ctx -> {
            ctx.renderer3D().render(ctx.world());
        });
        renderPipeline.addStep(RenderLayer.DEBUG_3D, ctx -> {
            ctx.renderer3D().setColor(1, 0, 0, 1);
            ctx.renderer3D().drawWireCube(new Vec3(-1, -1, -1), 3f);

            ctx.renderer3D().setColor(0, 0, 1, 1);
            ctx.renderer3D().drawFilledBox(new Vec3(5, 5, 5), new Vec3(3, 3, 3));
        });
        final boolean[] showDebugOverlay = {false};
        final int graphSamples = 100;
        final int[] fpsHistory = new int[graphSamples];
        final int[] tpsHistory = new int[graphSamples];
        final int[] graphIndex = {0};
        final long[] lastGraphSampleNano = {System.nanoTime()};
        final double graphSampleStepSeconds = 0.10;

        final boolean[] paused = {false};
        final PauseMenuScreen pauseMenu = new PauseMenuScreen();

        renderPipeline.addStep(RenderLayer.UI_2D, ctx -> {
            int sw = window.getWidth();
            int sh = window.getHeight();
            int centerX = sw / 2;
            int centerY = sh / 2;
            int size = 8;
            float hudFontSize = 2f;
            String tpsText = "TPS: " + CURRENT_TPS;
            String tickText = "Ticks: " + TICK_COUNTER;

            if (paused[0]) {
                pauseMenu.captureBackdrop(window);
                pauseMenu.layout(sw, sh);
                pauseMenu.drawBackdropBlurAndDim(ctx.renderer2D(), sw, sh);
                double mx = InputManager.getMouseX();
                double my = InputManager.getMouseY();
                pauseMenu.drawButtons(window, ctx.renderer2D(), textRenderer, mx, my);
            } else {
                ctx.renderer2D().setColor(1f, 1f, 1f, 1f);
                ctx.renderer2D().drawLine(centerX - size, centerY, centerX + size, centerY);
                ctx.renderer2D().drawLine(centerX, centerY - size, centerX, centerY + size);

                float textWidth = textRenderer.getTextWidth(tpsText, hudFontSize);
                float textX = sw - textWidth - 16f;
                float textY = sh - 20f;
                textRenderer.drawText2D(tpsText, textX, textY, hudFontSize, 1f, 1f, 1f, 1f);

                float tickTextX = 16f;
                float tickTextY = sh - 20f;
                textRenderer.drawText2D(tickText, tickTextX, tickTextY, hudFontSize, 1f, 1f, 1f, 1f);
            }

            if (showDebugOverlay[0] && !paused[0]) {
                float debugScale = 1.4f;
                float infoY = 16f;
                Vec3 p = cam.getPosition();

                textRenderer.drawText2D("12th Engine Debug (F3)", 16f, infoY, debugScale, 0.9f, 1f, 0.9f, 1f);
                infoY += 18f;
                textRenderer.drawText2D(String.format(Locale.US, "Pos: X %.2f  Y %.2f  Z %.2f", p.x(), p.y(), p.z()),
                        16f, infoY, debugScale, 1f, 1f, 1f, 1f);
                infoY += 16f;
                textRenderer.drawText2D(String.format(Locale.US, "Rot: Pitch %.2f  Yaw %.2f  Roll %.2f",
                                cam.getPitch(), cam.getYaw(), cam.getRoll()),
                        16f, infoY, debugScale, 1f, 1f, 1f, 1f);
                infoY += 16f;
                textRenderer.drawText2D("FPS: " + CURRENT_FPS + " | TPS: " + CURRENT_TPS +
                                " | Entities: " + world.getEntities().size(),
                        16f, infoY, debugScale, 1f, 1f, 1f, 1f);
                infoY += 16f;
                textRenderer.drawText2D(String.format(Locale.US, "Frame: %.2f ms | Tick: %.2f ms | LagSim: %s",
                                CURRENT_FPS > 0 ? 1000f / CURRENT_FPS : 0f,
                                CURRENT_TPS > 0 ? 1000f / CURRENT_TPS : 0f,
                                SIMULATE_LAG ? "ON" : "OFF"),
                        16f, infoY, debugScale, 1f, 1f, 1f, 1f);

                int graphX = 16;
                int graphY = 104;
                int graphW = 520;
                int graphH = 120;
                int maxFpsScale = 240;
                int maxTpsScale = 20;

                ctx.renderer2D().setColor(0f, 0f, 0f, 0.45f);
                ctx.renderer2D().drawRect(graphX - 4, graphY - 4, graphW + 8, graphH + 28);

                ctx.renderer2D().setColor(0.25f, 0.25f, 0.25f, 1f);
                ctx.renderer2D().drawLine(graphX, graphY + graphH, graphX + graphW, graphY + graphH);
                ctx.renderer2D().drawLine(graphX, graphY, graphX, graphY + graphH);
                ctx.renderer2D().drawLine(graphX + graphW, graphY, graphX + graphW, graphY + graphH);

                for (int i = 0; i < graphSamples - 1; i++) {
                    int idxA = (graphIndex[0] + i) % graphSamples;
                    int idxB = (graphIndex[0] + i + 1) % graphSamples;

                    int x1 = graphX + (i * graphW) / (graphSamples - 1);
                    int x2 = graphX + ((i + 1) * graphW) / (graphSamples - 1);

                    int fps1 = Math.min(maxFpsScale, fpsHistory[idxA]);
                    int fps2 = Math.min(maxFpsScale, fpsHistory[idxB]);
                    int yFps1 = graphY + graphH - (fps1 * graphH / maxFpsScale);
                    int yFps2 = graphY + graphH - (fps2 * graphH / maxFpsScale);
                    ctx.renderer2D().setColor(0.2f, 0.75f, 1f, 1f);
                    ctx.renderer2D().drawLine(x1, yFps1, x2, yFps2);

                    int tps1 = Math.min(maxTpsScale, tpsHistory[idxA]);
                    int tps2 = Math.min(maxTpsScale, tpsHistory[idxB]);
                    int yTps1 = graphY + graphH - (tps1 * graphH / maxTpsScale);
                    int yTps2 = graphY + graphH - (tps2 * graphH / maxTpsScale);
                    ctx.renderer2D().setColor(0.35f, 1f, 0.35f, 1f);
                    ctx.renderer2D().drawLine(x1, yTps1, x2, yTps2);
                }

                textRenderer.drawText2D("Profiler Graph: TPS(left scale 0-20) / FPS(right scale 0-240)",
                        graphX, graphY + graphH + 8, 1.0f, 1f, 1f, 1f, 1f);
            }
        });

        new Thread(() -> {
            long lastTickTime = System.nanoTime();
            long tpsWindowStart = System.nanoTime();
            int ticksInWindow = 0;
            TickProfiler profiler = new TickProfiler(TICK_RATE);

            while (EngineObject.getInstance().isRunning()) {
                long now = System.nanoTime();
                double elapsed = (now - lastTickTime) / 1_000_000_000.0;

                if (elapsed >= TICK_TIME) {
                    if (SIMULATE_LAG) {
                        try {
                            Thread.sleep(LAG_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    TICK_COUNTER++;
                    ticksInWindow++;
                    profiler.startTick();
                    tickManager.fire(TickPhase.PRE, TICK_TIME);
                    Vec3 toLight = mainLight.getDirectionToLightWorld();
                    System.out.println("LightDir: " + toLight.x() + " " + toLight.y() + " " + toLight.z());
                    tickManager.fire(TickPhase.POST, TICK_TIME);
                    profiler.endTick();
                    lastTickTime = now;
                }

                double tpsWindowElapsed = (now - tpsWindowStart) / 1_000_000_000.0;
                if (tpsWindowElapsed >= 1.0) {
                    CURRENT_TPS = ticksInWindow;
                    ticksInWindow = 0;
                    tpsWindowStart = now;
                }
                profiler.update();
            }
        }).start();

        long lastFrameTime = System.nanoTime();
        long fpsWindowStart = System.nanoTime();
        int framesInWindow = 0;

        while (!window.shouldClose()) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f;
            lastFrameTime = currentTime;
            framesInWindow++;

            GLFW.glfwPollEvents();
            InputManager.update();
            if (InputManager.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                paused[0] = !paused[0];
                if (paused[0]) {
                    window.unlockMouse();
                } else {
                    window.lockMouse();
                }
            }
            if (paused[0] && InputManager.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                PauseMenuScreen.Action a = pauseMenu.handleClick(window, InputManager.getMouseX(), InputManager.getMouseY());
                if (a == PauseMenuScreen.Action.RESUME) {
                    paused[0] = false;
                    window.lockMouse();
                } else if (a == PauseMenuScreen.Action.EXIT) {
                    window.requestClose();
                }
            }
            if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F3)) {
                showDebugOverlay[0] = !showDebugOverlay[0];
            }
            if (InputManager.isKeyPressed(GLFW.GLFW_KEY_F11)) {
                window.toggleFullscreen();
            }
            if (InputManager.isKeyPressed(GLFW.GLFW_KEY_L)) {
                SIMULATE_LAG = !SIMULATE_LAG;
                Logger.info("TickLag", "Simulated lag " + (SIMULATE_LAG ? "enabled" : "disabled")
                        + " (" + LAG_DELAY_MS + "ms delay per tick)");
            }

            double fpsElapsed = (currentTime - fpsWindowStart) / 1_000_000_000.0;
            if (fpsElapsed >= 1.0) {
                CURRENT_FPS = framesInWindow;
                framesInWindow = 0;
                fpsWindowStart = currentTime;
            }

            double graphElapsed = (currentTime - lastGraphSampleNano[0]) / 1_000_000_000.0;
            if (graphElapsed >= graphSampleStepSeconds) {
                fpsHistory[graphIndex[0]] = CURRENT_FPS;
                tpsHistory[graphIndex[0]] = CURRENT_TPS;
                graphIndex[0] = (graphIndex[0] + 1) % graphSamples;
                lastGraphSampleNano[0] = currentTime;
            }

            if (!paused[0]) {
                engine.getWorld().update(deltaTime);
            }
            renderPipeline.renderFrame(new RenderContext(window, engine.getWorld(), renderer, r3d));
            window.update();
        }

        pauseMenu.dispose();
        window.close();
        engine.stop();
        Logger.info("Window", "Window closed, Engine continues running!");
    }
}
