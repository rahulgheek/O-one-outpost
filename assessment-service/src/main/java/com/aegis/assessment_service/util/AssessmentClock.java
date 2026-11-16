package com.aegis.assessment_service.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for assessment wall-clock time.
 *
 * <p>Assessments are scheduled by companies in IST (the builder labels the input
 * "START TIME (IST)") and stored as naive {@link LocalDateTime} values. All
 * comparisons in this service must therefore use the same zone. Never call
 * {@link LocalDateTime#now()} directly in assessment code: the JVM default may
 * be UTC on the deployment host, which silently shifts every window by 5h30m.</p>
 */
public final class AssessmentClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AssessmentClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
