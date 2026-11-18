package com.aegis.assessment_service.entitiy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assessment_submissions")
public class CandidateSubmission {
    @Id
    private String id;

    private String assessmentId;
    private String candidateId;
    private String problemId;

    private String submittedCode;
    /** Judge language of the submission. */
    private String language;

    // For partial credit scoring
    private int testCasesPassed;
    private int totalTestCases;
    private int pointsAwarded;

    private String executionStatus; // e.g., "PARTIAL_SUCCESS", "ALL_PASSED", "RUNTIME_ERROR"
    private LocalDateTime submissionTime;
}