package dev.aimon.service.world;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sub-locations within Sweet Dominion.
 * Each maps to a background PNG, Vietnamese alias, and interest tags for travel suggestions.
 */
public enum SubLocation {
    /** Home/spawn location — intentionally no interest tags (not a suggestion target). */
    WHIPCREAM_SPIRE("Whipcream Spire", "Tháp Kem",
        "whipcream-Spire.png", List.of(), "SWEET_DOMINION"),
    MARSHMALLOW_MEADOW("Marshmallow Meadow", "Đồng Kẹo Dẻo",
        "marshmallow-meadow.png", List.of("dong-vat", "thien-nhien"), "SWEET_DOMINION"),
    CANDY_LANTERN_TOWN("Candy Lantern Town", "Thị Trấn Đèn Kẹo",
        "Candy-Lantern-Town.png", List.of("nghe-thuat", "gia-dinh"), "SWEET_DOMINION"),
    BISCUIT_HILLS("Biscuit Hills", "Đồi Bánh Quy",
        "Biscuit-Hills.png", List.of("nau-an", "the-thao"), "SWEET_DOMINION"),
    VANILLA_PROMENADE("Vanilla Promenade", "Đại Lộ Vani",
        "Vanilla-Promenade.png", List.of("am-nhac", "co-tich", "sach-truyen"), "SWEET_DOMINION");

    private final String displayName;
    private final String vietnameseAlias;
    private final String backgroundFile;
    private final List<String> interestTags;
    private final String parentRegion;

    SubLocation(String displayName, String vietnameseAlias, String backgroundFile,
                List<String> interestTags, String parentRegion) {
        this.displayName = displayName;
        this.vietnameseAlias = vietnameseAlias;
        this.backgroundFile = backgroundFile;
        this.interestTags = interestTags;
        this.parentRegion = parentRegion;
    }

    public String getDisplayName() { return displayName; }
    public String getVietnameseAlias() { return vietnameseAlias; }
    public String getBackgroundFile() { return backgroundFile; }
    public List<String> getInterestTags() { return interestTags; }
    public String getParentRegion() { return parentRegion; }

    /** Lookup by enum name. Returns empty if code not found. */
    public static Optional<SubLocation> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values())
            .filter(sl -> sl.name().equals(code))
            .findFirst();
    }

    /** Find sub-location with most overlapping interest tags. Skips locations with no tags. */
    public static Optional<SubLocation> bestMatchForInterests(List<String> interests) {
        if (interests == null || interests.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
            .filter(sl -> !sl.interestTags.isEmpty())
            .filter(sl -> sl.interestTags.stream().anyMatch(interests::contains))
            .max(Comparator.comparingLong(sl ->
                sl.interestTags.stream().filter(interests::contains).count()));
    }
}
