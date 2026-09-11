package com.aegis.arena_service.repository;

import com.aegis.arena_service.model.Match;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchRepository extends MongoRepository<Match,String> {
    List<Match> findByStatus(String status);

    List<Match> findByPlayerOneIdOrPlayerTwoId(String playerOneId,String playerTwoId);

    // Finds waiting matches where Player 1's rating is between (myRating - 100) and (myRating + 100)
    List<Match> findByStatusAndPlayerOneRatingBetween(String status, Integer minRating, Integer maxRating);
}
