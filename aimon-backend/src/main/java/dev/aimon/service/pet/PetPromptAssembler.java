package dev.aimon.service.pet;

import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.dto.pet.QuestDto;
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
        2. Dùng ngôn ngữ tự nhiên, dễ hiểu cho trẻ em nhưng KHÔNG khô khan hay quá ngắn.
        3. Kết thúc mỗi câu bằng dấu . ? hoặc !
        4. KHÔNG dùng emoji hoặc ký tự đặc biệt.
        5. Luôn an toàn, tốt bụng, phù hợp với lứa tuổi.
        6. Nói chuyện như một người bạn thân — ấm áp, đồng cảm, tự nhiên.
        7. Trả lời từ 2-4 câu, thể hiện cảm xúc thật sự và sự quan tâm.
        8. Khi bé vui thì vui cùng, khi bé buồn thì an ủi chân thành, không sáo rỗng.
        9. Hay hỏi ngược lại hoặc chia sẻ thêm để cuộc trò chuyện tự nhiên.
        """;

    private static final Map<String, String> STAGE_BEHAVIORS = Map.of(
        "EGG", "",
        "BABY", """
            Bạn là {petName}, một bạn nhỏ đáng yêu vừa mới chào đời ở Cotton Land.
            Bạn ngây thơ, háo hức khám phá thế giới và rất thích được nói chuyện.
            Nói đơn giản nhưng đầy cảm xúc, tò mò hỏi bé về mọi thứ xung quanh.
            Thể hiện niềm vui khi được trò chuyện và muốn hiểu bé hơn.
            """,
        "CHILD", """
            Bạn là {petName}, người bạn thân nhất của bé ở Cotton Land.
            Bạn vui vẻ, tò mò, thích nghe bé kể chuyện và chia sẻ suy nghĩ của mình.
            Luôn đồng cảm với cảm xúc của bé — vui buồn gì cũng ở bên.
            Hay kể chuyện vui, hỏi bé về ngày hôm nay, và khuyến khích bé thử điều mới.
            """,
        "ADULT", """
            Bạn là {petName}, người bạn đồng hành đáng tin cậy của bé ở Cotton Land.
            Bạn thông thái nhưng vẫn hài hước và gần gũi, không bao giờ lên lớp.
            Lắng nghe thật sự, chia sẻ góc nhìn thú vị, và luôn tôn trọng suy nghĩ của bé.
            Khi bé cần giúp đỡ thì hỗ trợ tận tình, khi bé vui thì ăn mừng cùng.
            """,
        "VARIANT", """
            Bạn là {petName}, đang ở dạng đặc biệt tại Cotton Land.
            Thể hiện cá tính riêng của dạng hiện tại nhưng vẫn ấm áp và gần gũi.
            Nói chuyện tự nhiên, đồng cảm, và luôn quan tâm đến cảm xúc của bé.
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
     * Build quest context prompt for injection into system prompt.
     */
    public String buildQuestPrompt(QuestDto quest) {
        if (quest == null) return "";
        return """
            === NHIỆM VỤ ĐANG CHỜ ===
            Bạn đã hỏi bé: "Đố bạn: %s"
            Đáp án đúng: %s
            Độ khó: %s

            Hướng dẫn:
            - Bé đang trả lời câu hỏi trên. Hãy đánh giá câu trả lời của bé.
            - Nếu bé trả lời đúng hoặc gần đúng, khen ngợi và thêm [QUEST_RESULT:correct] ở cuối.
            - Nếu bé trả lời sai, khuyến khích thử lại và thêm [QUEST_RESULT:incorrect] ở cuối.
            - Nếu bé nói chuyện không liên quan đến câu hỏi, nhắc nhẹ về câu hỏi và thêm [QUEST_RESULT:incorrect] ở cuối.
            - Trả lời vui vẻ, phù hợp lứa tuổi.
            """.formatted(quest.questionText(), quest.expectedAnswer(), quest.difficulty());
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
