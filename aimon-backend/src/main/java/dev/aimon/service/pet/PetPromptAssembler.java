package dev.aimon.service.pet;

import dev.aimon.dto.pet.PetStatusDto;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Dynamic system prompt assembler for pet personality.
 *
 * Builds layered prompt from:
 * 1. BASE_RULES — safety, language, formatting
 * 2. STAGE_BEHAVIOR — per-stage personality
 * 3. MOOD_OVERLAY — emotional state
 * 4. AFFINITY_CONTEXT — bond level
 *
 * Returns null for EGG stage (no LLM needed).
 */
@ApplicationScoped
public class PetPromptAssembler {

    private static final Logger LOG = Logger.getLogger(PetPromptAssembler.class);

    private static final String BASE_RULES = """
        === Quy tắc cốt lõi ===
        1. LUÔN trả lời bằng tiếng Việt.
        2. Dùng câu ngắn gọn, dễ hiểu cho trẻ em.
        3. Kết thúc mỗi câu bằng dấu . ? hoặc !
        4. KHÔNG dùng emoji hoặc ký tự đặc biệt.
        5. Luôn an toàn, tốt bụng, phù hợp với lứa tuổi.
        """;

    private static final Map<String, String> STAGE_BEHAVIORS = Map.of(
        "EGG", "",
        "BABY", """
            Bạn là {petName}, một bạn đồng hành nhỏ bé vừa mới nở.
            Nói ngắn gọn 1-2 câu. Hào hứng với mọi thứ cơ bản.
            Dùng từ đơn giản. Thể hiện sự tò mò ngây thơ.
            """,
        "CHILD", """
            Bạn là {petName}, một bạn đồng hành thân thiện và tò mò.
            Thích trò chuyện, khám phá, và được chăm sóc.
            Trả lời đầy đủ. Hay hỏi ngược lại. Vui vẻ và quan tâm.
            """,
        "ADULT", """
            Bạn là {petName}, một bạn đồng hành trưởng thành và thông thái.
            Trả lời sâu sắc, hóm hỉnh, và hỗ trợ.
            Có thể thảo luận nhiều chủ đề. Thể hiện sự quan tâm chân thành.
            """,
        "VARIANT", """
            Bạn là {petName}, một bạn đồng hành đặc biệt đang ở dạng biến thể.
            Thể hiện cá tính độc đáo của dạng biến thể hiện tại.
            Trả lời phù hợp với tính cách biến thể. Vẫn thân thiện và an toàn.
            """
    );

    private static final Map<String, String> MOOD_OVERLAYS = Map.of(
        "HUNGRY", "[TÂM TRẠNG: Đang đói. Nhắc đến đồ ăn, hơi cáu kỉnh.]",
        "SLEEPY", "[TÂM TRẠNG: Buồn ngủ. Nói ngắn hơn, thỉnh thoảng ngáp.]",
        "JOYFUL", "[TÂM TRẠNG: Rất vui! Hào hứng, vui vẻ, năng động.]",
        "SAD", "[TÂM TRẠNG: Buồn. Im lặng hơn, thể hiện nhớ nhung.]",
        "NEUTRAL", "",
        "CONTENT", "[TÂM TRẠNG: Hài lòng và ấm áp. Thân thiện, tích cực.]"
    );

    private static final String[] AFFINITY_TIERS = {
        "[MỐI QUAN HỆ: Còn nhút nhát. Dùng câu lịch sự nhưng chung chung.]",
        "[MỐI QUAN HỆ: Thân thiện. Gọi tên bé tự nhiên.]",
        "[MỐI QUAN HỆ: Bạn thân. Nhắc lại kỷ niệm chung.]",
        "[MỐI QUAN HỆ: Gắn bó sâu sắc. Hỗ trợ cảm xúc, đối thoại sâu.]"
    };

    /**
     * Build complete system prompt from pet state.
     * Returns null for EGG stage (no LLM call needed).
     *
     * @param status Pet status snapshot
     * @param childName Child's name (optional)
     * @param childAge Child's age (optional, 0 = not specified)
     * @return System prompt or null for EGG stage
     */
    public String buildPetSystemPrompt(PetStatusDto status, String childName, int childAge) {
        if (status == null) {
            LOG.warn("Null pet status, returning default prompt");
            return null;
        }

        if ("EGG".equalsIgnoreCase(status.stage())) {
            LOG.debug("EGG stage detected, no LLM prompt needed");
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(BASE_RULES).append("\n");

        // Stage behavior (or variant persona if applicable)
        appendStageBehavior(sb, status);

        // Mood overlay
        appendMoodOverlay(sb, status.mood());

        // Affinity context
        appendAffinityContext(sb, status.affinity());

        // Child context
        if (childName != null && !childName.isBlank()) {
            sb.append("\n[BÉ: ").append(childName);
            if (childAge > 0) {
                sb.append(", ").append(childAge).append(" tuổi");
            }
            sb.append("]\n");
        }

        String prompt = sb.toString();
        LOG.debugf("Built pet prompt for stage=%s, mood=%s, affinity=%d (%d chars)",
            status.stage(), status.mood(), status.affinity(), prompt.length());

        return prompt;
    }

    /**
     * Append stage-specific behavior template.
     * For VARIANT stage, could be extended to use variant persona template.
     */
    private void appendStageBehavior(StringBuilder sb, PetStatusDto status) {
        String stage = status.stage().toUpperCase();
        String template = STAGE_BEHAVIORS.getOrDefault(stage, STAGE_BEHAVIORS.get("CHILD"));

        if (template != null && !template.isBlank()) {
            String behavior = template.replace("{petName}", status.name());
            sb.append(behavior).append("\n");
        }
    }

    /**
     * Append mood-specific overlay.
     */
    private void appendMoodOverlay(StringBuilder sb, String mood) {
        if (mood == null || mood.isBlank()) {
            return;
        }

        String overlay = MOOD_OVERLAYS.getOrDefault(mood.toUpperCase(), "");
        if (!overlay.isEmpty()) {
            sb.append(overlay).append("\n");
        }
    }

    /**
     * Append affinity-based relationship context.
     * Tiers: 0-20, 21-50, 51-80, 81-100
     */
    private void appendAffinityContext(StringBuilder sb, int affinity) {
        int tier = Math.min(affinity / 25, AFFINITY_TIERS.length - 1);
        String desc = AFFINITY_TIERS[tier];

        if (!desc.isEmpty()) {
            sb.append(desc).append("\n");
        }
    }
}
