package net.twelfthengine.test;

import net.twelfthengine.core.tick.TickEvent;
import net.twelfthengine.core.tick.TickListener;

public class TickTest implements TickListener {
  private static int tick_counter = 0;

  public static void register() {}

  @Override
  public void onTick(TickEvent event) {
    tick_counter++;

    if (tick_counter % 100 == 0) {
      System.out.println("100er Tick: " + tick_counter);
    }
  }
}
