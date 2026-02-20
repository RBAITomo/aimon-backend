package dev.aimon.entity;

import jakarta.persistence.*;

/**
 * User (child) entity mapped to the aimon.users table.
 * Used for personalizing conversation prompts with child's name and age.
 */
@Entity
@Table(name = "users", schema = "aimon")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column
    private Integer age;

    @Column(name = "parent_id")
    private Long parentId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
