package net.twelfthengine.core.resources;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.twelfthengine.core.logger.Logger;

/**
 * A native loader for the custom Twelfth Engine binary archive formats: - .twm (Twelfth Model v1) -
 * .twa (Twelfth Archive v1)
 */
public class TwelfthPackage {

  private static final String TAG = "TwelfthPackage";

  private final Map<String, byte[]> fileSystem = new HashMap<>();
  private final String archiveName;

  public TwelfthPackage(File archiveFile) throws IOException {
    this.archiveName = archiveFile.getName();
    Logger.info(
        TAG,
        "Loading archive from file: "
            + archiveFile.getAbsolutePath()
            + " ("
            + (archiveFile.length() / 1024)
            + " KB)");
    try (BufferedInputStream fileStream =
        new BufferedInputStream(new FileInputStream(archiveFile))) {
      loadArchive(fileStream);
    }
    Logger.info(
        TAG, "Archive '" + archiveName + "' loaded — " + fileSystem.size() + " file(s) indexed.");
  }

  public TwelfthPackage(String filePath) throws IOException {
    this(new File(filePath));
  }

  public TwelfthPackage(byte[] rawData, String virtualName) throws IOException {
    this.archiveName = virtualName;
    Logger.info(
        TAG, "Loading archive from raw bytes: " + virtualName + " (" + rawData.length + " bytes)");
    try (ByteArrayInputStream bis = new ByteArrayInputStream(rawData)) {
      loadArchive(bis);
    }
    Logger.info(
        TAG,
        "Archive '"
            + archiveName
            + "' loaded from memory — "
            + fileSystem.size()
            + " file(s) indexed.");
  }

  /**
   * An InputStream wrapper that transparently XOR-deobfuscates the underlying stream using a
   * rolling 12-byte key.
   */
  private static class XorInputStream extends FilterInputStream {

    private final byte[] key;
    private int keyIndex = 0;

