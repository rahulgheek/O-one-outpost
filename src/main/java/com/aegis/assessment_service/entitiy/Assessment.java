package com.aegis.assessment_service.entitiy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assessments")
public class Assessment {
    @Id
    private String id;

    private String companyId;
    private String title;
    private String description;
    private String testCode;

    // THE NEW TIME LOGIC
    private LocalDateTime startTime; // When the recruiter allows people to start
    private LocalDateTime endTime;   // The hard cutoff for the entire test
    private int durationMinutes;     // e.g., 90 minutes total for all questions

    private List<String> companyProblemIds;
    private String status;
}