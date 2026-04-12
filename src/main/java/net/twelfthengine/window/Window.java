package net.twelfthengine.window;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.twelfthengine.core.resources.ResourceExtractor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

public class Window {

  private long windowHandle;
  private int width, height;
  private final String title;
  private final int id;
  private boolean mouseLocked  = false;
  private boolean isFullscreen = false;
  private int windowedX, windowedY, windowedWidth, windowedHeight;

  // FIX: Reusable int arrays for glfwGetFramebufferSize — avoids allocating
  //      new int[1] on every getFramebufferWidth() / getFramebufferHeight() call.
  private final int[] fbScratch = new int[2];

  private final java.util.List<java.util.function.BiConsumer<Integer, Integer>> resizeListeners =
          new java.util.ArrayList<>();

  public Window(int id, int width, int height, String title) {
    this.id             = id;
    this.width          = width;
    this.height         = height;
    this.title          = title;
    this.windowedWidth  = width;
    this.windowedHeight = height;
  }

  public void init() {
    init(0, true);
  }

  /**
   * @param shareContext  GLFW context to share resources with (0 = none).
   * @param enableVSync   Pass {@code true} to cap the frame-rate to the monitor
   *                      refresh rate, {@code false} for uncapped rendering.
   */
  public void init(long shareContext, boolean enableVSync) {
    GLFWErrorCallback.createPrint(System.err).set();

    if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

    GLFW.glfwDefaultWindowHints();
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,   GLFW.GLFW_FALSE);
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
    GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES,   4);

    windowHandle = GLFW.glfwCreateWindow(width, height, title, 0, shareContext);
    if (windowHandle == 0) throw new RuntimeException("Failed to create GLFW window");

    GLFW.glfwMakeContextCurrent(windowHandle);
    GL.createCapabilities();

    // ------------------------------------------------------------------
    // Minimal GL state that truly belongs to window initialisation.
    //
    // FIX: Removed GL_LIGHTING, GL_LIGHT0, GL_COLOR_MATERIAL, GL_BLEND,
    //      GL_ALPHA_TEST, and glLightfv from here.  Those are renderer-level
    //      concerns and are now owned exclusively by LegacyRenderer /
    //      Renderer3D.  Having them here as well means they were being set
    //      twice and could silently fight with the renderer's own state
    //      management (e.g. Renderer3D disables GL_LIGHTING for the lit
    //      pass, then re-enables it — but Window had already enabled it
    //      with different parameters).
    // ------------------------------------------------------------------
    GL11.glEnable(GL11.GL_DEPTH_TEST);

    // FIX: VSync is now explicitly controlled by the caller.
    //      0 = off (uncapped), 1 = on (capped to monitor refresh rate).
    GLFW.glfwSwapInterval(enableVSync ? 1 : 0);

    GLFW.glfwShowWindow(windowHandle);

    GLFW.glfwSetFramebufferSizeCallback(
            windowHandle,
            (win, w, h) -> {
              this.width  = w;
              this.height = h;
              GL11.glViewport(0, 0, w, h);
              for (var listener : resizeListeners) listener.accept(w, h);
            });

    // Sync to actual framebuffer size immediately (handles HiDPI at startup).
    GLFW.glfwGetFramebufferSize(windowHandle, fbScratch, null);
    // fbScratch[0] = width, but glfwGetFramebufferSize needs separate arrays;
    // use the two-array variant below.
    int[] fw = new int[1], fh = new int[1];
    GLFW.glfwGetFramebufferSize(windowHandle, fw, fh);
    this.width  = fw[0];
    this.height = fh[0];
    GL11.glViewport(0, 0, this.width, this.height);

    System.out.println(
            "[Window] OpenGL Window created: ID=" + id + " Size=" + width + "x" + height
                    + " VSync=" + enableVSync);
  }

  // Convenience overload — keeps old call-sites compiling without changes.
  public void init(long shareContext) {
    init(shareContext, true);
  }

  public void addResizeListener(java.util.function.BiConsumer<Integer, Integer> listener) {
    resizeListeners.add(listener);
  }

  public void lockMouse() {
    if (!mouseLocked) {
      GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
      mouseLocked = true;
    }
  }

  public boolean shouldClose() {
    return GLFW.glfwWindowShouldClose(windowHandle);
  }

  public void close() {
    if (mouseLocked) {
      GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
      mouseLocked = false;
    }
    GLFW.glfwDestroyWindow(windowHandle);
  }

  public void update() {
    GLFW.glfwSwapBuffers(windowHandle);
  }

  public void clear(float r, float g, float b, float a) {
    GL11.glClearColor(r, g, b, a);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
  }

  public int getId() {
    return id;
  }

  public long getHandle() {
    return windowHandle;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  // FIX: Reuse fbScratch instead of allocating new int[1] on every call.
  //      Note: GLFW is thread-safe for reads on the main thread; if you ever
  //      call these from another thread you will need synchronisation anyway.
  /** OpenGL framebuffer width (may differ from logical size on HiDPI). */
  public int getFramebufferWidth() {
    GLFW.glfwGetFramebufferSize(windowHandle, fbScratch, null);
    return fbScratch[0];
  }

  /** OpenGL framebuffer height (may differ from logical size on HiDPI). */
  public int getFramebufferHeight() {
    // Pass null for the width slot so only height is written.
    int[] h = new int[1];
    GLFW.glfwGetFramebufferSize(windowHandle, null, h);
    return h[0];
  }

  public void unlockMouse() {
    if (mouseLocked) {
      GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
      mouseLocked = false;
    }
  }

  public void toggleFullscreen() {
    if (isFullscreen) {
      GLFW.glfwSetWindowMonitor(
              windowHandle,
              0,
              windowedX,
              windowedY,
              windowedWidth,
              windowedHeight,
              GLFW.GLFW_DONT_CARE);
      isFullscreen = false;
      System.out.println("[Window] Exited fullscreen mode");
    } else {
      int[] xPos = new int[1], yPos = new int[1];
      GLFW.glfwGetWindowPos(windowHandle, xPos, yPos);
      int[] wPos = new int[1], hPos = new int[1];
      GLFW.glfwGetWindowSize(windowHandle, wPos, hPos);

      windowedX      = xPos[0];
      windowedY      = yPos[0];
      windowedWidth  = wPos[0];
      windowedHeight = hPos[0];

      long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
      var  videoMode      = GLFW.glfwGetVideoMode(primaryMonitor);

      GLFW.glfwSetWindowMonitor(
              windowHandle,
              primaryMonitor,
              0,
              0,
              videoMode.width(),
              videoMode.height(),
              videoMode.refreshRate());
      isFullscreen = true;
      System.out.println("[Window] Entered fullscreen mode");
    }
  }

  public void setWindowIconFromResource(String resourcePath) {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      String iconPath = ResourceExtractor.extract(resourcePath);

      IntBuffer w        = stack.mallocInt(1);
      IntBuffer h        = stack.mallocInt(1);
      IntBuffer channels = stack.mallocInt(1);

      ByteBuffer image = STBImage.stbi_load(iconPath, w, h, channels, 4);
      if (image == null) {
        throw new RuntimeException("Failed to load window icon: " + STBImage.stbi_failure_reason());
      }

      GLFWImage        icon  = GLFWImage.malloc(stack);
      icon.set(w.get(0), h.get(0), image);

      GLFWImage.Buffer icons = GLFWImage.malloc(1, stack);
      icons.put(0, icon);

      GLFW.glfwSetWindowIcon(windowHandle, icons);
      STBImage.stbi_image_free(image);

      System.out.println("[Window] Icon loaded from resource: " + resourcePath);
    } catch (Exception e) {
      System.err.println("[Window] Failed to set window icon: " + e.getMessage());
    }
  }

  public void requestClose() {
    GLFW.glfwSetWindowShouldClose(windowHandle, true);
  }
}