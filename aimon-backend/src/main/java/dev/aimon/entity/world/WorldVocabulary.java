package dev.aimon.entity.world;

import jakarta.persistence.*;

/**
 * Vocabulary term for a game world, used as Google STT phrase hint.
 * Gated by min_level so terms are only hinted once the pet reaches that level.
 */
@Entity
@Table(name = "world_vocabulary", schema = "aimon")
public class WorldVocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_code", length = 50, nullable = false)
    private String worldCode;

    @Column(length = 100, nullable = false)
    private String term;

    @Column(name = "min_level", nullable = false)
    private Integer minLevel = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorldCode() { return worldCode; }
    public void setWorldCode(String worldCode) { this.worldCode = worldCode; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
