package com.aegis.assessment_service.service;

import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.repository.CompanyProblemRepository;
import com.aegis.assessment_service.util.AssessmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanyProblemService {

    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    /**
     * The generated Java harness parses inputs and formats outputs with a fixed
     * set of types. Anything else would compile locally in the builder UI but
     * fail at submission time, so reject it when the problem is created.
     */
    private static final Set<String> ALLOWED_PARAMETER_TYPES = Set.of(
            "int", "long", "double", "boolean", "String", "int[]");
    private static final Set<String> ALLOWED_RETURN_TYPES = Set.of(
            "int", "long", "double", "boolean", "String",
            "int[]", "long[]", "double[]", "boolean[]", "String[]");

    private final CompanyProblemRepository problemRepository;

    public CompanyProblem createPrivateProblem(String companyId, CompanyProblem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("Problem payload is required.");
        }
        validate(problem);

        problem.setId(null);            // never overwrite an existing problem by id
        problem.setCompanyId(companyId); // ownership is always derived from the JWT
        problem.setDifficulty(AssessmentPolicy.normalizeDifficulty(problem.getDifficulty()));
        if (problem.getTimeLimitMs() <= 0) {
            problem.setTimeLimitMs(2000);
        }
        if (problem.getMemoryLimitKb() <= 0) {
            problem.setMemoryLimitKb(256000);
        }
        return problemRepository.save(problem);
    }

    public List<CompanyProblem> getProblemsByCompany(String companyId) {
        return problemRepository.findByCompanyId(companyId);
    }

    public CompanyProblem getProblemById(String problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found."));
    }

    public CompanyProblem getOwnedProblem(String companyId, String problemId) {
        CompanyProblem problem = getProblemById(problemId);
        if (companyId == null || !companyId.equals(problem.getCompanyId())) {
            throw new SecurityException("You do not have access to this problem.");
        }
        return problem;
    }

    private void validate(CompanyProblem problem) {
        if (problem.getTitle() == null || problem.getTitle().isBlank()) {
            throw new IllegalArgumentException("Problem title is required.");
        }
        if (problem.getDescription() == null || problem.getDescription().isBlank()) {
            throw new IllegalArgumentException("Problem description is required.");
        }
        String difficulty = AssessmentPolicy.normalizeDifficulty(problem.getDifficulty());
        if (!ALLOWED_DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("Difficulty must be EASY, MEDIUM, or HARD.");
        }

        if (problem.getMethodName() == null || problem.getMethodName().isBlank()) {
            throw new IllegalArgumentException("Method name is required for the Java harness.");
        }
        // The harness inserts the method name into generated Java source.
        if (!problem.getMethodName().trim().matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Method name must be a valid Java identifier.");
        }
        problem.setMethodName(problem.getMethodName().trim());

        if (problem.getReturnType() == null || problem.getReturnType().isBlank()) {
            throw new IllegalArgumentException("Return type is required for the Java harness.");
        }
        if (!ALLOWED_RETURN_TYPES.contains(problem.getReturnType().trim())) {
            throw new IllegalArgumentException(
                    "Unsupported return type '" + problem.getReturnType() + "'. Supported types: "
                            + String.join(", ", ALLOWED_RETURN_TYPES.stream().sorted().toList()) + ".");
        }

        if (problem.getParameterTypes() == null || problem.getParameterTypes().isEmpty()) {
            throw new IllegalArgumentException("At least one method parameter is required.");
        }
        if (problem.getParameterTypes().stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("Parameter types cannot be blank.");
        }
        for (String type : problem.getParameterTypes()) {
            if (!ALLOWED_PARAMETER_TYPES.contains(type.trim())) {
                throw new IllegalArgumentException(
                        "Unsupported parameter type '" + type + "'. Supported types: "
                                + String.join(", ", ALLOWED_PARAMETER_TYPES.stream().sorted().toList()) + ".");
            }
        }

        // Normalize before the harness interpolates these values into Java source.
        problem.setReturnType(problem.getReturnType().trim());
        problem.setParameterTypes(problem.getParameterTypes().stream()
                .map(String::trim)
                .toList());

        if (problem.getTestCases() == null || problem.getTestCases().isEmpty()) {
            throw new IllegalArgumentException("At least one test case is required.");
        }
        for (CompanyProblem.TestCase testCase : problem.getTestCases()) {
            if (testCase == null) {
                throw new IllegalArgumentException("Test cases cannot be null.");
            }
            if (testCase.getInputs() == null
                    || testCase.getInputs().size() != problem.getParameterTypes().size()) {
                throw new IllegalArgumentException(
                        "Each test case must provide exactly one input per method parameter.");
            }
            if (testCase.getInputs().stream().anyMatch(input -> input == null)) {
                throw new IllegalArgumentException("Test case inputs cannot be null.");
            }
            if (testCase.getExpectedOutput() == null) {
                throw new IllegalArgumentException("Each test case needs an expected output.");
            }
        }

        if (problem.getStartingCodeJava() == null || problem.getStartingCodeJava().isBlank()) {
            problem.setStartingCodeJava("class Solution {\n    // Write your solution here\n}");
        }
        problem.setDifficulty(difficulty.toUpperCase(Locale.ROOT));
    }
}
