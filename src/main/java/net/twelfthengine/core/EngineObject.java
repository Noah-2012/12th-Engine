package net.twelfthengine.core;

import net.twelfthengine.core.logger.Logger;
import net.twelfthengine.core.tick.TickManager;
import net.twelfthengine.world.World;

public final class EngineObject {

  private static EngineObject instance;

  private boolean running = false;

  private EngineObject() {}

  public static EngineObject getInstance() {
    if (instance == null) {
      instance = new EngineObject();
    }
    return instance;
  }

  // =========================
  // WORLD
  // =========================

  private World world;

  public void setWorld(World world) {
    this.world = world;
  }

  public World getWorld() {
    if (world == null) throw new IllegalStateException("World (IAB) not set!");
    return world;
  }

  public boolean hasWorld() {
    return world != null;
  }

  // =========================
  // TICKS
  // =========================

  private final TickManager tickManager = new TickManager();

  public TickManager getTickManager() {
    return tickManager;
  }

  // =========================
  // LIFECYCLE
  // =========================

  public void start() {
    if (running) return;
    running = true;
    Logger.info("12th Engine", "Engine started");
  }

  public void stop() {
    if (!running) return;
    running = false;
    Logger.info("12th Engine", "Engine stopped");
  }

  public boolean isRunning() {
    return running;
  }
}
