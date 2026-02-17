package dev.aimon.service.audio;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import java.io.IOException;

/**
 * Service for processing OPUS audio using JNA bindings to the native libopus.
 * Note: libopus must be installed on the system:
 * - Ubuntu/Debian: sudo apt-get install libopus0 libopus-dev
 * - Windows: Place opus.dll on PATH or java.library.path
 * - macOS: brew install opus
 */
@ApplicationScoped
public class OpusAudioProcessor {
  private static final Logger LOG = Logger.getLogger(OpusAudioProcessor.class);

  /** JNA interface for libopus */
  public interface OpusLibrary extends Library {
    // Try multiple names on Windows
    OpusLibrary INSTANCE = loadOpusLibrary();
    // Encoder functions
    Pointer opus_encoder_create(int Fs, int channels, int application, int[] error);
    int opus_encode(Pointer st, short[] pcm, int frame_size, byte[] data, int max_data_bytes);
    void opus_encoder_destroy(Pointer st);
    int opus_encoder_ctl(Pointer st, int request, Object... args);
    // Decoder functions
    Pointer opus_decoder_create(int Fs, int channels, int[] error);
    int opus_decode(Pointer st, byte[] data, int len, short[] pcm, int frame_size, int decode_fec);
    void opus_decoder_destroy(Pointer st);
    // Constants
    int OPUS_OK = 0;
    int OPUS_APPLICATION_VOIP = 2048;
    int OPUS_APPLICATION_AUDIO = 2049;
    int OPUS_SET_BITRATE_REQUEST = 4002;
  }

  private static OpusLibrary loadOpusLibrary() {
    Logger logger = Logger.getLogger(OpusAudioProcessor.class);

    // Try bundled native library first (extracted from resources)
    String bundledPath = extractBundledNative();
    if (bundledPath != null) {
      try {
        OpusLibrary lib = Native.load(bundledPath, OpusLibrary.class);
        logger.infof("Loaded bundled OPUS library from: %s", bundledPath);
        return lib;
      } catch (UnsatisfiedLinkError e) {
        logger.warnf("Failed to load bundled OPUS: %s", e.getMessage());
      }
    }

    // Fall back to system library
    return loadSystemOpusLibrary();
  }

  /**
   * Extract bundled native library from resources to temp directory.
   * Supports Windows x86_64, Linux x86_64, and Linux aarch64 (Raspberry Pi).
   */
  private static String extractBundledNative() {
    Logger logger = Logger.getLogger(OpusAudioProcessor.class);
    String osArch = System.getProperty("os.arch").toLowerCase();
    String osName = System.getProperty("os.name").toLowerCase();

    String resourcePath;
    String libName;

    if (osName.contains("windows")) {
      resourcePath = "native/windows-x86_64/opus.dll";
      libName = "opus.dll";
    } else if (osName.contains("linux")) {
      if (osArch.contains("aarch64") || osArch.contains("arm64")) {
        resourcePath = "native/linux-aarch64/libopus.so";
      } else {
        resourcePath = "native/linux-x86_64/libopus.so";
      }
      libName = "libopus.so";
    } else {
      // macOS: use system library (brew install opus)
      logger.debug("macOS detected, using system OPUS library");
      return null;
    }

    try {
      // Extract to temp directory
      java.io.InputStream is = OpusAudioProcessor.class.getClassLoader()
          .getResourceAsStream(resourcePath);
      if (is == null) {
        logger.debugf("Bundled native library not found: %s", resourcePath);
        return null;
      }

      java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "opus-native");
      tempDir.mkdirs();
      java.io.File libFile = new java.io.File(tempDir, libName);

      // Only extract if not already present or different size
      if (!libFile.exists()) {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(libFile)) {
          is.transferTo(fos);
        }
        logger.infof("Extracted bundled OPUS library to: %s", libFile.getAbsolutePath());
      } else {
        logger.debugf("Using cached bundled OPUS library: %s", libFile.getAbsolutePath());
      }
      is.close();

