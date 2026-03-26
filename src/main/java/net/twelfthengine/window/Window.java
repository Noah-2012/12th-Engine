package net.twelfthengine.window;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public class Window {

  private long windowHandle;
  private final int width, height;
  private final String title;
  private final int id; // ID des Fensters
  private boolean mouseLocked = false; // Flag für Mausstatus
  private boolean isFullscreen = false; // Fullscreen state
  private int windowedX, windowedY, windowedWidth, windowedHeight; // Windowed mode state

  public Window(int id, int width, int height, String title) {
    this.id = id;
    this.width = width;
    this.height = height;
    this.title = title;
    this.windowedWidth = width;
    this.windowedHeight = height;
  }

  public void init() {
    init(0);
  }

  public void init(long shareContext) {
    GLFWErrorCallback.createPrint(System.err).set();

    if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

    GLFW.glfwDefaultWindowHints();
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
    GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, 4);

    windowHandle = GLFW.glfwCreateWindow(width, height, title, 0, shareContext);
    if (windowHandle == 0) throw new RuntimeException("Failed to create GLFW window");

    GLFW.glfwMakeContextCurrent(windowHandle);
    GL.createCapabilities(); // Das hier "schaltet OpenGL frei"

    // --- JETZT erst OpenGL Befehle aufrufen ---
    GL11.glEnable(GL11.GL_DEPTH_TEST); // WICHTIG: Damit 3D Objekte Tiefe haben
    GL11.glEnable(GL11.GL_LIGHTING);
    GL11.glEnable(GL11.GL_LIGHT0);

    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

    GL11.glEnable(GL11.GL_ALPHA_TEST);
    GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);

    // Das sorgt dafür, dass glColor3f deine OBJ-Materialfarben beeinflusst!
    GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

    // Ein bisschen Standard-Licht (Position oben links)
    float[] lightPosition = {5.0f, 5.0f, 5.0f, 1.0f};
    GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPosition);

    GLFW.glfwSwapInterval(1);
    GLFW.glfwShowWindow(windowHandle);

    System.out.println(
        "[Window] OpenGL Window created: ID=" + id + " Size=" + width + "x" + height);
  }

  // Maus innerhalb des Fensters sperren
  public void lockMouse() {
    if (!mouseLocked) {
      GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
      mouseLocked = true;
    }
  }

  // Prüfen ob das Fenster geschlossen werden soll
  public boolean shouldClose() {
    return GLFW.glfwWindowShouldClose(windowHandle);
  }

  // Fenster schließen und Maus wieder freigeben
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

  /** OpenGL framebuffer size (may differ from logical size on HiDPI). */
  public int getFramebufferWidth() {
    int[] w = new int[1];
    GLFW.glfwGetFramebufferSize(windowHandle, w, new int[1]);
    return w[0];
  }

  public int getFramebufferHeight() {
    int[] h = new int[1];
    GLFW.glfwGetFramebufferSize(windowHandle, new int[1], h);
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
      // Exit fullscreen - restore windowed mode
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
      // Enter fullscreen - save current windowed state and go fullscreen
      int[] xPos = new int[1];
      int[] yPos = new int[1];
      GLFW.glfwGetWindowPos(windowHandle, xPos, yPos);
      int[] wPos = new int[1];
      int[] hPos = new int[1];
      GLFW.glfwGetWindowSize(windowHandle, wPos, hPos);

      windowedX = xPos[0];
      windowedY = yPos[0];
      windowedWidth = wPos[0];
      windowedHeight = hPos[0];

      // Get the primary monitor and its video mode
      long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
      var videoMode = GLFW.glfwGetVideoMode(primaryMonitor);

      // Set fullscreen mode with native resolution
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

  public void requestClose() {
    GLFW.glfwSetWindowShouldClose(windowHandle, true);
  }
}
