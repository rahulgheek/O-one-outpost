package com.aegis.assessment_service.entitiy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assessment_leaderboards")
public class LeaderboardRecord {
    @Id
    private String id;

    private String assessmentId;
    private String candidateId;

    private int totalPoints;

    // For tie-breakers: Calculated as (submissionTime - assessment.startTime)
    // The lower this number, the higher they rank!
    private long totalTimePenaltySeconds;
}