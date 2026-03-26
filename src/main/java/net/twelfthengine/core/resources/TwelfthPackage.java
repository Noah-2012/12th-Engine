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

/**
 * A native loader for the custom Twelfth Engine binary archive formats: - .twm (Twelfth Model v1) -
 * .twa (Twelfth Archive v1)
 *
 * <p>This class reads the binary specification directly into a HashMap in memory, allowing the
 * engine to request files like textures or .obj data as InputStreams.
 */
public class TwelfthPackage {

  private final Map<String, byte[]> fileSystem = new HashMap<>();
  private final String archiveName;

  public TwelfthPackage(File archiveFile) throws IOException {
    this.archiveName = archiveFile.getName();
    try (BufferedInputStream fileStream =
        new BufferedInputStream(new FileInputStream(archiveFile))) {
      loadArchive(fileStream);
    }
  }

  public TwelfthPackage(String filePath) throws IOException {
    this(new File(filePath));
  }

  public TwelfthPackage(byte[] rawData, String virtualName) throws IOException {
    this.archiveName = virtualName;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(rawData)) {
      loadArchive(bis);
    }
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

  /**
   * Parses the custom binary format and populates the virtual file system.
   *
   * <p>Format Specification: [12 bytes] Obfuscation Key (The rest of the file is XORed with the
   * key) [4 bytes] Magic Number ("TWM1" or "TWA1") [4 bytes] File Count (Unsigned Int,
   * Little-Endian)
   *
   * <p>For each file: [2 bytes] Filename Length (Unsigned Short, Little-Endian) [N bytes] Filename
   * (UTF-8 String) [4 bytes] Data Length (Unsigned Int, Little-Endian) [M bytes] File Data (Raw
   * Bytes)
   */
  private void loadArchive(InputStream fileStream) throws IOException {
    // Read the 12-byte Obfuscation Key
    byte[] obfuscationKey = new byte[12];
    if (fileStream.read(obfuscationKey) != 12) {
      throw new IOException("Invalid archive: Missing 12-byte obfuscation key.");
    }

    // Wrap the rest of the stream in our custom XOR deobfuscator
    XorInputStream bis = new XorInputStream(fileStream, obfuscationKey);

    // Read Magic Number (4 bytes)
    byte[] magicBytes = new byte[4];
    if (bis.read(magicBytes) != 4) {
      throw new IOException("Invalid archive: Missing magic number.");
    }
    String magic = new String(magicBytes, StandardCharsets.UTF_8);

    if (!magic.equals("TWM1") && !magic.equals("TWA1")) {
      throw new IOException("Unsupported archive format. Expected TWM1 or TWA1, got: " + magic);
    }

    // Read File Count (4 bytes, Little-Endian)
    byte[] countBytes = new byte[4];
    if (bis.read(countBytes) != 4) {
      throw new IOException("Invalid archive: Missing file count.");
    }
    int fileCount = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

    for (int i = 0; i < fileCount; i++) {
      // Read Filename Length (2 bytes, Little-Endian)
      byte[] nameLenBytes = new byte[2];
      if (bis.read(nameLenBytes) != 2) {
        throw new IOException("Invalid archive: Missing filename length at index " + i);
      }
      int nameLength =
          ByteBuffer.wrap(nameLenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

      // Read Filename
      byte[] nameBytes = new byte[nameLength];
      if (bis.read(nameBytes) != nameLength) {
        throw new IOException("Invalid archive: Missing filename data at index " + i);
      }
      String filename = new String(nameBytes, StandardCharsets.UTF_8);

      // Read Data Length (4 bytes, Little-Endian)
      byte[] dataLenBytes = new byte[4];
      if (bis.read(dataLenBytes) != 4) {
        throw new IOException("Invalid archive: Missing data length for file: " + filename);
      }
      int dataLength = ByteBuffer.wrap(dataLenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

      // Read Data
      byte[] fileData = new byte[dataLength];
      int bytesRead = 0;
      while (bytesRead < dataLength) {
        int read = bis.read(fileData, bytesRead, dataLength - bytesRead);
        if (read == -1) {
          throw new IOException(
              "Invalid archive: Unexpected end of stream while reading data for: " + filename);
        }
        bytesRead += read;
      }

      // Add to internal file system
      fileSystem.put(filename, fileData);
    }
  }

  /**
   * Checks if a specific file exists within the loaded archive.
   *
   * @param filepath The relative path stored in the archive (e.g., "textures/grass.png")
   * @return true if the file exists
   */
  public boolean hasFile(String filepath) {
    return fileSystem.containsKey(filepath);
  }

  /**
   * Returns the raw bytes of a file inside the archive.
   *
   * @param filepath The relative path stored in the archive.
   * @return byte[] containing the file data, or null if not found.
   */
  public byte[] getFileData(String filepath) {
    return fileSystem.get(filepath);
  }

  /**
   * Returns an InputStream for a file inside the archive. Useful for passing directly to ObjLoader
   * or TextureLoader.
   *
   * @param filepath The relative path stored in the archive.
   * @return InputStream of the file, or null if not found.
   */
  public InputStream getFileInputStream(String filepath) {
    byte[] data = getFileData(filepath);
    if (data != null) {
      return new ByteArrayInputStream(data);
    }
    return null;
  }

  /** Returns an unmodifiable map of all filenames to their raw byte arrays. */
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
