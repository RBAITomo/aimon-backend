package dev.aimon.service.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.config.AIConfig;
import dev.aimon.dto.tts.TtsAudio;
import dev.aimon.dto.tts.TtsRequest;
import dev.aimon.dto.tts.VoiceCloneResponse;
import dev.aimon.dto.tts.VoiceInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * VieNeu-TTS client for GPU-accelerated Vietnamese text-to-speech.
 *
 * Connects to VieNeu-TTS repo's FastAPI wrapper (api_server.py).
 * Uses VieNeuTTS/FastVieNeuTTS core directly for inference.
 *
 * Architecture:
 * - Java Backend → FastAPI (api_server.py) → VieNeu core (core.py) → PCM audio
 *
 * Supports:
 * - Synchronous synthesis via POST /synthesize
 * - True streaming via POST /synthesize_stream (chunked PCM response)
 * - Fake streaming fallback (chunk full audio response)
 */
@ApplicationScoped
public class VieNeuTtsService {

  private static final Logger LOG = Logger.getLogger(VieNeuTtsService.class);

  // Streaming constants for fake streaming (chunk full audio)
  private static final int CHUNK_SIZE = 32768; // 32KB chunks for streaming

  @Inject
  AIConfig aiConfig;

  @Inject
  ObjectMapper objectMapper;

  private HttpClient httpClient;

