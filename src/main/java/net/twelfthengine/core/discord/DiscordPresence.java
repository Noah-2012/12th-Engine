package net.twelfthengine.core.discord;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

public class DiscordPresence {

  private static final String APP_ID = "1489912639743197315";
  private static boolean running = false;

  public static void init() {
    System.out.println("Starting Discord RPC...");

    DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().build();
    DiscordRPC.discordInitialize(APP_ID, handlers, true);

    running = true;

    update("Starting Engine...", "Booting");

    Thread callbackThread =
        new Thread(
            () -> {
              while (running) {
                DiscordRPC.discordRunCallbacks();
                try {
                  Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
              }
            },
            "Discord-RPC");
    callbackThread.setDaemon(true);
    callbackThread.start();

    System.out.println("Discord RPC started!");
  }

  public static void update(String details, String state) {
    if (!running) return;

    DiscordRichPresence presence =
        new DiscordRichPresence.Builder(state)
            .setDetails(details)
            .setBigImage("logo", "12th Engine")
            .setStartTimestamps(System.currentTimeMillis() / 1000)
            .build();

    DiscordRPC.discordUpdatePresence(presence);
  }

  public static void shutdown() {
    running = false;
    DiscordRPC.discordShutdown();
    System.out.println("Discord RPC stopped.");
  }
}
