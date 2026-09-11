package com.aegis.assessment_service.service;

import com.aegis.assessment_service.dto.RegistrationRequest;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.repository.AssessmentRegistrationRepository;
import com.aegis.assessment_service.repository.AssessmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssessmentRegistrationService {

    private final AssessmentRegistrationRepository registrationRepository;
    private final AssessmentRepository assessmentRepository;

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private static LocalDateTime nowIst() {
        return LocalDateTime.now(IST);
    }

    public void registerCandidate(String assessmentId, String candidateId, RegistrationRequest request) {
        // Idempotency check — use targeted query, not full scan
        if (registrationRepository.existsByAssessmentIdAndCandidateId(assessmentId, candidateId)) {
            throw new IllegalStateException("You are already registered for this assessment.");
        }

        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));

        // Registration closes when the tournament ends
        if (nowIst().isAfter(assessment.getEndTime())) {
            throw new IllegalStateException("Registration is closed. This assessment has ended.");
        }

        AssessmentRegistration registration = AssessmentRegistration.builder()
                .assessmentId(assessmentId)
                .candidateId(candidateId)
                .registeredAt(nowIst())         // ← UTC
                .status("REGISTERED")
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .occupation(request.occupation())
                .address(request.address())
                .cityCountry(request.cityCountry())
                .resumeUrl(request.resumeUrl())
                .portfolioUrl(request.portfolioUrl())
                .githubId(request.githubId())
                .leetcodeId(request.leetcodeId())
                .profilePictureUrl(request.profilePictureUrl())
                .college(request.college())
                .degree(request.degree())
                .branch(request.branch())
                .currentYear(request.currentYear())
                .graduationYear(request.graduationYear())
                .cgpa(request.cgpa())
                .tenthPercentage(request.tenthPercentage())
                .twelfthPercentage(request.twelfthPercentage())
                .contestRating(request.contestRating())
                .problemsSolved(request.problemsSolved())
                .preferredContestTime(request.preferredContestTime())
                .availability(request.availability())
                .webcamAvailable(request.webcamAvailable())
                .internetStabilityConfirmed(request.internetStabilityConfirmed())
                .preferredLanguage(request.preferredLanguage())
                .skills(request.skills())
                .areasOfInterest(request.areasOfInterest())
                .build();

        registrationRepository.save(registration);
    }

    public List<Assessment> getCandidateActiveTournaments(String candidateId) {
        List<String> registeredAssessmentIds = registrationRepository
                .findByCandidateId(candidateId)
                .stream()
                .map(AssessmentRegistration::getAssessmentId)
                .toList();

        if (registeredAssessmentIds.isEmpty()) {
            return List.of();
        }

        return (List<Assessment>) assessmentRepository.findAllById(registeredAssessmentIds);
    }

    public List<AssessmentRegistration> getParticipantsForTournament(String companyId, String assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));

        if (!assessment.getCompanyId().equals(companyId)) {
            // Don't reveal that the assessment exists — return 403 from controller instead
            throw new SecurityException("You do not have access to this assessment.");
        }

        return registrationRepository.findByAssessmentId(assessmentId);
    }

    public void startWorkspaceTimer(String assessmentId, String candidateId) {
        // FIX: Use targeted compound query — much safer and more efficient than
        // fetching ALL of candidateId's registrations and filtering in memory.
        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new IllegalArgumentException("You are not registered for this assessment."));

        // Security gate: don't allow re-entry into locked sessions
        if ("COMPLETED".equals(registration.getStatus()) || "TERMINATED".equals(registration.getStatus())) {
            throw new SecurityException(
                    "ACCESS DENIED: This session is " + registration.getStatus().toLowerCase() +
                            " and cannot be resumed."
            );
        }

        // Only stamp start time on the very first workspace load
        if (!"IN_PROGRESS".equals(registration.getStatus())) {
            registration.setStartedAt(nowIst());    // ← UTC
            registration.setStatus("IN_PROGRESS");
            registrationRepository.save(registration);
        }
    }

    @Transactional
    public void lockAssessment(String assessmentId, String candidateId, String status) {
        // Validate the status value before persisting
        if (!List.of("COMPLETED", "TERMINATED").contains(status)) {
            throw new IllegalArgumentException("Invalid lock status: " + status);
        }

        // FIX: Use targeted compound query
        AssessmentRegistration reg = registrationRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new RuntimeException("Registration not found."));

        // Idempotent: don't downgrade a TERMINATED to COMPLETED
        if ("TERMINATED".equals(reg.getStatus())) {
            return; // Already in worst-case state, don't change it
        }

        reg.setStatus(status);
        registrationRepository.save(reg);
    }
}