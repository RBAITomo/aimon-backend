package dev.aimon.service.conversation;

import dev.aimon.service.ai.LiteLlmAIService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Classifies Vietnamese messages into topic categories for interest tracking.
 * Strategy: keyword matching first (fast, <1ms). LLM fallback if no match (~5% of messages).
 */
@ApplicationScoped
public class TopicClassifier {

    private static final Logger LOG = Logger.getLogger(TopicClassifier.class);

    @Inject
    LiteLlmAIService aiService;

    // topic code -> Vietnamese keywords (lowercase, normalized)
    private static final Map<String, List<String>> TOPIC_KEYWORDS = Map.ofEntries(
        Map.entry("khung-long", List.of("khủng long", "t-rex", "hóa thạch", "tiền sử", "bạo long")),
        Map.entry("vu-tru", List.of("vũ trụ", "hành tinh", "ngôi sao", "phi hành gia", "mặt trăng", "thiên hà")),
        Map.entry("dong-vat", List.of("chó", "mèo", "thú", "động vật", "hổ", "gấu", "cá voi", "voi", "chim")),
        Map.entry("xe-co", List.of("ô tô", "máy bay", "tàu lửa", "xe cộ", "tàu vũ trụ")),
        Map.entry("sieu-anh-hung", List.of("siêu nhân", "anh hùng", "phép màu", "siêu lực")),
        Map.entry("am-nhac", List.of("hát", "bài hát", "nhạc cụ", "đàn guitar", "trống")),
        Map.entry("nghe-thuat", List.of("vẽ", "tô màu", "nghệ thuật", "màu sắc", "tranh")),
        Map.entry("nau-an", List.of("nấu ăn", "món ăn", "thức ăn", "nấu cơm", "bánh", "kẹo")),
        Map.entry("the-thao", List.of("đá bóng", "bơi lội", "thể thao", "bóng đá", "thể dục")),
        Map.entry("co-tich", List.of("công chúa", "hoàng tử", "phép thuật", "cổ tích", "thần tiên")),
        Map.entry("truong-hoc", List.of("trường học", "bài tập", "thầy cô", "bài học", "lớp học")),
        Map.entry("gia-dinh", List.of("ba mẹ", "ông bà", "anh chị", "gia đình", "bố mẹ")),
        Map.entry("thien-nhien", List.of("biển cả", "núi rừng", "cây cối", "hoa lá", "thiên nhiên")),
        Map.entry("khoa-hoc", List.of("thí nghiệm", "phát minh", "robot", "khoa học", "công nghệ")),
        Map.entry("sach-truyen", List.of("truyện tranh", "đọc sách", "câu chuyện", "nhân vật", "sách"))
    );

    /** Vietnamese display names for topic codes — used by AdaptiveInterestService and WorldLoreService matching. */
    public static final Map<String, String> TOPIC_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("khung-long", "Khủng long"),
        Map.entry("vu-tru", "Vũ trụ"),
        Map.entry("dong-vat", "Động vật"),
        Map.entry("xe-co", "Xe cộ"),
        Map.entry("sieu-anh-hung", "Siêu anh hùng"),
        Map.entry("am-nhac", "Âm nhạc"),
        Map.entry("nghe-thuat", "Nghệ thuật"),
        Map.entry("nau-an", "Nấu ăn"),
        Map.entry("the-thao", "Thể thao"),
        Map.entry("co-tich", "Cổ tích"),
        Map.entry("truong-hoc", "Trường học"),
        Map.entry("gia-dinh", "Gia đình"),
        Map.entry("thien-nhien", "Thiên nhiên"),
        Map.entry("khoa-hoc", "Khoa học"),
        Map.entry("sach-truyen", "Sách/Truyện")
    );

    private static final String LLM_CLASSIFY_PROMPT =
        "Phân loại câu sau vào 1-3 chủ đề từ danh sách:\n"
        + "khung-long, vu-tru, dong-vat, xe-co, sieu-anh-hung, am-nhac, nghe-thuat, "
        + "nau-an, the-thao, co-tich, truong-hoc, gia-dinh, thien-nhien, khoa-hoc, sach-truyen\n"
        + "Trả về CHỈ mã chủ đề, cách nhau bằng dấu phẩy. Nếu không khớp, trả về: none\n"
        + "Câu cần phân loại: ";

    /**
     * Keyword-only classification (no LLM fallback). Used for bulk re-classification of timeline entries.
     */
    public List<String> classifyKeywordOnly(String message) {
        if (message == null || message.isBlank()) return List.of();
        String lower = message.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * Classify message into topic codes with LLM fallback. Returns empty list if no topics detected.
     * Note: Vietnamese keyword matching is accent-sensitive; STT output without diacritics may miss.
     */
    public List<String> classify(String message) {
        if (message == null || message.isBlank()) return List.of();

        String lower = message.toLowerCase();
        List<String> matched = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }

        if (!matched.isEmpty()) {
            LOG.debugf("Keyword match: %s → %s",
                message.substring(0, Math.min(30, message.length())), matched);
            return matched;
        }

        return classifyWithLlm(message);
    }

    private List<String> classifyWithLlm(String message) {
        try {
            String result = aiService.generateResponse(
                LLM_CLASSIFY_PROMPT + message,
                "Bạn là bộ phân loại chủ đề. Chỉ trả về mã chủ đề, không giải thích."
            );
            if (result == null || result.isBlank() || result.toLowerCase().contains("none")) {
                return List.of();
            }
            return Arrays.stream(result.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(TOPIC_KEYWORDS::containsKey)
                .limit(3)
                .toList();
        } catch (Exception e) {
            LOG.warnf("LLM topic classification failed: %s", e.getMessage());
            return List.of();
        }
    }
}
