package dev.aimon.rest;

import dev.aimon.entity.pet.QuestQuestion;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for bulk importing quest questions from JSON.
 * Supports upsert (skip duplicates by code).
 */
@Path("/api/quests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QuestImportResource {

    private static final Logger LOG = Logger.getLogger(QuestImportResource.class);

    @Inject
    EntityManager em;

    @ConfigProperty(name = "quest.import.api-key")
    String apiKey;

    /**
     * Bulk import questions. Body: array of question objects.
     * Each: { code, category, difficulty, question, answer, hint? }
     */
    @POST
    @Path("/import")
    @Transactional
    public Response importQuestions(@HeaderParam("X-API-Key") String key, List<Map<String, String>> questions) {
        if (!apiKey.equals(key)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Invalid API key")).build();
        }

        if (questions == null || questions.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Empty question list")).build();
        }

        int inserted = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            Map<String, String> q = questions.get(i);
            String code = q.get("code");
            String category = q.get("category");
            String difficulty = q.get("difficulty");
            String questionText = q.get("question");
            String answer = q.get("answer");

            // Validate required fields
            if (code == null || category == null || difficulty == null || questionText == null || answer == null) {
                errors.add("Entry %d: missing required field (code/category/difficulty/question/answer)".formatted(i));
                continue;
            }

            // Check duplicate
            Long existing = em.createQuery("SELECT COUNT(q) FROM QuestQuestion q WHERE q.code = :code", Long.class)
                .setParameter("code", code)
                .getSingleResult();
            if (existing > 0) {
                skipped++;
                continue;
            }

            QuestQuestion entity = new QuestQuestion();
            entity.setCode(code);
            entity.setCategory(category);
            entity.setDifficulty(difficulty);
            entity.setQuestionText(questionText);
            entity.setExpectedAnswer(answer);
            entity.setHint(q.get("hint"));
            em.persist(entity);
            inserted++;
        }

        LOG.infof("Quest import: %d inserted, %d skipped, %d errors", inserted, skipped, errors.size());
        return Response.ok(Map.of("inserted", inserted, "skipped", skipped, "errors", errors)).build();
    }
}
