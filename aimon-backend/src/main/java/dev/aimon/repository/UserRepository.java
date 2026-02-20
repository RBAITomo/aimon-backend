package dev.aimon.repository;

import dev.aimon.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Repository for querying users (children) table.
 * Used to fetch child name/age for prompt personalization.
 */
@ApplicationScoped
public class UserRepository {

    @Inject
    EntityManager em;

    public User findById(Long id) {
        return em.find(User.class, id);
    }
}
