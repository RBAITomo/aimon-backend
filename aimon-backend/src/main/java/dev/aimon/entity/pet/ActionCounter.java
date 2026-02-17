package dev.aimon.entity.pet;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Action counter tracking for badge conditions
 */
@Entity
@Table(
    name = "action_counters",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "action_type"})
)
public class ActionCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(nullable = false)
    private Long count = 0L;

    @Column(name = "last_at")
    private LocalDateTime lastAt;

    @PrePersist
    protected void onCreate() {
        if (lastAt == null) {
            lastAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }

    public LocalDateTime getLastAt() { return lastAt; }
    public void setLastAt(LocalDateTime lastAt) { this.lastAt = lastAt; }
}
