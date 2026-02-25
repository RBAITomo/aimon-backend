package dev.aimon.service.world;

import dev.aimon.model.ConversationSession;
import dev.aimon.service.conversation.TopicClassifier;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Builds travel-related prompt context: current location flavor, travel suggestions, marker instructions.
 * Injected as layer 1e in buildEnhancedPrompt().
 */
@ApplicationScoped
public class TravelPromptBuilder {

    private static final Map<SubLocation, String> FLAVOR_TEXT = Map.of(
        SubLocation.WHIPCREAM_SPIRE,
            "Bạn đang ở Tháp Kem — những tòa tháp kem khổng lồ lấp lánh dưới ánh nắng.",
        SubLocation.MARSHMALLOW_MEADOW,
            "Bạn đang ở Đồng Kẹo Dẻo — cỏ mềm như bông gòn, thỏ kẹo nhảy nhót khắp nơi.",
        SubLocation.CANDY_LANTERN_TOWN,
            "Bạn đang ở Thị Trấn Đèn Kẹo — đèn lồng nhiều màu sắc treo khắp phố.",
        SubLocation.BISCUIT_HILLS,
            "Bạn đang ở Đồi Bánh Quy — mùi bánh quy thơm lừng quanh đây.",
        SubLocation.VANILLA_PROMENADE,
            "Bạn đang ở Đại Lộ Vani — giai điệu nhẹ nhàng vang khắp con đường."
    );

    /**
     * Build travel context prompt block.
     * @param currentLocation PetProfile.currentLocation value
     * @param topInterests kid's top interest codes
     * @param session conversation session (for once-per-session suggestion tracking)
     * @return prompt block or empty string
     */
    public String build(String currentLocation, List<String> topInterests, ConversationSession session) {
        StringBuilder sb = new StringBuilder();

        SubLocation current = SubLocation.fromCode(currentLocation).orElse(null);

        // Current location flavor text
        if (current != null) {
            sb.append("=== Địa Điểm Hiện Tại ===\n");
            sb.append(FLAVOR_TEXT.getOrDefault(current, "")).append("\n\n");
        }

        // Travel suggestion (once per session per location)
        if (topInterests != null && !topInterests.isEmpty()) {
            SubLocation suggested = SubLocation.bestMatchForInterests(topInterests).orElse(null);
            if (suggested != null && suggested != current
                && !session.hasSuggestedTravel(suggested.name())) {
                sb.append("=== Gợi Ý Du Lịch ===\n");
                sb.append("Bé có vẻ thích ").append(interestLabel(topInterests)).append(". ");
                sb.append("Hãy gợi ý rủ bé đến ").append(suggested.getVietnameseAlias());
                sb.append(" (").append(suggested.getDisplayName()).append("). ");
                sb.append("Chỉ gợi ý tự nhiên trong cuộc trò chuyện, đừng ép.\n\n");
                session.markTravelSuggested(suggested.name());
            }
        }

        // Travel marker instruction (when in Sweet Dominion or sub-location)
        if (current != null || "SWEET_DOMINION".equals(currentLocation)) {
            sb.append("=== Hướng Dẫn Di Chuyển ===\n");
            sb.append("Khi bé đồng ý di chuyển đến một địa điểm, ");
            sb.append("hãy thêm marker [TRAVEL:CODE] ở CUỐI câu trả lời.\n");
            sb.append("Các địa điểm có thể đến:\n");
            for (SubLocation sl : SubLocation.values()) {
                sb.append("- ").append(sl.name()).append(" = ")
                  .append(sl.getVietnameseAlias()).append("\n");
            }
            sb.append("Ví dụ: \"Đi thôi! Mình đến Đồi Bánh Quy nào! [TRAVEL:BISCUIT_HILLS]\"\n");
            sb.append("CHỈ dùng marker khi bé XÁC NHẬN muốn đi. Không tự ý di chuyển.\n\n");
        }

        return sb.toString();
    }

    private String interestLabel(List<String> interests) {
        return TopicClassifier.TOPIC_DISPLAY_NAMES
            .getOrDefault(interests.get(0), interests.get(0));
    }
}
