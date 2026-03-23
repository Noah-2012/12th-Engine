package net.twelfthengine.core.tick;

import java.util.ArrayList;
import java.util.List;

public class TickManager {

  private final List<TickListener> listeners = new ArrayList<>();

  public void register(TickListener listener) {
    listeners.add(listener);
  }

  public void unregister(TickListener listener) {
    listeners.remove(listener);
  }

  public void fire(TickPhase phase, double deltaTime) {
    TickEvent event = new TickEvent(phase, deltaTime);

    for (TickListener listener : listeners) {
      listener.onTick(event);
    }
  }
}
