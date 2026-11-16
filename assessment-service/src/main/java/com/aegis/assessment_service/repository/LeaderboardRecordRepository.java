package com.aegis.assessment_service.repository;

import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LeaderboardRecordRepository extends MongoRepository<LeaderboardRecord, String> {
    Optional<LeaderboardRecord> findByAssessmentIdAndCandidateId(String assessmentId, String candidateId);

    List<LeaderboardRecord> findByAssessmentId(String assessmentId);

    List<LeaderboardRecord> findByAssessmentIdOrderByTotalPointsDescTotalTimePenaltySecondsAsc(String assessmentId);
}
