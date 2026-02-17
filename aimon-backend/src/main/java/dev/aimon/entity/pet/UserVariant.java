package dev.aimon.entity.pet;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Permanently unlocked variants per user
 */
@Entity
@Table(
    name = "user_variants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "variant_id"})
)
public class UserVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private PetVariant variant;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "times_used")
    private Integer timesUsed = 0;

    @PrePersist
    protected void onCreate() {
        if (unlockedAt == null) {
            unlockedAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public PetVariant getVariant() { return variant; }
    public void setVariant(PetVariant variant) { this.variant = variant; }

    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }

    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer timesUsed) { this.timesUsed = timesUsed; }
}
