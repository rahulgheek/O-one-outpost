package com.aegis.assessment_service.repository;

import com.aegis.assessment_service.entitiy.CandidateSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CandidateSubmissionRepository extends MongoRepository<CandidateSubmission, String> {
    List<CandidateSubmission> findByAssessmentIdAndCandidateIdAndProblemId(String assessmentId, String candidateId, String problemId);

    List<CandidateSubmission> findByAssessmentIdAndCandidateId(String assessmentId, String candidateId);
}
