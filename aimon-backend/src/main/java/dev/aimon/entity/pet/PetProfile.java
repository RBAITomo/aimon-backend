package dev.aimon.entity.pet;

import dev.aimon.model.PetStage;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pet profile with stats, progression, and variant state (1:1 with user)
 */
@Entity
@Table(name = "pet_profiles")
public class PetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(length = 100)
    private String name = "Mon";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PetStage stage = PetStage.EGG;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private PetVariant variant;

    @Column(nullable = false)
    private Integer hunger = 50;

    @Column(nullable = false)
    private Integer energy = 100;

    @Column(nullable = false)
    private Integer happiness = 80;

    @Column(nullable = false)
    private Long xp = 0L;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(nullable = false)
    private Integer affinity = 0;

    @Column(name = "variant_expires_at")
    private LocalDateTime variantExpiresAt;

    @Column(name = "variant_cooldown_at")
    private LocalDateTime variantCooldownAt;

    @Column(name = "last_decay_at")
    private LocalDateTime lastDecayAt;

    @Column(name = "login_streak")
    private Integer loginStreak = 0;

    @Column(name = "total_sessions")
    private Integer totalSessions = 0;

    @Column(name = "regression_warnings")
    private Integer regressionWarnings = 0;

    @Column(name = "active_world", length = 50, nullable = false)
    private String activeWorld = "COTTON_LAND";

    @Column(name = "current_location", length = 50, nullable = false)
    private String currentLocation = "SWEET_DOMINION";

    @Column(name = "noir_last_attempt")
    private LocalDate noirLastAttempt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastDecayAt == null) {
            lastDecayAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PetStage getStage() { return stage; }
    public void setStage(PetStage stage) { this.stage = stage; }

    public PetVariant getVariant() { return variant; }
    public void setVariant(PetVariant variant) { this.variant = variant; }

    public Integer getHunger() { return hunger; }
    public void setHunger(Integer hunger) { this.hunger = hunger; }

    public Integer getEnergy() { return energy; }
    public void setEnergy(Integer energy) { this.energy = energy; }

    public Integer getHappiness() { return happiness; }
    public void setHappiness(Integer happiness) { this.happiness = happiness; }

    public Long getXp() { return xp; }
    public void setXp(Long xp) { this.xp = xp; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getAffinity() { return affinity; }
    public void setAffinity(Integer affinity) { this.affinity = affinity; }

    public LocalDateTime getVariantExpiresAt() { return variantExpiresAt; }
    public void setVariantExpiresAt(LocalDateTime variantExpiresAt) { this.variantExpiresAt = variantExpiresAt; }

    public LocalDateTime getVariantCooldownAt() { return variantCooldownAt; }
    public void setVariantCooldownAt(LocalDateTime variantCooldownAt) { this.variantCooldownAt = variantCooldownAt; }

    public LocalDateTime getLastDecayAt() { return lastDecayAt; }
    public void setLastDecayAt(LocalDateTime lastDecayAt) { this.lastDecayAt = lastDecayAt; }

    public Integer getLoginStreak() { return loginStreak; }
    public void setLoginStreak(Integer loginStreak) { this.loginStreak = loginStreak; }

    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }

    public Integer getRegressionWarnings() { return regressionWarnings; }
    public void setRegressionWarnings(Integer regressionWarnings) { this.regressionWarnings = regressionWarnings; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getActiveWorld() { return activeWorld; }
    public void setActiveWorld(String activeWorld) { this.activeWorld = activeWorld; }
    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
    public LocalDate getNoirLastAttempt() { return noirLastAttempt; }
    public void setNoirLastAttempt(LocalDate noirLastAttempt) { this.noirLastAttempt = noirLastAttempt; }
}
