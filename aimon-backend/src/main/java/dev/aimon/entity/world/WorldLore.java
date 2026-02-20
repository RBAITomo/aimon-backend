package dev.aimon.entity.world;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * World lore entry — Cotton Land facts gated by pet level and shard type.
 * AMBIENT entries injected into conversation prompt; SIDE/MILESTONE for Phase 2b.
 */
@Entity
@Table(name = "world_lore", schema = "aimon")
public class WorldLore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_code", length = 50, nullable = false)
    private String worldCode;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(length = 50, nullable = false)
    private String category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "min_level", nullable = false)
    private Integer minLevel = 3;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interest_tags", columnDefinition = "TEXT[]")
    private String[] interestTags = new String[0];

    @Column(name = "shard_type", length = 20, nullable = false)
    private String shardType = "AMBIENT";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorldCode() { return worldCode; }
    public void setWorldCode(String worldCode) { this.worldCode = worldCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
    public String[] getInterestTags() { return interestTags; }
    public void setInterestTags(String[] interestTags) { this.interestTags = interestTags; }
    public String getShardType() { return shardType; }
    public void setShardType(String shardType) { this.shardType = shardType; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
