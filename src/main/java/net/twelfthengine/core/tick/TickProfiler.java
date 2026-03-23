package net.twelfthengine.core.tick;

import net.twelfthengine.core.logger.Logger;

public class TickProfiler {

  private final double tickTime; // Sekunden pro Tick
  private int tickCount = 0;
  private int ticksSkipped = 0;

  private long lastTickNano = System.nanoTime();
  private long lastReportNano = System.nanoTime();

  public TickProfiler(double tickRate) {
    this.tickTime = 1.0 / tickRate;
  }

  // Wird zu Beginn jedes Ticks aufgerufen
  public void startTick() {
    long now = System.nanoTime();
    double elapsed = (now - lastTickNano) / 1_000_000_000.0;

    if (elapsed > 1.5 * tickTime) { // Threshold kann angepasst werden
      ticksSkipped++;
      Logger.warn("TickProfiler", "Lag detected! Tick skipped. Elapsed: " + elapsed + "s");
    }

    lastTickNano = now;
    tickCount++;
  }

  // Am Ende des Ticks optional aufrufen, falls du End-Time messen willst
  public void endTick() {
    // Kann genutzt werden, um DeltaTime für Statistik zu speichern
  }

  // Jede Sekunde aufrufen (oder in EngineLoop)
  public void update() {
    long now = System.nanoTime();
    double elapsed = (now - lastReportNano) / 1_000_000_000.0;

    if (elapsed >= 1.0) { // jede Sekunde Report
      Logger.info("TickProfiler", "TPS: " + tickCount + ", Skipped: " + ticksSkipped);
      tickCount = 0;
      ticksSkipped = 0;
      lastReportNano = now;
    }
  }
}
