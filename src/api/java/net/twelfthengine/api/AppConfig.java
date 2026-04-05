package net.twelfthengine.api;

public record AppConfig(
        String title,
        int width,
        int height,
        double tickRate,
        float cullingTestCameraFovDegrees,
        boolean enableAntialiasing,
        boolean enableMultisampling,
        String introVideoPath   // null or empty = skip intro
) {
  public static AppConfig defaults() {
    return new AppConfig(
            "12th Engine",
            1920,
            1080,
            20.0,
            120f,
            true,
            true,
            "/engine-intro.mp4"
    );
  }

  /** Convenience — same as defaults but with a custom title. */
  public static AppConfig withTitle(String title) {
    return new AppConfig(title, 1920, 1080, 20.0, 90f, true, true, "/engine-intro.mp4");
  }

  /** Skip the intro entirely. */
  public AppConfig withoutIntro() {
    return new AppConfig(title, width, height, tickRate, cullingTestCameraFovDegrees,
            enableAntialiasing, enableMultisampling, null);
  }
}