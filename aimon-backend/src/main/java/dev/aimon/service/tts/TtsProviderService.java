package dev.aimon.service.tts;

import dev.aimon.config.AIConfig;
import dev.aimon.dto.tts.TtsRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * TTS Provider Orchestrator with Config-Driven Fallback Strategy.
 *
 * Provider selection is driven by configuration:
 * - ai.tts.primary-provider (default: google)
 * - ai.tts.fallback-provider (default: vieneu)
 *
 * Supported providers:
 * - vieneu: VieNeu-TTS (GPU-accelerated, local, 24kHz)
 * - google: Google Cloud TTS (external API, high quality)
 *
 * Circuit Breaker Logic:
 * - Tracks consecutive failures per provider
 * - Opens circuit after configured threshold (default: 3)
 * - Auto-resets after configured timeout (default: 30s)
 */
@ApplicationScoped
public class TtsProviderService {

  private static final Logger LOG = Logger.getLogger(TtsProviderService.class);

  @Inject
  GoogleTtsStreamingService googleTtsService;

  @Inject
  VieNeuTtsService vieNeuTtsService;

  @Inject
  AIConfig aiConfig;

  // Circuit breaker state per provider
  private final Map<String, CircuitBreakerState> circuitStates = new ConcurrentHashMap<>();

  /**
   * Generate speech with automatic provider selection and failover.
   * Uses config-driven primary/fallback selection with circuit breaker.
   */
  public void generateSpeechStreaming(
      TtsRequest request,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete,
      Consumer<Exception> onError) {

    String primary = aiConfig.tts().primaryProvider();
    String fallback = aiConfig.tts().fallbackProvider();

    if (shouldUseProvider(primary)) {
      LOG.infof("Using PRIMARY provider: %s", primary);
      invokeProvider(primary, request, onChunk,
          () -> {
            resetCircuitBreaker(primary);
            onComplete.run();
          },
          error -> {
            recordFailure(primary, error);
            LOG.warnf("PRIMARY %s failed, falling back to %s", primary, fallback);
            fallbackToProvider(fallback, request, onChunk, onComplete, onError);
          });
    } else {
      String reason = isProviderEnabled(primary) ? "circuit breaker OPEN" : "disabled in configuration";
      LOG.infof("Skipping PRIMARY %s (%s), using FALLBACK: %s", primary, reason, fallback);
      fallbackToProvider(fallback, request, onChunk, onComplete, onError);
    }
  }

  private void invokeProvider(
      String provider,
      TtsRequest request,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete,
      Consumer<Exception> onError) {

    switch (provider.toLowerCase()) {
      case "vieneu" -> vieNeuTtsService.generateSpeechStreaming(request, onChunk, onComplete, onError);
      case "google" -> googleTtsService.generateSpeechStreaming(request, onChunk, onComplete, onError);
      default -> {
        LOG.errorf("Unknown TTS provider: %s", provider);
        onError.accept(new IllegalArgumentException("Unknown TTS provider: " + provider));
      }
    }
  }

  private void fallbackToProvider(
      String fallback,
      TtsRequest request,
      Function<byte[], Boolean> onChunk,
      Runnable onComplete,
      Consumer<Exception> onError) {

    if (!isProviderEnabled(fallback)) {
      LOG.errorf("Fallback provider %s is also disabled - no TTS provider available", fallback);
      onError.accept(new IllegalStateException("No TTS provider available"));
      return;
    }

    invokeProvider(fallback, request, onChunk, onComplete, onError);
  }

  private boolean shouldUseProvider(String provider) {
    return isProviderEnabled(provider) && !isCircuitOpen(provider);
  }

  private boolean isProviderEnabled(String provider) {
    return switch (provider.toLowerCase()) {
      case "vieneu" -> aiConfig.vieneuTts().enabled();
      case "google" -> aiConfig.googleTts().enabled();
      default -> false;
    };
  }

  private boolean isCircuitOpen(String provider) {
    CircuitBreakerState state = circuitStates.get(provider);
    if (state == null || state.openedAt == 0) {
      return false;
    }

    long elapsed = System.currentTimeMillis() - state.openedAt;
    long resetTimeout = aiConfig.tts().circuitBreaker().resetTimeout();

    if (elapsed >= resetTimeout) {
      LOG.infof("Circuit breaker entering HALF-OPEN state for %s", provider);
      state.consecutiveFailures = 0;
      state.openedAt = 0;
      return false;
    }

    return true;
  }

  private void recordFailure(String provider, Exception error) {
    CircuitBreakerState state = circuitStates.computeIfAbsent(provider, k -> new CircuitBreakerState());
    state.consecutiveFailures++;
    LOG.warnf("Provider %s failure #%d: %s", provider, state.consecutiveFailures, error.getMessage());

    int threshold = aiConfig.tts().circuitBreaker().failureThreshold();
    if (state.consecutiveFailures >= threshold) {
      state.openedAt = System.currentTimeMillis();
      LOG.errorf("Circuit breaker OPENED for %s after %d failures", provider, state.consecutiveFailures);
    }
  }

  private void resetCircuitBreaker(String provider) {
    CircuitBreakerState state = circuitStates.get(provider);
    if (state != null && state.consecutiveFailures > 0) {
      LOG.debugf("Provider %s recovered, resetting circuit breaker", provider);
      state.consecutiveFailures = 0;
      state.openedAt = 0;
    }
  }

  /**
   * Get current provider status for health checks.
   */
  public String getProviderStatus() {
    String primary = aiConfig.tts().primaryProvider();
    String fallback = aiConfig.tts().fallbackProvider();

    StringBuilder status = new StringBuilder();
    status.append("Primary=").append(primary).append("[");
    status.append(getProviderStateString(primary));
    status.append("], Fallback=").append(fallback).append("[");
    status.append(getProviderStateString(fallback));
    status.append("]");

    return status.toString();
  }

  private String getProviderStateString(String provider) {
    if (!isProviderEnabled(provider)) {
      return "DISABLED";
    }

    CircuitBreakerState state = circuitStates.get(provider);
    if (state == null || state.openedAt == 0) {
      if (state != null && state.consecutiveFailures > 0) {
        return "DEGRADED, failures=" + state.consecutiveFailures;
      }
      return "HEALTHY";
    }

    long elapsed = System.currentTimeMillis() - state.openedAt;
    long resetTimeout = aiConfig.tts().circuitBreaker().resetTimeout();
    long remaining = (resetTimeout - elapsed) / 1000;
    return "CIRCUIT_OPEN, reset_in=" + remaining + "s";
  }

  /**
   * Manually reset all circuit breakers.
   */
  public void resetAllCircuitBreakers() {
    circuitStates.clear();
    LOG.infof("All circuit breakers manually reset");
  }

  /**
   * Reset circuit breaker for specific provider.
   */
  public void resetCircuitBreakerFor(String provider) {
    circuitStates.remove(provider);
    LOG.infof("Circuit breaker for %s manually reset", provider);
  }

  /**
   * Check if circuit breaker is open for primary provider.
   */
  public boolean isCircuitBreakerOpen() {
    return isCircuitOpen(aiConfig.tts().primaryProvider());
  }

  /**
   * Get consecutive failures for primary provider.
   */
  public int getConsecutiveFailures() {
    CircuitBreakerState state = circuitStates.get(aiConfig.tts().primaryProvider());
    return state != null ? state.consecutiveFailures : 0;
  }

  // Keep old method for backward compatibility
  public void resetCircuitBreaker() {
    resetAllCircuitBreakers();
  }

  private static class CircuitBreakerState {
    int consecutiveFailures = 0;
    long openedAt = 0;
  }
}
