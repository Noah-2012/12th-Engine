package net.twelfthengine.gui;

import net.twelfthengine.renderer.Renderer2D;
import net.twelfthengine.renderer.TextRenderer;
import net.twelfthengine.window.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * ESC pause overlay: blurred backdrop (multi-sample copy + offset passes) and rounded buttons.
 */
public class PauseMenuScreen {

    public enum Action {
        NONE,
        RESUME,
        EXIT
    }

    private int backdropTexture = -1;
    private int lastFbW;
    private int lastFbH;

    private int resumeX, resumeY, resumeW, resumeH;
    private int exitX, exitY, exitW, exitH;
    private static final int CORNER_RADIUS = 14;
    private static final int BUTTON_W = 300;
    private static final int BUTTON_H = 54;

    /**
     * GLFW reports cursor position in framebuffer pixels; {@link Renderer2D} uses logical window size for ortho.
     */
    public static double[] cursorToOrtho(Window window, double cursorX, double cursorY) {
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        int orthoW = window.getWidth();
        int orthoH = window.getHeight();
        double mx = cursorX * orthoW / Math.max(1, fbW);
        double my = cursorY * orthoH / Math.max(1, fbH);
        return new double[] { mx, my };
    }

    /**
     * Call at the start of UI pass while back buffer still holds the 3D frame (before overlay quads).
     */
    public void captureBackdrop(Window window) {
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        if (fbW <= 0 || fbH <= 0) return;

        if (backdropTexture < 0) {
            backdropTexture = GL11.glGenTextures();
        }
        lastFbW = fbW;
        lastFbH = fbH;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, fbW, fbH, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void layout(int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        resumeW = BUTTON_W;
        resumeH = BUTTON_H;
        exitW = BUTTON_W;
        exitH = BUTTON_H;
        resumeX = cx - resumeW / 2;
        resumeY = cy - resumeH - 16;
        exitX = cx - exitW / 2;
        exitY = cy + 16;
    }

    public void drawBackdropBlurAndDim(Renderer2D r2d, int screenW, int screenH) {
        if (backdropTexture < 0 || lastFbW <= 0 || lastFbH <= 0) {
            r2d.setColor(0.04f, 0.05f, 0.08f, 0.92f);
            r2d.drawRect(0, 0, screenW, screenH);
            return;
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);

        float du = 1.5f / lastFbW;
        float dv = 1.5f / lastFbH;
        int passes = 12;
        float alpha = 0.14f;

        for (int p = 0; p < passes; p++) {
            int ox = (p % 4) - 1;
            int oy = (p / 4) - 1;
            GL11.glColor4f(1f, 1f, 1f, alpha);
            GL11.glPushMatrix();
            GL11.glTranslatef(ox * 2.5f, oy * 2.5f, 0f);
            drawFullscreenTexturedQuad(screenW, screenH, du * ox, dv * oy);
            GL11.glPopMatrix();
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        r2d.setColor(0.03f, 0.04f, 0.07f, 0.45f);
        r2d.drawRect(0, 0, screenW, screenH);
    }

    private void drawFullscreenTexturedQuad(int screenW, int screenH, float uOff, float vOff) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f + uOff, 1f - vOff);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(1f + uOff, 1f - vOff);
        GL11.glVertex2f(screenW, 0f);
        GL11.glTexCoord2f(1f + uOff, 0f - vOff);
        GL11.glVertex2f(screenW, screenH);
        GL11.glTexCoord2f(0f + uOff, 0f - vOff);
        GL11.glVertex2f(0f, screenH);
        GL11.glEnd();
    }

    public void drawButtons(Window window, Renderer2D r2d, TextRenderer textRenderer, double cursorX, double cursorY) {
        double[] o = cursorToOrtho(window, cursorX, cursorY);
        double mouseX = o[0];
        double mouseY = o[1];
        boolean overResume = contains(resumeX, resumeY, resumeW, resumeH, mouseX, mouseY);
        boolean overExit = contains(exitX, exitY, exitW, exitH, mouseX, mouseY);

        drawModernButton(r2d, textRenderer, resumeX, resumeY, resumeW, resumeH, "Resume", overResume);
        drawModernButton(r2d, textRenderer, exitX, exitY, exitW, exitH, "Exit", overExit);
    }

    private void drawModernButton(Renderer2D r2d, TextRenderer textRenderer, int x, int y, int w, int h,
                                  String label, boolean hovered) {
        if (hovered) {
            r2d.setColor(0.22f, 0.55f, 0.95f, 0.35f);
        } else {
            r2d.setColor(0.12f, 0.14f, 0.18f, 0.55f);
        }
        r2d.drawRoundedRect(x - 2, y - 2, w + 4, h + 4, CORNER_RADIUS + 4);

        r2d.setColor(hovered ? 0.18f : 0.12f, hovered ? 0.22f : 0.14f, hovered ? 0.30f : 0.20f, 0.98f);
        r2d.drawRoundedRect(x, y, w, h, CORNER_RADIUS);

        float fontScale = 1.35f;
        float tw = textRenderer.getTextWidth(label, fontScale);
        float tx = x + (w - tw) / 2f;
        float ty = y + (h - 12f * fontScale) / 2f;
        textRenderer.drawText2D(label, tx, ty, fontScale, 0.92f, 0.94f, 0.98f, 1f);
    }

    private boolean contains(int bx, int by, int bw, int bh, double mx, double my) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    public Action handleClick(Window window, double cursorX, double cursorY) {
        double[] o = cursorToOrtho(window, cursorX, cursorY);
        double mouseX = o[0];
        double mouseY = o[1];
        if (contains(resumeX, resumeY, resumeW, resumeH, mouseX, mouseY)) {
            return Action.RESUME;
        }
        if (contains(exitX, exitY, exitW, exitH, mouseX, mouseY)) {
            return Action.EXIT;
        }
        return Action.NONE;
    }

    public void dispose() {
        if (backdropTexture >= 0) {
            GL11.glDeleteTextures(backdropTexture);
            backdropTexture = -1;
        }
    }
}
