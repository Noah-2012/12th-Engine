package net.twelfthengine.core.resources;

import java.io.*;
import java.nio.file.*;
import net.twelfthengine.core.logger.Logger;

public class ResourceExtractor {

  private static final Path TEMP_DIR;

  static {
    try {
      TEMP_DIR = Files.createTempDirectory("twelfthengine_");
      Logger.info("ResourceExtractor", "Temp directory created: " + TEMP_DIR.toAbsolutePath());

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    Logger.info("ResourceExtractor", "Cleaning up temp directory: " + TEMP_DIR);
                    try {
                      deleteDirectory(TEMP_DIR.toFile());
                      Logger.info("ResourceExtractor", "Temp directory deleted successfully.");
                    } catch (IOException e) {
                      Logger.warn(
                          "ResourceExtractor",
                          "Failed to delete temp directory: " + e.getMessage());
                    }
                  }));
    } catch (IOException e) {
      Logger.error(
          "ResourceExtractor", "Fatal: Failed to create temp directory — " + e.getMessage());
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
    String normalised = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    String fileName = Paths.get(normalised).getFileName().toString();
    Path dest = TEMP_DIR.resolve(fileName);

    if (Files.exists(dest)) {
      Logger.debug("ResourceExtractor", "Cache hit — skipping extraction for: " + normalised);
      return dest.toAbsolutePath().toString();
    }

    Logger.info("ResourceExtractor", "Extracting resource to disk: " + normalised);
    try (InputStream in = ResourceExtractor.class.getResourceAsStream(normalised)) {
      if (in == null) {
        Logger.error("ResourceExtractor", "Resource not found on classpath: " + normalised);
        throw new IOException("Resource not found on classpath: " + normalised);
      }
      Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
      long sizeKb = Files.size(dest) / 1024;
      Logger.info(
          "ResourceExtractor",
          "Extracted '" + fileName + "' → " + dest.toAbsolutePath() + " (" + sizeKb + " KB)");
    }

    return dest.toAbsolutePath().toString();
  }

  /**
   * Reads a classpath resource entirely into a byte array. Use this for TwelfthPackage — no temp
   * file needed.
   */
  public static byte[] readBytes(String resourcePath) throws IOException {
    String normalised = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    Logger.debug("ResourceExtractor", "Reading resource bytes: " + normalised);

    try (InputStream in = ResourceExtractor.class.getResourceAsStream(normalised)) {
      if (in == null) {
        Logger.error("ResourceExtractor", "Resource not found on classpath: " + normalised);
        throw new IOException("Resource not found on classpath: " + normalised);
      }
      byte[] data = in.readAllBytes();
      Logger.debug("ResourceExtractor", "Read " + data.length + " bytes from: " + normalised);
      return data;
    }
  }

  private static void deleteDirectory(File dir) throws IOException {
    if (dir.isDirectory()) {
      File[] children = dir.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteDirectory(child);
        }
      }
    }
    if (!dir.delete()) {
      Logger.warn("ResourceExtractor", "Could not delete: " + dir.getAbsolutePath());
    }
  }
}