  @PostConstruct
  void init() {
    long connectTimeout = Math.max(5, aiConfig.timeout().connect());
    // Force HTTP/1.1 - uvicorn/FastAPI may not fully support HTTP/2 streaming
    httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(connectTimeout))
        .build();
  }

  /**
   * Generate speech audio synchronously.
   * Calls FastAPI server POST /synthesize endpoint.
   * Returns PCM 16-bit mono audio at configured sample rate (default 16kHz).
   */
  public TtsAudio synthesize(TtsRequest request) {
    if (request == null || request.getText() == null || request.getText().isBlank()) {
      throw new IllegalArgumentException("TTS request text must not be blank");
    }

    AIConfig.VieNeuTtsConfig config = aiConfig.vieneuTts();
    if (!config.enabled()) {
      throw new IllegalStateException("VieNeu-TTS provider is disabled via configuration");
    }

    try {
      String url = normalizeBaseUrl(config.baseUrl()) + "/synthesize";

      // Build request JSON with minimal parameters for optimal speed
      ObjectNode payload = objectMapper.createObjectNode();
      payload.put("text", request.getText());
      payload.put("sample_rate", config.sampleRate());
      payload.put("voice_id", config.voiceId());
      payload.put("silence_p", config.silenceP());
      double pitch = request.getPitchShift() != null ? request.getPitchShift() : config.pitchShift();
      payload.put("pitch_shift", pitch);

      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json; charset=utf-8")
          .timeout(Duration.ofSeconds(Math.max(30, aiConfig.timeout().read())))
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
          .build();

      LOG.debugf("Calling VieNeu FastAPI server at %s (text: %d chars)", url, request.getText().length());

      HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

      if (response.statusCode() != 200) {
        String errorBody = new String(response.body());
        String preview = errorBody.length() > 256 ? errorBody.substring(0, 256) + "..." : errorBody;
        throw new IOException("VieNeu API error status=" + response.statusCode() + ", body=" + preview);
      }

      byte[] pcmData = response.body();

      // Parse sample rate from response header (fallback to config)
      int sampleRate = response.headers()
          .firstValue("X-Sample-Rate")
          .map(Integer::parseInt)
          .orElse(config.sampleRate());

      int channels = response.headers()
          .firstValue("X-Channels")
          .map(Integer::parseInt)
          .orElse(1);

      LOG.debugf("VieNeu-TTS synthesized %d bytes, sampleRate=%d Hz, channels=%d",
          pcmData.length, sampleRate, channels);

      return new TtsAudio(pcmData, sampleRate, channels);

    } catch (Exception e) {
      LOG.errorf(e, "Failed to synthesize speech via VieNeu FastAPI server");
      throw new RuntimeException("VieNeu TTS synthesis failed", e);
    }
  }

  /**
   * Generate speech with streaming support.
   *
   * Uses true streaming via /synthesize_stream if streaming is enabled in config.
   * Falls back to fake streaming (chunk full response) otherwise.
   *
   * @param request    TTS request
   * @param onChunk    callback for each chunk, return false to stop
   * @param onComplete callback when done
   * @param onError    callback on error
   */
  public void generateSpeechStreaming(
      TtsRequest request,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete,
      Consumer<Exception> onError) {

    CompletableFuture.runAsync(() -> {
      try {
        AIConfig.VieNeuTtsConfig config = aiConfig.vieneuTts();

        if (config.streaming()) {
          // True streaming via /synthesize_stream endpoint
          streamFromEndpoint(request, config, onChunk, onComplete);
        } else {
          // Fallback: full synthesis + chunked response
          fakeStream(request, onChunk, onComplete);
        }

      } catch (Exception e) {
        LOG.errorf(e, "Error in VieNeu-TTS streaming: %s", e.getMessage());
        onError.accept(e);
      }
    });
  }

  /**
   * True streaming via POST /synthesize_stream.
   * Reads chunked PCM response from FastAPI StreamingResponse.
   */
  private void streamFromEndpoint(
      TtsRequest request,
      AIConfig.VieNeuTtsConfig config,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete) throws IOException, InterruptedException {

    String url = normalizeBaseUrl(config.baseUrl()) + "/synthesize_stream";

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("text", request.getText());
    payload.put("sample_rate", config.sampleRate());
    payload.put("voice_id", config.voiceId());
    payload.put("silence_p", config.silenceP());
    payload.put("pitch_shift", config.pitchShift());

    String jsonBody = objectMapper.writeValueAsString(payload);
    LOG.debugf("Calling VieNeu streaming at %s (text: %d chars, body: %d bytes)",
        url, request.getText().length(), jsonBody.length());

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json; charset=utf-8")
        .timeout(Duration.ofSeconds(Math.max(60, aiConfig.timeout().read())))
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
        .build();

    HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

    if (response.statusCode() != 200) {
      byte[] errorBytes = response.body().readAllBytes();
      String errorBody = new String(errorBytes);
      throw new IOException("VieNeu streaming error status=" + response.statusCode() + ", body=" + errorBody);
    }

    // Read chunked PCM stream with PCM16 byte alignment (2 bytes per sample)
    int totalBytes = 0;
    int pendingByte = -1; // carry-over byte when read returns odd length
    try (InputStream is = response.body()) {
      byte[] buffer = new byte[CHUNK_SIZE];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        byte[] chunk;
        int chunkLen;

        if (pendingByte >= 0) {
          // Prepend carry-over byte from previous read
          chunkLen = bytesRead + 1;
          chunk = new byte[chunkLen];
          chunk[0] = (byte) pendingByte;
          System.arraycopy(buffer, 0, chunk, 1, bytesRead);
          pendingByte = -1;
        } else {
          chunkLen = bytesRead;
          chunk = new byte[chunkLen];
          System.arraycopy(buffer, 0, chunk, 0, chunkLen);
        }

        // Ensure even length for PCM16 alignment
        if (chunkLen % 2 != 0) {
          pendingByte = chunk[chunkLen - 1] & 0xFF;
          chunkLen--;
          chunk = java.util.Arrays.copyOf(chunk, chunkLen);
        }

        if (chunkLen > 0) {
          totalBytes += chunkLen;
          if (!onChunk.apply(chunk)) {
            LOG.infof("True streaming stopped by client at %d bytes", totalBytes);
            return;
          }
        }
      }
    }

    LOG.infof("VieNeu-TTS true streaming completed, total: %d bytes", totalBytes);
    onComplete.run();
  }

  /**
   * Fake streaming: full synthesis then chunk the response.
   */
  private void fakeStream(
      TtsRequest request,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete) {

    TtsAudio audio = synthesize(request);
    byte[] pcmData = audio.pcmData();
    int offset = 0;

    while (offset < pcmData.length) {
      int chunkLength = Math.min(CHUNK_SIZE, pcmData.length - offset);
      byte[] chunk = new byte[chunkLength];
      System.arraycopy(pcmData, offset, chunk, 0, chunkLength);

      if (!onChunk.apply(chunk)) {
        LOG.infof("Fake streaming stopped by client at offset %d", offset);
        return;
      }
      offset += chunkLength;
    }

    LOG.infof("VieNeu-TTS fake streaming completed, total: %d bytes", pcmData.length);
    onComplete.run();
  }

  /**
   * Clone a voice from reference audio bytes.
   *
   * Sends a multipart/form-data request to POST /voices/clone on VieNeu-TTS.
   *
   * @param audioBytes  Raw audio file bytes (WAV or MP3)
   * @param filename    Original filename (used to infer MIME type)
   * @param transcript  Exact transcript of the reference audio
   * @param voiceId     Desired voice ID (null = auto-generated by Python server)
   * @param description Human-readable label
   */
  public VoiceCloneResponse cloneVoice(
      byte[] audioBytes,
      String filename,
      String transcript,
      String voiceId,
      String description) {

    String url = normalizeBaseUrl(aiConfig.vieneuTts().baseUrl()) + "/voices/clone";
    String boundary = "----AiMonBoundary" + UUID.randomUUID().toString().replace("-", "");

    try {
      byte[] body = buildMultipartBody(boundary, audioBytes, filename, transcript, voiceId, description);

      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .timeout(Duration.ofSeconds(60)) // encoding can take a few seconds on GPU
          .POST(HttpRequest.BodyPublishers.ofByteArray(body))
          .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IOException("VieNeu clone error status=" + response.statusCode() + " body=" + response.body());
      }

      JsonNode json = objectMapper.readTree(response.body());
      return new VoiceCloneResponse(
          json.path("voice_id").asText(),
          json.path("status").asText(),
          json.path("description").asText(),
          json.path("codes_count").asInt(),
          json.path("latency_ms").asInt()
      );

    } catch (Exception e) {
      LOG.errorf(e, "Failed to clone voice via VieNeu-TTS");
      throw new RuntimeException("Voice cloning failed", e);
    }
  }

  /**
   * List all available voices (preset + custom) from VieNeu-TTS.
   */
  public List<VoiceInfo> listVoices() {
    String url = normalizeBaseUrl(aiConfig.vieneuTts().baseUrl()) + "/voices";

    try {
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IOException("VieNeu list voices error status=" + response.statusCode());
      }

      JsonNode json = objectMapper.readTree(response.body());
      List<VoiceInfo> voices = new ArrayList<>();
      for (JsonNode v : json.path("voices")) {
        voices.add(new VoiceInfo(
            v.path("id").asText(),
            v.path("name").asText(),
            v.path("gender").asText("unknown"),
            v.path("region").asText("unknown"),
            v.path("type").asText("preset")
        ));
      }
      return voices;

    } catch (Exception e) {
      LOG.errorf(e, "Failed to list voices from VieNeu-TTS");
      throw new RuntimeException("List voices failed", e);
    }
  }

  /**
   * Delete a custom cloned voice by ID.
   *
   * @return true if deleted, false if not found (404)
   */
  public boolean deleteCustomVoice(String voiceId) {
    String url = normalizeBaseUrl(aiConfig.vieneuTts().baseUrl()) + "/voices/custom/" + voiceId;

    try {
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .DELETE()
          .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 404) {
        return false;
      }
      if (response.statusCode() != 200) {
        throw new IOException("VieNeu delete voice error status=" + response.statusCode());
      }
      return true;

    } catch (IOException | InterruptedException e) {
      LOG.errorf(e, "Failed to delete custom voice '%s'", voiceId);
      throw new RuntimeException("Delete voice failed", e);
    }
  }

  /**
   * Build a multipart/form-data body for the /voices/clone endpoint.
   * Fields: audio_file (binary), transcript (text), voice_id (text), description (text).
   */
  private byte[] buildMultipartBody(
      String boundary,
      byte[] audioBytes,
      String filename,
      String transcript,
      String voiceId,
      String description) {

    String nl = "\r\n";
    String safeFilename = (filename != null && !filename.isBlank()) ? filename : "reference.wav";
    String safeVoiceId = (voiceId != null) ? voiceId : "";
    String safeDesc = (description != null) ? description : "";

    StringBuilder sb = new StringBuilder();

    // audio_file part
    sb.append("--").append(boundary).append(nl);
    sb.append("Content-Disposition: form-data; name=\"audio_file\"; filename=\"").append(safeFilename).append("\"").append(nl);
    sb.append("Content-Type: application/octet-stream").append(nl).append(nl);

    byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);

    // Text fields
    String textParts = nl
        + "--" + boundary + nl
        + "Content-Disposition: form-data; name=\"transcript\"" + nl + nl
        + transcript + nl
        + "--" + boundary + nl
        + "Content-Disposition: form-data; name=\"voice_id\"" + nl + nl
        + safeVoiceId + nl
        + "--" + boundary + nl
        + "Content-Disposition: form-data; name=\"description\"" + nl + nl
        + safeDesc + nl
        + "--" + boundary + "--" + nl;

    byte[] tail = textParts.getBytes(StandardCharsets.UTF_8);

    byte[] body = new byte[header.length + audioBytes.length + tail.length];
    System.arraycopy(header, 0, body, 0, header.length);
    System.arraycopy(audioBytes, 0, body, header.length, audioBytes.length);
    System.arraycopy(tail, 0, body, header.length + audioBytes.length, tail.length);
    return body;
  }

  /**
   * Normalize base URL by removing trailing slash.
   */
  private String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://localhost:5001";
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
