package com.aegis.assessment_service.service;

import com.aegis.assessment_service.dto.CandidatePerformanceView;
import com.aegis.assessment_service.dto.RegistrationRequest;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import com.aegis.assessment_service.repository.AssessmentRegistrationRepository;
import com.aegis.assessment_service.repository.AssessmentRepository;
import com.aegis.assessment_service.repository.LeaderboardRecordRepository;
import com.aegis.assessment_service.util.AssessmentClock;
import com.aegis.assessment_service.util.AssessmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentRegistrationService {

    private final AssessmentRegistrationRepository registrationRepository;
    private final AssessmentRepository assessmentRepository;
    private final LeaderboardRecordRepository leaderboardRepository;

    // ─── CANDIDATE: REGISTER ─────────────────────────────────────

    @Transactional
    public void registerCandidate(String assessmentId, String candidateId, RegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration details are required.");
        }
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        if (request.college() == null || request.college().isBlank()) {
            throw new IllegalArgumentException("College / institution is required.");
        }

        // Idempotency check — targeted query, not a full scan
        if (registrationRepository.existsByAssessmentIdAndCandidateId(assessmentId, candidateId)) {
            throw new IllegalStateException("You are already registered for this assessment.");
        }

        Assessment assessment = requireAssessment(assessmentId);
        LocalDateTime now = AssessmentClock.now();

        // Registration closes the moment the assessment opens.
        if (assessment.getStartTime() == null || !now.isBefore(assessment.getStartTime())) {
            throw new IllegalStateException("Registration is closed. This assessment has already started.");
        }

        AssessmentRegistration registration = AssessmentRegistration.builder()
                .assessmentId(assessmentId)
                .candidateId(candidateId)
                .registeredAt(now)
                .status("REGISTERED")
                .fullName(request.fullName().trim())
                .phoneNumber(request.phoneNumber().trim())
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

        try {
            registrationRepository.save(registration);
        } catch (DuplicateKeyException ex) {
            // Two parallel submissions: the unique compound index wins.
            throw new IllegalStateException("You are already registered for this assessment.");
        }
    }

    // ─── CANDIDATE: DASHBOARD ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<Assessment> getCandidateActiveTournaments(String candidateId) {
        List<String> registeredAssessmentIds = registrationRepository
                .findByCandidateId(candidateId)
                .stream()
                .map(AssessmentRegistration::getAssessmentId)
                .toList();

        if (registeredAssessmentIds.isEmpty()) {
            return List.of();
        }
        return assessmentRepository.findAllById(registeredAssessmentIds);
    }

    // ─── CANDIDATE: WORKSPACE LIFECYCLE ──────────────────────────

    @Transactional(noRollbackFor = SecurityException.class)
    public void startWorkspaceTimer(String assessmentId, String candidateId) {
        Assessment assessment = requireAssessment(assessmentId);
        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new SecurityException("You are not registered for this assessment."));

        if (AssessmentPolicy.isFinal(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: This session is "
                    + registration.getStatus().toLowerCase(Locale.ROOT) + " and cannot be resumed.");
        }

        LocalDateTime now = AssessmentClock.now();
        if (assessment.getStartTime() == null || now.isBefore(assessment.getStartTime())) {
            throw new IllegalStateException("This assessment has not started yet.");
        }
        if (assessment.getEndTime() != null && now.isAfter(assessment.getEndTime())) {
            throw new IllegalStateException("This assessment has already concluded.");
        }

        if (AssessmentPolicy.isRegistered(registration.getStatus())) {
            if (now.isAfter(assessment.getStartTime().plus(AssessmentPolicy.LATE_ENTRY_WINDOW))) {
                throw new SecurityException(
                        "ACCESS DENIED: The late entry window has closed. "
                                + "You must enter within 10 minutes of the start time.");
            }
            registration.setStartedAt(now);
            registration.setStatus("IN_PROGRESS");
        } else if (!AssessmentPolicy.isInProgress(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: This registration is not active.");
        }

        LocalDateTime deadline = AssessmentPolicy.deadline(assessment, registration);
        if (deadline != null && now.isAfter(deadline)) {
            registration.setStatus("COMPLETED");
            registrationRepository.save(registration);
            throw new SecurityException("ACCESS DENIED: Your assessment window has ended.");
        }

        registrationRepository.save(registration);
    }

    @Transactional
    public void lockAssessment(String assessmentId, String candidateId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("COMPLETED", "TERMINATED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid lock status: " + status);
        }

        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new SecurityException("Registration not found."));

        // Idempotent: never downgrade a TERMINATED session.
        if ("TERMINATED".equals(registration.getStatus())) {
            return;
        }
        // A COMPLETED session can be escalated to TERMINATED by proctoring.
        if (normalized.equals(registration.getStatus())) {
            return;
        }

        registration.setStatus(normalized);
        registrationRepository.save(registration);
    }

    // ─── COMPANY: SELECTION ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssessmentRegistration> getParticipantsForTournament(String companyId, String assessmentId) {
        requireOwnedAssessment(companyId, assessmentId);
        return registrationRepository.findByAssessmentId(assessmentId);
    }

    /**
     * Combined registration + leaderboard rows for the company selection table.
     * The frontend previously expected totals on the registration object itself.
     */
    @Transactional(readOnly = true)
    public List<CandidatePerformanceView> getCandidatePerformance(String companyId, String assessmentId) {
        requireOwnedAssessment(companyId, assessmentId);

        Map<String, LeaderboardRecord> recordsByCandidate = leaderboardRepository
                .findByAssessmentId(assessmentId)
                .stream()
                .collect(Collectors.toMap(
                        LeaderboardRecord::getCandidateId,
                        Function.identity(),
                        (first, ignored) -> first));

        return registrationRepository.findByAssessmentId(assessmentId).stream()
                .map(registration -> {
                    LeaderboardRecord record = recordsByCandidate.get(registration.getCandidateId());
                    return new CandidatePerformanceView(
                            registration.getId(),
                            registration.getCandidateId(),
                            registration.getFullName(),
                            registration.getPhoneNumber(),
                            registration.getCollege(),
                            registration.getDegree(),
                            registration.getStatus(),
                            registration.getRegisteredAt(),
                            registration.getStartedAt(),
                            record == null ? 0 : record.getTotalPoints(),
                            record == null ? 0L : record.getTotalTimePenaltySeconds()
                    );
                })
                .toList();
    }

    private Assessment requireAssessment(String assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));
    }

    private Assessment requireOwnedAssessment(String companyId, String assessmentId) {
        Assessment assessment = requireAssessment(assessmentId);
        if (companyId == null || !companyId.equals(assessment.getCompanyId())) {
            // Do not reveal that the assessment exists
            throw new SecurityException("You do not have access to this assessment.");
        }
        return assessment;
    }
}
