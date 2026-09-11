package com.aegis.assessment_service.repository;

import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentRegistrationRepository extends MongoRepository<AssessmentRegistration,String> {
    boolean existsByAssessmentIdAndCandidateId(String assessmentId,String candidateId);

    List<AssessmentRegistration> findByCandidateId(String candidateId);
    List<AssessmentRegistration> findByAssessmentId(String assessmentId);

    Optional<AssessmentRegistration> findByAssessmentIdAndCandidateId(String assessmentId, String candidateId);
}
