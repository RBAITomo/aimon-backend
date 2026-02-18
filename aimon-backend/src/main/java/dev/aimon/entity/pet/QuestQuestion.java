package dev.aimon.entity.pet;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Question bank entry for quest notifications.
 * Seeded from quest-bank.json via Flyway migration.
 */
@Entity
@Table(name = "quest_questions")
public class QuestQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 10)
    private String difficulty;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "expected_answer", nullable = false, columnDefinition = "TEXT")
    private String expectedAnswer;

    @Column(columnDefinition = "TEXT")
    private String hint;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getExpectedAnswer() { return expectedAnswer; }
    public void setExpectedAnswer(String expectedAnswer) { this.expectedAnswer = expectedAnswer; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
