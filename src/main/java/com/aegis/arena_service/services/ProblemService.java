package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.ProblemRepository;
import com.aegis.judge.harness.HarnessGenerator;
import com.aegis.judge.harness.HarnessLanguage;
import com.aegis.judge.harness.HarnessSpec;
import com.aegis.judge.harness.StarterCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProblemService {
    private final ProblemRepository problemRepository;

    public Problem createProblem(Problem problem) {
        validateProblem(problem);
        problem.setStartingCode(cleanOverrides(problem.getStartingCode()));
        return problemRepository.save(problem);
    }

    public List<Problem> getAllProblems() {
        return problemRepository.findAll();
    }

    /**
     * Returns the problem with {@code startingCode} resolved for every judge
     * language. Only the GET endpoints call this: the resolved map is a view,
     * not data that should be written back to MongoDB.
     */
    public Problem withStartingCode(Problem problem) {
        if (problem == null) {
            return null;
        }
        problem.setStartingCode(StarterCode.forProblem(
                problem.getStartingCode(),
                problem.getStartingCodeJava(),
                problem.getMethodName(),
                problem.getReturnType(),
                problem.getParameterTypes()));
        return problem;
    }

    public void updateProblemByTitle(String title, Problem updatedProblem) {
        // Validate before touching the stored document so a rejected update
        // cannot leave a half-applied problem in place.
        validateProblem(updatedProblem);

        // Find the existing problem by its exact title
        Problem existingProblem = problemRepository.findByTitle(title)
                .orElseThrow(() -> new RuntimeException("Problem with title '" + title + "' not found!"));

        // Overwrite the fields
        existingProblem.setDifficulty(updatedProblem.getDifficulty());
        existingProblem.setDescription(updatedProblem.getDescription());
        existingProblem.setMethodName(updatedProblem.getMethodName());
        existingProblem.setReturnType(updatedProblem.getReturnType());
        existingProblem.setParameterTypes(updatedProblem.getParameterTypes());
        existingProblem.setStartingCodeJava(updatedProblem.getStartingCodeJava());
        // Do not copy startingCode. Admin clients PUT the resolved view they
        // received from GET; persisting that view would freeze generated stubs
        // and make them stale after a signature change. Any explicit
        // per-language overrides already stored on the problem are preserved.
        existingProblem.setTestCases(updatedProblem.getTestCases());
        if (updatedProblem.getTimeLimitMs() > 0) {
            existingProblem.setTimeLimitMs(updatedProblem.getTimeLimitMs());
        }
        if (updatedProblem.getMemoryLimitKb() > 0) {
            existingProblem.setMemoryLimitKb(updatedProblem.getMemoryLimitKb());
        }

        // Save it back to MongoDB
        problemRepository.save(existingProblem);
    }

    /**
     * Rejects problems the generated harnesses cannot judge in every supported
     * language. This mirrors the validation in assessment-service so a problem
     * cannot be created successfully and then fail for C or Rust submissions.
     */
    private void validateProblem(Problem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("Problem payload is required.");
        }
        if (problem.getTitle() == null || problem.getTitle().isBlank()) {
            throw new IllegalArgumentException("Problem title is required.");
        }
        if (problem.getMethodName() == null || problem.getMethodName().isBlank()) {
            throw new IllegalArgumentException("Method name is required for the judge harness.");
        }
        // The method name is interpolated into source in every language, so
        // only the identifier subset shared by all of them is safe.
        if (!problem.getMethodName().trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Method name must be a valid identifier shared by all supported languages.");
        }
        problem.setMethodName(problem.getMethodName().trim());

        if (problem.getReturnType() == null || problem.getReturnType().isBlank()) {
            throw new IllegalArgumentException("Return type is required for the judge harness.");
        }
        problem.setReturnType(problem.getReturnType().trim());
        HarnessSpec.kind(problem.getReturnType());

        if (problem.getParameterTypes() == null) {
            throw new IllegalArgumentException("Parameter types are required for the judge harness.");
        }
        if (problem.getParameterTypes().stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("Parameter types cannot be blank.");
        }
        problem.setParameterTypes(problem.getParameterTypes().stream()
                .map(String::trim)
                .map(type -> {
                    HarnessSpec.Kind kind = HarnessSpec.kind(type);
                    if (kind.isArray() && kind != HarnessSpec.Kind.INT_ARRAY) {
                        throw new IllegalArgumentException("Unsupported parameter type '" + type
                                + "'. Only int[] arrays can be parameters.");
                    }
                    return type;
                })
                .toList());

        if (problem.getTestCases() == null || problem.getTestCases().isEmpty()) {
            throw new IllegalArgumentException("At least one test case is required.");
        }
        for (Problem.TestCase testCase : problem.getTestCases()) {
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

        HarnessSpec.Signature signature = new HarnessSpec.Signature(
                problem.getMethodName(), problem.getReturnType(), problem.getParameterTypes());
        List<HarnessSpec.TestCase> testCases = problem.getTestCases().stream()
                .map(testCase -> new HarnessSpec.TestCase(
                        testCase.getInputs(), testCase.getExpectedOutput()))
                .toList();
        new HarnessSpec.Spec(signature, testCases);

        // Fail fast on literals that would only break once a candidate submits.
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (HarnessSpec.TestCase testCase : testCases) {
            for (int i = 0; i < kinds.size(); i++) {
                HarnessSpec.parseLiteral(testCase.inputs().get(i), kinds.get(i));
            }
        }

        if (problem.getStartingCodeJava() == null || problem.getStartingCodeJava().isBlank()) {
            problem.setStartingCodeJava(HarnessGenerator.starterCode(HarnessLanguage.JAVA, signature));
        }
        if (problem.getDifficulty() != null) {
            problem.setDifficulty(problem.getDifficulty().trim().toUpperCase(Locale.ROOT));
        }
    }

    private static Map<String, String> cleanOverrides(Map<String, String> overrides) {
        if (overrides == null) {
            return null;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        overrides.forEach((language, code) -> {
            if (language != null && code != null && !code.isBlank()) {
                cleaned.put(language, code);
            }
        });
        return cleaned.isEmpty() ? null : cleaned;
    }
}
