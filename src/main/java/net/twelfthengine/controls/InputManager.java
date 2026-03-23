package net.twelfthengine.controls;

import org.lwjgl.glfw.GLFW;

public class InputManager {

    private static long window;

    private static final boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] keysLast = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private static final boolean[] mouseButtons = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
    private static final boolean[] mouseButtonsLast = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];

    private static double mouseX, mouseY;
    private static double lastMouseX, lastMouseY;
    private static double deltaX, deltaY;

    private InputManager() {}

    // =========================
    // INIT
    // =========================

    public static void init(long glfwWindow) {
        window = glfwWindow;
    }

    // =========================
    // UPDATE (CALL EVERY FRAME)
    // =========================

    public static void update() {

        // ---- Keys ----
        for (int i = 0; i < keys.length; i++) {
            if (i < GLFW.GLFW_KEY_SPACE) continue; // skip invalid
            keysLast[i] = keys[i];
            keys[i] = GLFW.glfwGetKey(window, i) == GLFW.GLFW_PRESS;
        }

        // ---- Mouse Buttons ----
        for (int i = 0; i < mouseButtons.length; i++) {
            mouseButtonsLast[i] = mouseButtons[i];
            mouseButtons[i] = GLFW.glfwGetMouseButton(window, i) == GLFW.GLFW_PRESS;
        }

        // ---- Mouse Position ----
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);

        mouseX = x[0];
        mouseY = y[0];

        deltaX = mouseX - lastMouseX;
        deltaY = mouseY - lastMouseY;
    }

    // =========================
    // KEY QUERIES
    // =========================

    public static boolean isKeyDown(int key) {
        return keys[key];
    }

    public static boolean isKeyPressed(int key) {
        return keys[key] && !keysLast[key];
    }

    public static boolean isKeyReleased(int key) {
        return !keys[key] && keysLast[key];
    }

    // =========================
    // MOUSE BUTTONS
    // =========================

    public static boolean isMouseDown(int button) {
        return mouseButtons[button];
    }

    public static boolean isMousePressed(int button) {
        return mouseButtons[button] && !mouseButtonsLast[button];
    }

    public static boolean isMouseReleased(int button) {
        return !mouseButtons[button] && mouseButtonsLast[button];
    }

    // =========================
    // MOUSE POSITION
    // =========================

    public static double getMouseX() {
        return mouseX;
    }

    public static double getMouseY() {
        return mouseY;
    }

    public static double getMouseDeltaX() {
        return deltaX;
    }

    public static double getMouseDeltaY() {
        return deltaY;
    }

    // =========================
    // CURSOR CONTROL (VERY USEFUL)
    // =========================

    public static void setCursorLocked(boolean locked) {
        GLFW.glfwSetInputMode(
                window,
                GLFW.GLFW_CURSOR,
                locked ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL
        );
    }
}