package com.aegis.arena_service.repository;

import com.aegis.arena_service.model.Problem;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends MongoRepository<Problem,String> {
    List<Problem> findByDifficulty(String Difficulty);
    @Aggregation(pipeline = {
            "{ $match: { difficulty: ?0 } }",
            "{ $sample: { size: 1 } }"
    })
    Optional<Problem> findRandomByDifficulty(String difficulty);
    Optional<Problem> findByTitle(String title);

    List<Problem> findTop10ByTitleContainingIgnoreCase(String query);
}
