package com.aegis.assessment_service.entitiy;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "assessment_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "assessment_candidate_idx",
        def = "{'assessmentId': 1, 'candidateId': 1}",
        unique = true)
public class AssessmentRegistration {

    @Id
    private String id; // MongoDB uses String (ObjectId)

    private String assessmentId;
    private String candidateId;
    private LocalDateTime registeredAt;
    private LocalDateTime startedAt; // When they actually clicked "Start Test"

    // 🚨 UNIFIED STATUS FIELD: "REGISTERED", "IN_PROGRESS", "COMPLETED", "TERMINATED"
    @Builder.Default
    private String status = "REGISTERED";

    // ==========================================
    // CANDIDATE GRANULAR DETAILS
    // ==========================================

    private String fullName;
    private String phoneNumber;
    private String occupation;
    private String address;
    private String cityCountry;

    private String resumeUrl;
    private String portfolioUrl;
    private String githubId;
    private String leetcodeId;
    private String profilePictureUrl;

    private String college;
    private String degree;
    private String branch;
    private String currentYear;
    private String graduationYear;
    private Double cgpa;
    private Double tenthPercentage;
    private Double twelfthPercentage;

    private Integer contestRating;
    private Integer problemsSolved;
    private String preferredContestTime;
    private String availability;
    private Boolean webcamAvailable;
    private Boolean internetStabilityConfirmed;
    private String preferredLanguage;

    private List<String> skills;
    private List<String> areasOfInterest;
}