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
        "Analyze this image. Is it food?\n" +
        "If food, respond JSON only: {\"is_food\": true, \"food_name\": \"<Vietnamese name>\", " +
        "\"sprite_key\": \"<closest from list>\", \"description\": \"<brief>\"}\n" +
        "If not food, respond JSON only: {\"is_food\": false, \"food_name\": null, " +
        "\"sprite_key\": null, \"description\": \"<Vietnamese, child-friendly>\"}\n" +
        "Keep descriptions under 50 words. Be child-appropriate.\n" +
        "Sprite keys: " + SPRITE_KEYS;

    private final HttpClient sidecarHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
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

        long t0 = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(sidecarUrl + "/classify-base64"))
            .timeout(Duration.ofMillis(sidecarTimeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
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
                "max_tokens", 512,
                "temperature", 0.1
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(litellmBaseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(10))
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
            JsonNode resultJson = mapper.readTree(stripMarkdownFences(text));
            return parseVisionResult(resultJson, elapsed, fallbackModel);

        } catch (Exception e) {
            throw new RuntimeException("Cloud vision failed: " + e.getMessage(), e);
        }
    }

    private FoodVisionResult parseVisionResult(JsonNode json, int inferenceMs, String source) {
        boolean isFood = json.path("is_food").asBoolean(false);
        String foodName = json.path("food_name").isNull() ? null : json.path("food_name").asText(null);
        String spriteKey = json.path("sprite_key").isNull() ? null : json.path("sprite_key").asText(null);
        String description = json.path("description").asText("");

        if (isFood) {
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
