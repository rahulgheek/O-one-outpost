package com.aegis.code_sandbox.judge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the line-oriented protocol emitted by the arena/assessment test
 * harness. Both execution backends share this logic so a submission gets the
 * same verdict regardless of where it ran.
 */
public final class JudgeOutput {

    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile("(?m)^\\s*PROGRESS\\|(\\d+)/(\\d+)\\s*$");
    private static final Pattern ASSESSMENT_PATTERN =
            Pattern.compile("(?m)^\\s*ASSESSMENT_RESULT\\|(\\d+)\\|(\\d+)\\s*$");
    private static final Pattern ALL_PASSED_PATTERN =
            Pattern.compile("(?m)^\\s*ALL_TESTS_PASSED\\s*$");
    private static final Pattern WRONG_ANSWER_PATTERN =
            Pattern.compile("(?m)^\\s*WRONG_ANSWER\\b");
    private static final Pattern RUNTIME_ERROR_PATTERN =
            Pattern.compile("(?m)^\\s*RUNTIME_ERROR\\b");

    private JudgeOutput() {
    }

    public static Verdict classify(String stdout, String stderr, PassCounts counts) {
        String combined = nullToEmpty(stdout) + "\n" + nullToEmpty(stderr);
        if (ALL_PASSED_PATTERN.matcher(combined).find()) {
            return Verdict.AC;
        }
        if (RUNTIME_ERROR_PATTERN.matcher(combined).find()) {
            return Verdict.RE;
        }
        if (WRONG_ANSWER_PATTERN.matcher(combined).find()) {
            return Verdict.WA;
        }
        if (counts != null && counts.known()) {
            return counts.passed() >= counts.total() ? Verdict.AC : Verdict.WA;
        }
        return Verdict.AC;
    }

    public static PassCounts parseCounts(String stdout) {
        Integer passed = null;
        Integer total = null;
        if (stdout != null) {
            Matcher progress = PROGRESS_PATTERN.matcher(stdout);
            while (progress.find()) {
                passed = parseInt(progress.group(1));
                total = parseInt(progress.group(2));
            }
            if (passed == null) {
                Matcher assessment = ASSESSMENT_PATTERN.matcher(stdout);
                while (assessment.find()) {
                    passed = parseInt(assessment.group(1));
                    total = parseInt(assessment.group(2));
                }
            }
        }
        return new PassCounts(passed, total);
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record PassCounts(Integer passed, Integer total) {
        public boolean known() {
            return passed != null && total != null;
        }
    }
}
