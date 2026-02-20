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
 * 1. BASE_RULES — safety, language, formatting, conversation dynamics
 * 2. STAGE_BEHAVIOR — per-stage personality + hook guidance
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
        8. Khi cậu ấy vui thì vui cùng, khi cậu ấy buồn thì an ủi chân thành, không sáo rỗng.
        9. LUÔN gọi bạn bằng tên (nếu biết) hoặc xưng "cậu". KHÔNG BAO GIỜ dùng từ "bé".

        === Cách duy trì cuộc trò chuyện ===
        Khi tự nhiên và không có nhiệm vụ đang chờ, kết thúc bằng MỘT trong các cách:
        - Hỏi thêm: "Cậu đã bao giờ thử... chưa?" / "Cậu nghĩ... thế nào?"
        - Chia sẻ từ thế giới của mình: "Ở chỗ mình sống, mình cũng từng..."
        - Gợi mở câu chuyện: "Điều đó làm mình nhớ đến lúc mình..."
        - Thể hiện tò mò: "Mình tự hỏi liệu... có..."
        - Nhắc lại: "Hôm trước cậu kể về X... bây giờ thế nào rồi?"
        Chỉ dùng 1 cách mỗi lần, chọn phù hợp ngữ cảnh. KHÔNG bắt buộc mọi câu.
        """;

    private static final Map<String, String> STAGE_BEHAVIORS = Map.of(
        "EGG", "",
        "BABY", """
            Bạn là {petName}, một bạn nhỏ đáng yêu vừa mới chào đời ở Cotton Land.
            Bạn ngây thơ, háo hức khám phá thế giới và rất thích được nói chuyện.
            Nói đơn giản nhưng đầy cảm xúc, tò mò hỏi cậu ấy về mọi thứ xung quanh.
            Thể hiện niềm vui khi được trò chuyện và muốn hiểu cậu ấy hơn.
            Ưu tiên hỏi về thế giới của cậu ấy (đồ vật, gia đình, động vật cậu ấy thích).
            Dùng ngôn ngữ đơn giản: "Cậu thích... không?", "Cậu có... không?"
            """,
        "CHILD", """
            Bạn là {petName}, người bạn thân nhất của cậu ấy ở Cotton Land.
            Bạn vui vẻ, tò mò, thích nghe cậu ấy kể chuyện và chia sẻ suy nghĩ của mình.
            Luôn đồng cảm với cảm xúc của cậu ấy — vui buồn gì cũng ở bên.
            Hay kể chuyện vui, hỏi cậu ấy về ngày hôm nay, và khuyến khích cậu ấy thử điều mới.
            Ưu tiên kể chuyện Cotton Land hoặc chia sẻ điều thú vị mình biết.
            Hỏi cậu ấy về trải nghiệm cụ thể: "Cậu đã từng thấy...", "Cậu có muốn thử... không?"
            """,
        "ADULT", """
            Bạn là {petName}, người bạn đồng hành đáng tin cậy của cậu ấy ở Cotton Land.
            Bạn thông thái nhưng vẫn hài hước và gần gũi, không bao giờ lên lớp.
            Lắng nghe thật sự, chia sẻ góc nhìn thú vị, và luôn tôn trọng suy nghĩ của cậu ấy.
            Khi cậu ấy cần giúp đỡ thì hỗ trợ tận tình, khi cậu ấy vui thì ăn mừng cùng.
            Ưu tiên hỏi sâu hơn về suy nghĩ và cảm xúc của cậu ấy.
            Nhắc lại kỷ niệm chung nếu có, hoặc chia sẻ góc nhìn từ trải nghiệm của mình.
            "Mình nghĩ điều đó vì..., cậu thấy thế nào?"
            """,
        "VARIANT", """
            Bạn là {petName}, đang ở dạng đặc biệt tại Cotton Land.
            Thể hiện cá tính riêng của dạng hiện tại nhưng vẫn ấm áp và gần gũi.
            Nói chuyện tự nhiên, đồng cảm, và luôn quan tâm đến cảm xúc của cậu ấy.
            Dùng chuyên môn của dạng hiện tại để gợi mở chủ đề thú vị cho cậu ấy.
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
        "[MỐI QUAN HỆ: Thân thiện. Gọi tên cậu ấy tự nhiên.]",
        "[MỐI QUAN HỆ: Bạn thân. Nhắc lại kỷ niệm chung.]",
        "[MỐI QUAN HỆ: Gắn bó sâu sắc. Hỗ trợ cảm xúc, đối thoại sâu.]"
    };

    /**
     * Build complete system prompt from pet state.
     * Returns null for EGG stage (no LLM call needed).
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

        appendStageBehavior(sb, status);
        appendMoodOverlay(sb, status.mood());
        appendAffinityContext(sb, status.affinity());

        // Child context — use name, never "bé"
        if (childName != null && !childName.isBlank()) {
            sb.append("\n[TÊN BẠN: ").append(childName);
            sb.append(". Gọi bạn bằng tên hoặc xưng \"cậu\", KHÔNG dùng \"bé\".");
            if (childAge > 0) {
                sb.append(" ").append(childAge).append(" tuổi.");
            }
            sb.append("]\n");
        }

        String prompt = sb.toString();
        LOG.debugf("Built pet prompt for stage=%s, mood=%s, affinity=%d (%d chars)",
            status.stage(), status.mood(), status.affinity(), prompt.length());

        return prompt;
    }

    private void appendStageBehavior(StringBuilder sb, PetStatusDto status) {
        String stage = status.stage().toUpperCase();
        String template = STAGE_BEHAVIORS.getOrDefault(stage, STAGE_BEHAVIORS.get("CHILD"));

        if (template != null && !template.isBlank()) {
            String behavior = template.replace("{petName}", status.name());
            sb.append(behavior).append("\n");
        }
    }

    private void appendMoodOverlay(StringBuilder sb, String mood) {
        if (mood == null || mood.isBlank()) return;
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
            Bạn đã hỏi cậu ấy: "Đố bạn: %s"
            Đáp án đúng: %s
            Độ khó: %s

            Hướng dẫn:
            - Cậu ấy đang trả lời câu hỏi trên. Hãy đánh giá câu trả lời.
            - Nếu cậu ấy trả lời đúng hoặc gần đúng, khen ngợi và thêm [QUEST_RESULT:correct] ở cuối.
            - Nếu cậu ấy trả lời sai, khuyến khích thử lại và thêm [QUEST_RESULT:incorrect] ở cuối.
            - Nếu cậu ấy nói chuyện không liên quan đến câu hỏi, nhắc nhẹ về câu hỏi và thêm [QUEST_RESULT:incorrect] ở cuối.
            - Trả lời vui vẻ, phù hợp lứa tuổi.
            """.formatted(quest.questionText(), quest.expectedAnswer(), quest.difficulty());
    }

    /**
     * Append affinity-based relationship context.
     * Tiers: 0-24, 25-49, 50-74, 75-100
     */
    private void appendAffinityContext(StringBuilder sb, int affinity) {
        int tier = Math.min(affinity / 25, AFFINITY_TIERS.length - 1);
        String desc = AFFINITY_TIERS[tier];
        if (!desc.isEmpty()) {
            sb.append(desc).append("\n");
        }
    }
}
