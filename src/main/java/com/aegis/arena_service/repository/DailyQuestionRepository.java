package com.aegis.arena_service.repository;

import com.aegis.arena_service.model.DailyQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyQuestionRepository extends MongoRepository<DailyQuestion, String> {

    /**
     * Find the single active daily question: activatesAt <= now < expiresAt
     * Used by the public endpoint to return today's challenge.
     */
    Optional<DailyQuestion> findFirstByActivatesAtLessThanEqualAndExpiresAtGreaterThan(
            Instant activatesAt, Instant expiresAt);

    /**
     * Find all scheduled daily questions, sorted by activation time descending.
     * Used by the admin management panel.
     */
    List<DailyQuestion> findAllByOrderByActivatesAtDesc();
}
