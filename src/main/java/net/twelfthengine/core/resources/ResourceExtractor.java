package net.twelfthengine.core.resources;

import java.io.*;
import java.nio.file.*;

public class ResourceExtractor {

  private static final Path TEMP_DIR;

  static {
    try {
      TEMP_DIR = Files.createTempDirectory("twelfthengine_");
      // Delete the temp dir and its contents when the JVM exits
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      deleteDirectory(TEMP_DIR.toFile());
                    } catch (IOException ignored) {
                    }
                  }));
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temp directory for resources", e);
    }
  }

  /**
   * Extracts a classpath resource to a temp file and returns its absolute path. Safe to call
   * multiple times — returns the cached path if already extracted.
   *
   * @param resourcePath e.g. "/engine-intro.mp4" or "/models/6ovcmof8fc56.twa"
   * @return absolute filesystem path to the extracted temp file
   */
  public static String extract(String resourcePath) throws IOException {
    // Normalise — ensure leading slash for getResourceAsStream
    String normalised = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;

    // Use the filename as the temp file name to keep it recognisable
    String fileName = Paths.get(normalised).getFileName().toString();
    Path dest = TEMP_DIR.resolve(fileName);

    // Already extracted this session
    if (Files.exists(dest)) {
      return dest.toAbsolutePath().toString();
    }

    try (InputStream in = ResourceExtractor.class.getResourceAsStream(normalised)) {
      if (in == null) {
        throw new IOException("Resource not found on classpath: " + normalised);
      }
      Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    return dest.toAbsolutePath().toString();
  }

  /**
   * Reads a classpath resource entirely into a byte array. Use this for TwelfthPackage — no temp
   * file needed.
   */
  public static byte[] readBytes(String resourcePath) throws IOException {
    String normalised = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    try (InputStream in = ResourceExtractor.class.getResourceAsStream(normalised)) {
      if (in == null) {
        throw new IOException("Resource not found on classpath: " + normalised);
      }
      return in.readAllBytes();
    }
  }

  private static void deleteDirectory(File dir) throws IOException {
    if (dir.isDirectory()) {
      for (File child : dir.listFiles()) {
        deleteDirectory(child);
      }
    }
    dir.delete();
  }
}
