package dev.aimon.entity.pet;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pet variant configurations for transformations
 */
@Entity
@Table(name = "pet_variants")
public class PetVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "persona_template", nullable = false, columnDefinition = "TEXT")
    private String personaTemplate;

    @Column(name = "sprite_prefix", nullable = false, length = 50)
    private String spritePrefix;

    @Column(name = "min_level")
    private Integer minLevel = 12;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_badges", nullable = false, columnDefinition = "jsonb")
    private List<String> requiredBadges;

    @Column(name = "transform_base_minutes")
    private Integer transformBaseMinutes = 30;

    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes = 60;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPersonaTemplate() { return personaTemplate; }
    public void setPersonaTemplate(String personaTemplate) { this.personaTemplate = personaTemplate; }

    public String getSpritePrefix() { return spritePrefix; }
    public void setSpritePrefix(String spritePrefix) { this.spritePrefix = spritePrefix; }

    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }

    public List<String> getRequiredBadges() { return requiredBadges; }
    public void setRequiredBadges(List<String> requiredBadges) { this.requiredBadges = requiredBadges; }

    public Integer getTransformBaseMinutes() { return transformBaseMinutes; }
    public void setTransformBaseMinutes(Integer transformBaseMinutes) { this.transformBaseMinutes = transformBaseMinutes; }

    public Integer getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(Integer cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