    protected XorInputStream(InputStream in, byte[] key) {
      super(in);
      this.key = key;
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b == -1) return -1;
      int decoded = b ^ (key[keyIndex % key.length] & 0xFF);
      keyIndex++;
      return decoded;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int bytesRead = super.read(b, off, len);
      if (bytesRead == -1) return -1;
      for (int i = 0; i < bytesRead; i++) {
        b[off + i] = (byte) (b[off + i] ^ key[keyIndex % key.length]);
        keyIndex++;
      }
      return bytesRead;
    }
  }

  /** Parses the custom binary format and populates the virtual file system. */
  private void loadArchive(InputStream fileStream) throws IOException {
    // Read the 12-byte obfuscation key
    byte[] obfuscationKey = new byte[12];
    if (fileStream.read(obfuscationKey) != 12) {
      Logger.error(
          TAG, "Archive '" + archiveName + "' is malformed — missing 12-byte obfuscation key.");
      throw new IOException("Invalid archive: Missing 12-byte obfuscation key.");
    }
    Logger.debug(TAG, "Obfuscation key read for: " + archiveName);

    XorInputStream bis = new XorInputStream(fileStream, obfuscationKey);

    // Read magic number (4 bytes)
    byte[] magicBytes = new byte[4];
    if (bis.read(magicBytes) != 4) {
      Logger.error(TAG, "Archive '" + archiveName + "' is malformed — missing magic number.");
      throw new IOException("Invalid archive: Missing magic number.");
    }
    String magic = new String(magicBytes, StandardCharsets.UTF_8);
    Logger.debug(TAG, "Archive magic: '" + magic + "'");

    if (!magic.equals("TWM1") && !magic.equals("TWA1")) {
      Logger.error(
          TAG,
          "Unsupported archive format '"
              + magic
              + "' in: "
              + archiveName
              + " (expected TWM1 or TWA1)");
      throw new IOException("Unsupported archive format. Expected TWM1 or TWA1, got: " + magic);
    }

    // Read file count (4 bytes, little-endian)
    byte[] countBytes = new byte[4];
    if (bis.read(countBytes) != 4) {
      Logger.error(TAG, "Archive '" + archiveName + "' is malformed — missing file count.");
      throw new IOException("Invalid archive: Missing file count.");
    }
    int fileCount = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    Logger.info(
        TAG, "Archive '" + archiveName + "' contains " + fileCount + " file(s), format: " + magic);

    for (int i = 0; i < fileCount; i++) {
      // Filename length (2 bytes, little-endian)
      byte[] nameLenBytes = new byte[2];
      if (bis.read(nameLenBytes) != 2) {
        Logger.error(
            TAG,
            "Unexpected end of stream reading filename length at entry "
                + i
                + " in: "
                + archiveName);
        throw new IOException("Invalid archive: Missing filename length at index " + i);
      }
      int nameLength =
          ByteBuffer.wrap(nameLenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

      // Filename
      byte[] nameBytes = new byte[nameLength];
      if (bis.read(nameBytes) != nameLength) {
        Logger.error(
            TAG, "Unexpected end of stream reading filename at entry " + i + " in: " + archiveName);
        throw new IOException("Invalid archive: Missing filename data at index " + i);
      }
      String filename = new String(nameBytes, StandardCharsets.UTF_8);

      // Data length (4 bytes, little-endian)
      byte[] dataLenBytes = new byte[4];
      if (bis.read(dataLenBytes) != 4) {
        Logger.error(TAG, "Missing data length for entry '" + filename + "' in: " + archiveName);
        throw new IOException("Invalid archive: Missing data length for file: " + filename);
      }
      int dataLength = ByteBuffer.wrap(dataLenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

      // File data
      byte[] fileData = new byte[dataLength];
      int bytesRead = 0;
      while (bytesRead < dataLength) {
        int read = bis.read(fileData, bytesRead, dataLength - bytesRead);
        if (read == -1) {
          Logger.error(
              TAG,
              "Unexpected end of stream reading data for '"
                  + filename
                  + "' in: "
                  + archiveName
                  + " (got "
                  + bytesRead
                  + "/"
                  + dataLength
                  + " bytes)");
          throw new IOException(
              "Invalid archive: Unexpected end of stream while reading data for: " + filename);
        }
        bytesRead += read;
      }

      fileSystem.put(filename, fileData);
      Logger.debug(
          TAG,
          "  ["
              + (i + 1)
              + "/"
              + fileCount
              + "] Indexed: "
              + filename
              + " ("
              + dataLength
              + " bytes)");
    }
  }

  public boolean hasFile(String filepath) {
    boolean result = fileSystem.containsKey(filepath);
    Logger.debug(
        TAG, "hasFile('" + filepath + "') → " + result + " [archive: " + archiveName + "]");
    return result;
  }

  public byte[] getFileData(String filepath) {
    byte[] data = fileSystem.get(filepath);
    if (data == null) {
      Logger.warn(
          TAG, "getFileData('" + filepath + "') — file not found in archive: " + archiveName);
    } else {
      Logger.debug(TAG, "getFileData('" + filepath + "') — returning " + data.length + " bytes");
    }
    return data;
  }

  public InputStream getFileInputStream(String filepath) {
    byte[] data = getFileData(filepath);
    if (data != null) {
      Logger.debug(
          TAG,
          "getFileInputStream('" + filepath + "') — wrapping " + data.length + " bytes in stream");
      return new ByteArrayInputStream(data);
    }
    Logger.warn(
        TAG,
        "getFileInputStream('"
            + filepath
            + "') — returning null (file not in archive: "
            + archiveName
            + ")");
    return null;
  }

  public Map<String, byte[]> getFileSystem() {
    return Collections.unmodifiableMap(fileSystem);
  }

  public String getArchiveName() {
    return archiveName;
  }

  public int getFileCount() {
    return fileSystem.size();
  }
}
