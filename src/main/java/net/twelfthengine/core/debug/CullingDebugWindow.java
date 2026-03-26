package net.twelfthengine.core.debug;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.twelfthengine.entity.BasicEntity;
import net.twelfthengine.entity.ModelEntity;
import net.twelfthengine.entity.camera.CameraEntity;
import net.twelfthengine.entity.world.BasicPlaneEntity;
import net.twelfthengine.entity.world.TextureEntity;
import net.twelfthengine.world.World;
import org.joml.FrustumIntersection;

/**
 * A Swing-based debug window that displays a top-down view of the 3D world. It shows the camera,
 * the view frustum bounds, and all entities, color-coded based on whether they are culled or drawn.
 */
public class CullingDebugWindow {

  private JFrame frame;
  private DebugPanel panel;

  private World world;
  private FrustumIntersection frustum;
  private float viewScale = 10f; // Pixels per world unit

  public CullingDebugWindow(World world) {
    this.world = world;

    SwingUtilities.invokeLater(
        () -> {
          frame = new JFrame("12th Engine - Frustum Culling Debugger");
          frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

          panel = new DebugPanel();
          panel.setPreferredSize(new Dimension(800, 800));
          frame.add(panel);

          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        });
  }

  /**
   * Update the debug view with the latest frustum. Call this at the end of your render loop or
   * tick.
   */
  public void update(FrustumIntersection currentFrustum) {
    this.frustum = currentFrustum;
    if (panel != null) {
      panel.repaint();
    }
  }

  public boolean isVisible() {
    return frame != null && frame.isVisible();
  }

  private class DebugPanel extends JPanel {

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (world == null) return;

      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Fill background
      g2d.setColor(new Color(30, 30, 35));
      g2d.fillRect(0, 0, getWidth(), getHeight());

      int cx = getWidth() / 2;
      int cy = getHeight() / 2;

      CameraEntity cam = null;
      try {
        cam = world.getActiveCamera();
      } catch (Exception ignored) {
        // No active camera yet
      }

      float camX = 0;
      float camZ = 0;
      if (cam != null) {
        camX = cam.getPosition().x();
        camZ = cam.getPosition().z();
      }

      int offsetX = cx - (int) (camX * viewScale);
      int offsetY = cy - (int) (camZ * viewScale);

      // Draw grid
      g2d.setColor(new Color(50, 50, 55));
      g2d.setStroke(new BasicStroke(1));
      int gridStart = (int) (camX / 10) * 10 - 100;
      int gridEnd = gridStart + 200;
      for (int i = gridStart; i <= gridEnd; i += 10) {
        int px = offsetX + (int) (i * viewScale);
        g2d.drawLine(px, 0, px, getHeight());
      }
      int gridZStart = (int) (camZ / 10) * 10 - 100;
      int gridZEnd = gridZStart + 200;
      for (int i = gridZStart; i <= gridZEnd; i += 10) {
        int py = offsetY + (int) (i * viewScale);
        g2d.drawLine(0, py, getWidth(), py);
      }

      // Draw axes
      g2d.setColor(new Color(150, 50, 50));
      g2d.drawLine(offsetX, offsetY, offsetX + 100, offsetY); // +X (Right)
      g2d.setColor(new Color(50, 50, 150));
      g2d.drawLine(offsetX, offsetY, offsetX, offsetY + 100); // +Z (Down in top-down view)

      // Draw entities
      List<BasicEntity> entities = new ArrayList<>(world.getEntities());
      for (BasicEntity e : entities) {
        if (e instanceof CameraEntity) continue;

        float x = e.getPosition().x();
        float z = e.getPosition().z();

        float radius = e.getCollisionRadius();
        if (e instanceof ModelEntity me) {
          radius = me.getModelBoundingRadius() * me.getSize();
        } else if (e instanceof BasicPlaneEntity plane) {
          radius = Math.max(plane.getWidth(), plane.getLength());
        } else if (e instanceof TextureEntity te) {
          radius = Math.max(te.getWidth(), te.getHeight());
        }

        // Determine Culling state
        boolean isCulled = false;
        if (frustum != null) {
          isCulled =
              !frustum.testSphere(
                  e.getPosition().x(), e.getPosition().y(), e.getPosition().z(), radius);
        }

        // Convert to screen coordinates
        int sx = offsetX + (int) (x * viewScale);
        int sz = offsetY + (int) (z * viewScale); // Top down: Z maps to Y axis
        int sr = (int) (radius * viewScale);

        // Draw bounding sphere
        if (isCulled) {
          g2d.setColor(new Color(200, 50, 50, 100)); // Red = Culled
          g2d.fillOval(sx - sr, sz - sr, sr * 2, sr * 2);
          g2d.setColor(new Color(255, 100, 100));
          g2d.drawOval(sx - sr, sz - sr, sr * 2, sr * 2);
        } else {
          g2d.setColor(new Color(50, 200, 50, 100)); // Green = Rendered
          g2d.fillOval(sx - sr, sz - sr, sr * 2, sr * 2);
          g2d.setColor(new Color(100, 255, 100));
          g2d.drawOval(sx - sr, sz - sr, sr * 2, sr * 2);
        }
      }

      // Draw Camera
      if (cam != null) {
        float cxPos = cam.getPosition().x();
        float czPos = cam.getPosition().z();
        int scx = offsetX + (int) (cxPos * viewScale);
        int scz = offsetY + (int) (czPos * viewScale);

        g2d.setColor(Color.WHITE);
        g2d.fillOval(scx - 4, scz - 4, 8, 8);

        // Draw Camera View Direction
        float yaw = cam.getYaw();
        float dx = (float) Math.sin(Math.toRadians(yaw));
        float dz = -(float) Math.cos(Math.toRadians(yaw));

        g2d.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(scx, scz, scx + (int) (dx * 30), scz + (int) (dz * 30));
      }

      // Draw legend
      g2d.setColor(Color.WHITE);
      g2d.drawString("Entities: " + entities.size(), 10, 20);
      g2d.setColor(new Color(100, 255, 100));
      g2d.drawString("Green: Rendered", 10, 40);
      g2d.setColor(new Color(255, 100, 100));
      g2d.drawString("Red: Culled", 10, 60);
    }
  }
}
