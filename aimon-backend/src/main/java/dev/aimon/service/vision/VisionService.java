package dev.aimon.service.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aimon.dto.vision.FoodVisionResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class VisionService {

    private static final Logger log = Logger.getLogger(VisionService.class);

    private static final Set<String> SPRITE_KEYS = Set.of(
        "apple pie", "bacon", "bagel", "baguette", "banana", "bread",
        "bubble_gum", "bun", "burger", "burrito dish", "butter2", "cabbage",
        "cheesecake", "cheesepuff_bowl", "chocolate", "chocolate cake",
        "cocacola", "coffee_espresso", "coffee_foam", "coffee_greentea",
        "cookies", "curry dish", "default", "default2", "donut", "dumplings",
        "egg_brown", "egg_white", "eggsalad", "eggtart", "energy_bar", "fanta",
        "frenchfries", "friedegg", "fruit_blueberry", "fruit_cherry",
        "fruit_grape_red", "fruit_kiwi", "fruit_lemon", "fruit_lime",
        "fruit_orange", "fruit_orange_slice", "fruit_peach", "fruit_strawberry",
        "fruit_watermelon", "fruit_watermelon_slice", "fruitcake", "garlicbread",
        "gingerbreadman", "green_apple", "green_grape", "gummybear", "hotdog",
        "icecream", "jam", "jelly", "lemonpie", "loafbread", "macncheese",
        "meat", "meatball", "milk_bottle", "milk_chocolate", "nacho", "omlet",
        "onigiri", "pancakes", "pastry_baguette", "pastry_bread",
        "pastry_brioche", "pastry_croissant", "pastry_pretzel", "pepsi",
        "pizza", "popcorn", "popsicle_blue", "potato", "potatochip_blue",
        "potatochip_green", "potatochip_yellow", "potatochips", "pudding",
        "ramen", "red_apple", "red_grape", "roastedchicken", "salmon",
        "sandwich", "sliced_bread_p", "spaghetti", "sprite", "steak",
        "strawberry", "strawberrycake", "sushi", "taco", "vegetable_carrot",
        "vegetable_corn", "waffle", "watermelon", "white_cheese", "wine_red"
    );

    private static final String VISION_PROMPT =
        "Analyze this image. Is it food? Respond with ONLY a JSON object, no markdown.\n" +
        "If food: {\"is_food\":true,\"food_name\":\"<Vietnamese name>\",\"food_name_en\":\"<English name>\",\"description\":\"<brief Vietnamese>\"}\n" +
        "If not food: {\"is_food\":false,\"food_name\":null,\"food_name_en\":null,\"description\":\"<brief Vietnamese, child-friendly>\"}\n" +
        "Keep descriptions under 30 words.";

    private final HttpClient sidecarHttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final HttpClient cloudHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @ConfigProperty(name = "ai.vision.sidecar-url", defaultValue = "http://vision-sidecar:8090")
    String sidecarUrl;

    @ConfigProperty(name = "ai.vision.sidecar-timeout-ms", defaultValue = "2000")
    int sidecarTimeoutMs;

    @ConfigProperty(name = "ai.vision.fallback-model", defaultValue = "gpt-4o-mini")
    String fallbackModel;

    @ConfigProperty(name = "ai.litellm.api-key")
    String litellmApiKey;

    @ConfigProperty(name = "ai.litellm.base-url")
    String litellmBaseUrl;

    public FoodVisionResult analyzeFood(byte[] jpegBytes) {
        // Try sidecar first
        try {
            FoodVisionResult result = classifyViaSidecar(jpegBytes);
            if (result != null) return result;
        } catch (Exception e) {
            log.warn("Sidecar classification failed, falling back to cloud: " + e.getMessage());
        }

        // Fallback to cloud via LiteLLM
        try {
            return classifyViaCloud(jpegBytes);
        } catch (Exception e) {
            log.error("Cloud vision fallback also failed", e);
            return FoodVisionResult.notFood("Vision analysis unavailable", 0, "error");
        }
    }

    private FoodVisionResult classifyViaSidecar(byte[] jpegBytes) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(jpegBytes);
        String requestBody = mapper.writeValueAsString(Map.of("image_base64", base64));
        byte[] bodyBytes = requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        log.infof("Sidecar request: %d bytes image → %d bytes JSON body", jpegBytes.length, bodyBytes.length);

        long t0 = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(sidecarUrl + "/classify-base64"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
            .build();

        HttpResponse<String> response = sidecarHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int elapsed = (int) (System.currentTimeMillis() - t0);

        if (response.statusCode() != 200) {
            throw new RuntimeException("Sidecar returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        return parseVisionResult(json, elapsed, "moondream2");
    }

    private FoodVisionResult classifyViaCloud(byte[] jpegBytes) {
        String base64 = Base64.getEncoder().encodeToString(jpegBytes);
        String imageUrl = "data:image/jpeg;base64," + base64;

        // Build OpenAI vision format with content array
        List<Map<String, Object>> contentParts = List.of(
            Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)),
            Map.of("type", "text", "text", VISION_PROMPT)
        );

        Map<String, Object> userMessage = Map.of("role", "user", "content", contentParts);
        List<Map<String, Object>> messages = List.of(userMessage);

        // Build request manually as JSON since LiteLlmChatMessage doesn't support content arrays
        long t0 = System.currentTimeMillis();
        try {
            String requestBody = mapper.writeValueAsString(Map.of(
                "model", fallbackModel,
                "messages", messages,
                "max_tokens", 1024,
                "max_output_tokens", 1024,
                "temperature", 0.1
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(litellmBaseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + litellmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = cloudHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int elapsed = (int) (System.currentTimeMillis() - t0);

            if (response.statusCode() != 200) {
                throw new RuntimeException("LiteLLM returned " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("LiteLLM returned no choices: " + response.body());
            }
            String text = choices.get(0).path("message").path("content").asText();
            log.infof("Cloud vision raw response: %s", text);
            String cleaned = extractJson(stripMarkdownFences(text));
            try {
                JsonNode resultJson = mapper.readTree(cleaned);
                return parseVisionResult(resultJson, elapsed, fallbackModel);
            } catch (Exception parseEx) {
                log.warnf("Failed to parse cloud vision JSON: %s — raw: %s", parseEx.getMessage(), text);
                // Return description from whatever text the LLM gave
                return FoodVisionResult.notFood(
                    text.length() > 100 ? text.substring(0, 100) : text, elapsed, fallbackModel);
            }

        } catch (Exception e) {
            throw new RuntimeException("Cloud vision failed: " + e.getMessage(), e);
        }
    }

    private FoodVisionResult parseVisionResult(JsonNode json, int inferenceMs, String source) {
        boolean isFood = json.path("is_food").asBoolean(false);
        String foodName = json.path("food_name").isNull() ? null : json.path("food_name").asText(null);
        String description = json.path("description").asText("");

        if (isFood) {
            // sprite_key from sidecar (which has the full list), or derive from English name
            String spriteKey = json.path("sprite_key").isNull() ? null : json.path("sprite_key").asText(null);
            if (spriteKey == null || spriteKey.isBlank()) {
                String enName = json.path("food_name_en").isNull() ? null : json.path("food_name_en").asText(null);
                spriteKey = enName != null ? enName.toLowerCase().strip() : null;
            }
            spriteKey = validateSpriteKey(spriteKey);
            return FoodVisionResult.food(foodName, spriteKey, description, inferenceMs, source);
        }
        return FoodVisionResult.notFood(description, inferenceMs, source);
    }

    private String validateSpriteKey(String key) {
        if (key == null || key.isBlank()) return "default";
        String sanitized = key.replaceAll("[^a-zA-Z0-9_ ]", "");
        if (SPRITE_KEYS.contains(sanitized)) return sanitized;
        // Case-insensitive match
        String lower = sanitized.toLowerCase();
        for (String k : SPRITE_KEYS) {
            if (k.toLowerCase().equals(lower)) return k;
        }
        return "default";
    }

    /**
     * Extract the first complete JSON object from text.
     * Handles cases where LLM adds text before/after JSON or truncates output.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        if (start < 0) return text;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        // No complete JSON found — return from first brace anyway (will fail with clear error)
        return text.substring(start);
    }

    private String stripMarkdownFences(String text) {
        text = text.strip();
        // Triple backtick fences: ```json\n...\n```
        if (text.startsWith("```")) {
            int firstNl = text.indexOf("\n");
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).strip();
            }
        }
        // Single backtick wrapping: `{...}`
        if (text.startsWith("`") && text.endsWith("`")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

}