      return libFile.getAbsolutePath();
    } catch (Exception e) {
      logger.debugf("Failed to extract bundled native: %s", e.getMessage());
      return null;
    }
  }

  /**
   * Load OPUS library from system paths.
   */
  private static OpusLibrary loadSystemOpusLibrary() {
    Logger logger = Logger.getLogger(OpusAudioProcessor.class);

    if (Platform.isWindows()) {
      String[] windowsNames = {"opus", "opus.dll", "libopus", "libopus.dll", "libopus-0", "libopus-0.dll"};
      for (String name : windowsNames) {
        try {
          OpusLibrary lib = Native.load(name, OpusLibrary.class);
          logger.infof("Successfully loaded system OPUS library: %s", name);
          return lib;
        } catch (UnsatisfiedLinkError e) {
          logger.debugf("Failed to load %s: %s", name, e.getMessage());
        }
      }
      throw new UnsatisfiedLinkError("Could not load any OPUS library variant on Windows");
    } else {
      return Native.load("opus", OpusLibrary.class);
    }
  }

  private static boolean OPUS_AVAILABLE = false;
  static {
    try {
      Logger logger = Logger.getLogger(OpusAudioProcessor.class);
      logger.info("=== Initializing OPUS library ===");
      logger.info("Testing OPUS library access...");
      OpusLibrary.INSTANCE.toString(); // Force load library
      logger.info("OPUS library loaded successfully");
      logger.info("Testing OPUS encoder creation...");
      int[] error = new int[1];
      Pointer encoder = OpusLibrary.INSTANCE.opus_encoder_create(48000, 1, OpusLibrary.OPUS_APPLICATION_VOIP, error);
      logger.infof("Encoder creation result: pointer=%s, error=%d", encoder, error[0]);
      if (encoder != null && error[0] == OpusLibrary.OPUS_OK) {
        OpusLibrary.INSTANCE.opus_encoder_destroy(encoder);
        OPUS_AVAILABLE = true;
        logger.info("Native OPUS library loaded successfully");
      } else {
        logger.warnf("OPUS encoder creation failed with error: %d", error[0]);
        logger.warn("Error codes: OPUS_OK=0, OPUS_BAD_ARG=-1, OPUS_BUFFER_TOO_SMALL=-2, OPUS_INTERNAL_ERROR=-3, OPUS_INVALID_PACKET=-4, OPUS_UNIMPLEMENTED=-5, OPUS_INVALID_STATE=-6, OPUS_ALLOC_FAIL=-7");
      }
    } catch (UnsatisfiedLinkError e) {
      Logger logger = Logger.getLogger(OpusAudioProcessor.class);
      logger.warnf("OPUS library not found: %s", e.getMessage());
      logger.warn("Please install OPUS library:");
      logger.warn("  Windows: Place opus.dll in system PATH or java.library.path");
      logger.warn("  Linux: sudo apt-get install libopus0 libopus-dev");
      logger.warn("  macOS: brew install opus");
    } catch (Throwable e) {
      Logger logger = Logger.getLogger(OpusAudioProcessor.class);
      logger.warnf("Native OPUS library not available: %s", e.getMessage());
      logger.warn("OPUS audio processing will be disabled.");
      e.printStackTrace();
    }
  }

  /**
   * Convert raw OPUS data to PCM16 bytes.
   */
  public byte[] opusToPcm16(byte[] opusData, int sampleRate) throws IOException {
    if (!OPUS_AVAILABLE) {
      throw new IOException("OPUS library not available. Please install libopus.");
    }
    LOG.debugf("Converting OPUS to PCM16, input size: %d bytes, sample rate: %d", opusData.length, sampleRate);
    try {
      int[] error = new int[1];
      Pointer decoder = OpusLibrary.INSTANCE.opus_decoder_create(sampleRate, 1, error);
      if (decoder == null || error[0] != OpusLibrary.OPUS_OK) {
        throw new IOException("Failed to create OPUS decoder, error: " + error[0]);
      }
      try {
        int frameSize = (sampleRate * 60) / 1000; // 60 ms
        short[] pcmBuffer = new short[frameSize * 2]; // mono: samples per channel
        int decodedSamples = OpusLibrary.INSTANCE.opus_decode(decoder, opusData, opusData.length, pcmBuffer, frameSize, 0);
        if (decodedSamples < 0) {
          if (decodedSamples == -4) { // OPUS_INVALID_PACKET
            LOG.debug("OPUS invalid packet when decoding full buffer, trying heuristic split");
            return decodeMultipleFramesHeuristic(decoder, opusData, frameSize);
          }
          throw new IOException("OPUS decode error: " + decodedSamples);
        }
        return shortsToBytes(pcmBuffer, decodedSamples);
      } finally {
        OpusLibrary.INSTANCE.opus_decoder_destroy(decoder);
      }
    } catch (Exception e) {
      LOG.errorf(e, "Failed to convert OPUS to PCM16");
      throw new IOException("OPUS decode error: " + e.getMessage(), e);
    }
  }

  /**
   * Heuristic to decode multiple frames when aggregated in a single buffer:
   * tries chunk sizes from 20..400 bytes, decreasing by 10.
   */
  private byte[] decodeMultipleFramesHeuristic(Pointer decoder, byte[] opusData, int frameSize) throws IOException {
    int minFrame = 20;
    int maxFrame = 400;
    int pos = 0;
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    while (pos < opusData.length) {
      int remaining = opusData.length - pos;
      int attemptSize = Math.min(Math.max(minFrame, remaining), maxFrame);
      boolean decodedOne = false;
      for (int size = attemptSize; size >= minFrame; size -= 10) {
        if (pos + size > opusData.length) continue;
        short[] pcm = new short[frameSize * 2];
        int samples = OpusLibrary.INSTANCE.opus_decode(decoder, slice(opusData, pos, size), size, pcm, frameSize, 0);
        if (samples > 0) {
          try {
            out.write(shortsToBytes(pcm, samples));
          } catch (Exception ignore) {
          }
          pos += size;
          decodedOne = true;
          break;
        }
      }
      if (!decodedOne) {
        pos += 1; // prevent infinite loop
      }
    }
    byte[] result = out.toByteArray();
    if (result.length == 0) {
      throw new IOException("OPUS decode error: unable to decode any frame in aggregated buffer (-4 heuristic)");
    }
    LOG.debugf("Heuristic multi-frame OPUS decode output=%d bytes", result.length);
    return result;
  }

  private byte[] slice(byte[] data, int off, int len) {
    byte[] r = new byte[len];
    System.arraycopy(data, off, r, 0, len);
    return r;
  }

  private byte[] shortsToBytes(short[] pcm, int sampleCount) {
    if (sampleCount <= 0) {
      return new byte[0];
    }
    byte[] pcmBytes = new byte[sampleCount * 2];
    for (int i = 0; i < sampleCount; i++) {
      short sample = pcm[i];
      pcmBytes[i * 2] = (byte) (sample & 0xFF);
      pcmBytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    LOG.debugf("OPUS to PCM16 conversion completed, output size: %d bytes (%d samples)", pcmBytes.length, sampleCount);
    return pcmBytes;
  }

  /**
   * Convert PCM16 bytes to OPUS.
   */
  public byte[] pcm16ToOpus(byte[] pcmData, int sampleRate, int channels, int bitrate) throws IOException {
    if (!OPUS_AVAILABLE) {
      throw new IOException("OPUS library not available. Please install libopus.");
    }
    LOG.debugf("Converting PCM16 to OPUS, input size: %d bytes, sample rate: %d, bitrate: %d", pcmData.length, sampleRate, bitrate);
    try {
      int[] error = new int[1];
      Pointer encoder = OpusLibrary.INSTANCE.opus_encoder_create(sampleRate, channels, OpusLibrary.OPUS_APPLICATION_VOIP, error);
      if (encoder == null || error[0] != OpusLibrary.OPUS_OK) {
        throw new IOException("Failed to create OPUS encoder, error: " + error[0]);
      }
      try {
        OpusLibrary.INSTANCE.opus_encoder_ctl(encoder, OpusLibrary.OPUS_SET_BITRATE_REQUEST, bitrate);
        int sampleCount = pcmData.length / 2;
        short[] pcmSamples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
          int low = pcmData[i * 2] & 0xFF;
          int high = pcmData[i * 2 + 1] & 0xFF;
          pcmSamples[i] = (short) ((high << 8) | low);
        }
        int frameSize = sampleRate / 50; // 20 ms
        byte[] opusBuffer = new byte[4000]; // Max OPUS frame size
        int encodedBytes = OpusLibrary.INSTANCE.opus_encode(encoder, pcmSamples, Math.min(frameSize, sampleCount), opusBuffer, opusBuffer.length);
        if (encodedBytes < 0) {
          throw new IOException("OPUS encode error: " + encodedBytes);
        }
        byte[] result = new byte[encodedBytes];
        System.arraycopy(opusBuffer, 0, result, 0, encodedBytes);
        LOG.debugf("PCM16 to OPUS conversion completed, output size: %d bytes", result.length);
        return result;
      } finally {
        OpusLibrary.INSTANCE.opus_encoder_destroy(encoder);
      }
    } catch (Exception e) {
      LOG.errorf(e, "Failed to convert PCM16 to OPUS");
      throw new IOException("OPUS encode error: " + e.getMessage(), e);
    }
  }

  /**
   * Lightweight check to guess if the data might be OPUS (Ogg container).
   */
  public boolean isOpusData(byte[] audioData) {
    if (audioData == null || audioData.length < 8) {
      return false;
    }
    String header = new String(audioData, 0, Math.min(8, audioData.length));
    return header.startsWith("OggS") || header.contains("OpusHead");
  }

  /**
   * Detect OPUS frame duration from packet header (TOC byte).
   * See RFC 6716 Section 3.1 for TOC byte format.
   *
   * @param opusData Raw OPUS packet data
   * @return Frame duration in milliseconds (defaults to 20ms if detection fails)
   */
  public int detectFrameDuration(byte[] opusData) {
    if (opusData == null || opusData.length < 1) {
      return 20; // Default 20ms
    }

    // OPUS TOC byte format:
    // config (5 bits) | s (1 bit) | c (2 bits)
    int toc = opusData[0] & 0xFF;
    int config = (toc >> 3) & 0x1F;

    // Frame duration based on config value (RFC 6716 Section 3.1)
    if (config <= 3) {
      // SILK-only modes NB: 10, 20, 40, 60ms
      int[] silkDurations = {10, 20, 40, 60};
      return silkDurations[config];
    } else if (config <= 7) {
      // SILK-only modes MB: 10, 20, 40, 60ms
      int[] silkDurations = {10, 20, 40, 60};
      return silkDurations[config - 4];
    } else if (config <= 11) {
      // SILK-only modes WB: 10, 20, 40, 60ms
      int[] silkDurations = {10, 20, 40, 60};
      return silkDurations[config - 8];
    } else if (config <= 13) {
      // Hybrid modes SWB: 10, 20ms
      return (config == 12) ? 10 : 20;
    } else if (config <= 15) {
      // Hybrid modes FB: 10, 20ms
      return (config == 14) ? 10 : 20;
    } else {
      // CELT-only modes: 2.5, 5, 10, 20ms
      int[] celtDurations = {2, 5, 10, 20};
      int celtConfig = (config - 16) / 4;
      if (celtConfig >= 0 && celtConfig < celtDurations.length) {
        return celtDurations[celtConfig];
      }
      return 20; // Default fallback
    }
  }

  /**
   * Decode OPUS with auto-detected frame size.
   * Analyzes the OPUS packet header to determine the correct frame duration
   * instead of assuming a fixed 60ms frame size.
   *
   * @param opusData Raw OPUS packet data
   * @param sampleRate Target sample rate (8000, 12000, 16000, 24000, or 48000)
   * @return Decoded PCM16 bytes
   * @throws IOException If decoding fails
   */
  public byte[] opusToPcm16AutoFrame(byte[] opusData, int sampleRate) throws IOException {
    if (!OPUS_AVAILABLE) {
      throw new IOException("OPUS library not available. Please install libopus.");
    }

    int frameDurationMs = detectFrameDuration(opusData);
    int frameSize = (sampleRate * frameDurationMs) / 1000;

    LOG.debugf("Auto-detected OPUS frame: %dms, frameSize=%d samples @ %dHz",
        frameDurationMs, frameSize, sampleRate);

    int[] error = new int[1];
    Pointer decoder = OpusLibrary.INSTANCE.opus_decoder_create(sampleRate, 1, error);
    if (decoder == null || error[0] != OpusLibrary.OPUS_OK) {
      throw new IOException("Failed to create OPUS decoder, error: " + error[0]);
    }

    try {
      // Buffer size: frame size * 2 for safety margin
      short[] pcmBuffer = new short[frameSize * 2];
      int decodedSamples = OpusLibrary.INSTANCE.opus_decode(
          decoder, opusData, opusData.length, pcmBuffer, frameSize * 2, 0);

      if (decodedSamples < 0) {
        throw new IOException("OPUS decode error: " + decodedSamples);
      }
      return shortsToBytes(pcmBuffer, decodedSamples);
    } finally {
      OpusLibrary.INSTANCE.opus_decoder_destroy(decoder);
    }
  }

  /**
   * Validate if a sample rate is supported by OPUS.
   * OPUS supports: 8000, 12000, 16000, 24000, 48000 Hz
   *
   * @param sampleRate Sample rate to validate
   * @return true if sample rate is valid for OPUS
   */
  public static boolean isValidOpusSampleRate(int sampleRate) {
    return sampleRate == 8000 || sampleRate == 12000 ||
           sampleRate == 16000 || sampleRate == 24000 ||
           sampleRate == 48000;
  }

  /**
   * Get nearest valid OPUS sample rate for an invalid rate.
   *
   * @param sampleRate Requested sample rate
   * @return Nearest valid OPUS sample rate
   */
  public static int getNearestValidSampleRate(int sampleRate) {
    if (isValidOpusSampleRate(sampleRate)) {
      return sampleRate;
    }
    // Map to nearest valid rate
    if (sampleRate < 10000) return 8000;
    if (sampleRate < 14000) return 12000;
    if (sampleRate < 20000) return 16000;
    if (sampleRate < 36000) return 24000;
    return 48000;
  }

  /**
   * Create a streaming decoder for repeated decode calls.
   */
  public StreamingDecoder createStreamingDecoder(int sampleRate, int channels) throws IOException {
    if (!OPUS_AVAILABLE) {
      throw new IOException("OPUS library not available. Please install libopus.");
    }
    int[] error = new int[1];
    Pointer decoder = OpusLibrary.INSTANCE.opus_decoder_create(sampleRate, channels, error);
    if (decoder == null || error[0] != OpusLibrary.OPUS_OK) {
      throw new IOException("Failed to create OPUS decoder, error: " + error[0]);
    }
    return new StreamingDecoder(decoder, sampleRate, channels);
  }

  public byte[] decodeWithStreamingDecoder(StreamingDecoder decoder, byte[] opusData) throws IOException {
    if (decoder == null || opusData == null || opusData.length == 0) {
      return new byte[0];
    }
    return decoder.decode(opusData);
  }

  public void closeStreamingDecoder(StreamingDecoder decoder) {
    if (decoder != null) {
      decoder.closeQuietly();
    }
  }

  public OpusMetadata getOpusMetadata(byte[] opusData) {
    try {
      // Placeholder: real-world implementation would parse OGG container/headers
      return new OpusMetadata(48000, 1, 96000); // Default values
    } catch (Exception e) {
      LOG.warnf("Could not parse OPUS metadata: %s", e.getMessage());
      return new OpusMetadata(48000, 1, 96000); // Fallback
    }
  }

  /**
   * Check if native OPUS library is available.
   */
  public boolean isOpusAvailable() {
    return OPUS_AVAILABLE;
  }

  /**
   * Debug helper for native library paths.
   */
  public void debugLibraryPaths() {
    LOG.info("=== OPUS Library Debug Info ===");
    LOG.infof("OPUS Available: %s", OPUS_AVAILABLE);
    LOG.infof("OS: %s %s", System.getProperty("os.name"), System.getProperty("os.arch"));
    LOG.infof("Java Library Path: %s", System.getProperty("java.library.path"));
    LOG.infof("PATH: %s", System.getenv("PATH"));
    if (Platform.isWindows()) {
      LOG.info("Checking Windows OPUS library locations...");
      String[] locations = {
        "C:\\Windows\\System32\\opus.dll",
        "C:\\Windows\\SysWOW64\\opus.dll",
        System.getProperty("user.dir") + "\\opus.dll"
      };
      for (String location : locations) {
        java.io.File file = new java.io.File(location);
        LOG.infof("  %s: %s", location, file.exists() ? "EXISTS" : "NOT FOUND");
      }
    }
    LOG.info("=== End Debug Info ===");
  }

  /**
   * Streaming OPUS decoder wrapper.
   */
  public final class StreamingDecoder implements AutoCloseable {
    private final Pointer handle;
    private final int sampleRate;
    private final int channels;
    private final int maxFrameSize;
    private boolean closed = false;

    private StreamingDecoder(Pointer handle, int sampleRate, int channels) {
      this.handle = handle;
      this.sampleRate = sampleRate;
      this.channels = channels;
      this.maxFrameSize = Math.max((sampleRate * 120) / 1000, Math.max(sampleRate / 50, 960));
    }

    public byte[] decode(byte[] opusData) throws IOException {
      if (closed) {
        throw new IOException("OPUS decoder already closed");
      }
      if (opusData == null || opusData.length == 0) {
        return new byte[0];
      }
      short[] pcmBuffer = new short[maxFrameSize * Math.max(1, channels)];
      int decodedSamples = OpusLibrary.INSTANCE.opus_decode(handle, opusData, opusData.length, pcmBuffer, maxFrameSize, 0);
      if (decodedSamples < 0) {
        throw new IOException("OPUS decode error: " + decodedSamples);
      }
      int totalSamples = decodedSamples * Math.max(1, channels);
      return shortsToBytes(pcmBuffer, totalSamples);
    }

    public int getSampleRate() {
      return sampleRate;
    }

    public int getChannels() {
      return channels;
    }

    @Override
    public void close() {
      if (!closed) {
        OpusLibrary.INSTANCE.opus_decoder_destroy(handle);
        closed = true;
      }
    }

    private void closeQuietly() {
      try {
        close();
      } catch (Exception ignored) {
      }
    }
  }

  public static class OpusMetadata {
    private final int sampleRate;
    private final int channels;
    private final int bitrate;

    public OpusMetadata(int sampleRate, int channels, int bitrate) {
      this.sampleRate = sampleRate;
      this.channels = channels;
      this.bitrate = bitrate;
    }

    public int getSampleRate() {
      return sampleRate;
    }

    public int getChannels() {
      return channels;
    }

    public int getBitrate() {
      return bitrate;
    }

    @Override
    public String toString() {
      return String.format("OpusMetadata{rate=%d, channels=%d, bitrate=%d}", sampleRate, channels, bitrate);
    }
  }

  /**
   * Save PCM16 (byte[]) to a standard WAV file for debugging playback.
   */
  public void savePcm16AsWav(byte[] pcmData, int sampleRate, int channels, String filePath) throws IOException {
    int byteRate = sampleRate * channels * 2;
    int totalDataLen = pcmData.length + 36;
    int totalAudioLen = pcmData.length;
    byte[] header = new byte[44];
    // ChunkID "RIFF"
    header[0] = 'R';
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    // ChunkSize
    writeInt(header, 4, totalDataLen);
    // Format "WAVE"
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';
    // Subchunk1ID "fmt "
    header[12] = 'f';
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';
    // Subchunk1Size (16 for PCM)
    writeInt(header, 16, 16);
    // AudioFormat (1 for PCM)
    header[20] = 1;
    header[21] = 0;
    // NumChannels
    header[22] = (byte) channels;
    header[23] = 0;
    // SampleRate
    writeInt(header, 24, sampleRate);
    // ByteRate
    writeInt(header, 28, byteRate);
    // BlockAlign
    header[32] = (byte) (channels * 2);
    header[33] = 0;
    // BitsPerSample
    header[34] = 16;
    header[35] = 0;
    // Subchunk2ID "data"
    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';
    // Subchunk2Size
    writeInt(header, 40, totalAudioLen);
    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath)) {
      fos.write(header);
      fos.write(pcmData);
    }
  }

  private void writeInt(byte[] buf, int offset, int value) {
    buf[offset] = (byte) (value & 0xFF);
    buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
    buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
  }
}