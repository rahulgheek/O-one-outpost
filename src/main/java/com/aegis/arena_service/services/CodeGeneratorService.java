package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import com.aegis.judge.harness.HarnessGenerator;
import com.aegis.judge.harness.HarnessLanguage;
import com.aegis.judge.harness.HarnessSpec;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wraps a contestant's solution in the language-specific judge harness.
 *
 * <p>The harness embeds the problem's test cases as native literals and emits
 * the arena protocol ({@code PROGRESS}, {@code ALL_TESTS_PASSED},
 * {@code WRONG_ANSWER}, {@code RUNTIME_ERROR}). The same generator supports all
 * eight judge languages.</p>
 */
@Service
public class CodeGeneratorService {

    public String generateExecutableCode(String userCode, Problem problem) {
        return generateExecutableCode(userCode, problem, "java");
    }

    public String generateExecutableCode(String userCode, Problem problem, String languageId) {
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
        return HarnessGenerator.generate(language, userCode, spec, HarnessGenerator.Protocol.ARENA);
    }
}
