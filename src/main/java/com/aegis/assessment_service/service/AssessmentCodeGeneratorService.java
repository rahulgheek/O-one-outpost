package com.aegis.assessment_service.service;

import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.judge.harness.HarnessGenerator;
import com.aegis.judge.harness.HarnessLanguage;
import com.aegis.judge.harness.HarnessSpec;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wraps a candidate's solution in the language-specific judge harness.
 *
 * <p>The assessment harness emits {@code ASSESSMENT_RESULT|passed|total} so
 * partial credit survives, alongside per-test {@code WRONG_ANSWER} and
 * {@code RUNTIME_ERROR} lines.</p>
 */
@Service
public class AssessmentCodeGeneratorService {

    public String generateExecutableCode(String userCode, CompanyProblem problem) {
        return generateExecutableCode(userCode, problem, "java");
    }

    public String generateExecutableCode(String userCode, CompanyProblem problem, String languageId) {
        HarnessLanguage language = HarnessLanguage.resolve(languageId);

        if (problem.getMethodName() == null || problem.getMethodName().isBlank()
                || problem.getReturnType() == null || problem.getReturnType().isBlank()
                || problem.getParameterTypes() == null) {
            throw new IllegalArgumentException(
                    "This problem is missing method metadata and cannot be judged.");
        }
        HarnessSpec.Signature signature = new HarnessSpec.Signature(
                problem.getMethodName(), problem.getReturnType(), problem.getParameterTypes());

        if (problem.getTestCases() == null || problem.getTestCases().isEmpty()) {
            throw new IllegalArgumentException("This problem has no test cases configured.");
        }
        List<HarnessSpec.TestCase> testCases = problem.getTestCases().stream()
                .map(testCase -> new HarnessSpec.TestCase(
                        testCase.getInputs(), testCase.getExpectedOutput()))
                .toList();

        HarnessSpec.Spec spec = new HarnessSpec.Spec(signature, testCases);
        return HarnessGenerator.generate(language, userCode, spec, HarnessGenerator.Protocol.ASSESSMENT);
    }
}
