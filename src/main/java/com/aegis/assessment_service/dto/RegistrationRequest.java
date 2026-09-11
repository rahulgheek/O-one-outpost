package com.aegis.assessment_service.dto;

import java.util.List;

public record RegistrationRequest(
        String fullName,
        String phoneNumber,
        String occupation,
        String address,
        String cityCountry,

        String resumeUrl,
        String portfolioUrl,
        String githubId,
        String leetcodeId,
        String profilePictureUrl,

        String college,
        String degree,
        String branch,
        String currentYear,
        String graduationYear,
        Double cgpa,
        Double tenthPercentage,
        Double twelfthPercentage,

        Integer contestRating,
        Integer problemsSolved,
        String preferredContestTime,
        String availability,
        Boolean webcamAvailable,
        Boolean internetStabilityConfirmed,
        String preferredLanguage,

        List<String> skills,
        List<String> areasOfInterest
) {}