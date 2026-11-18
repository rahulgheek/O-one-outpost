package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGeneratorServiceTest {

    private final CodeGeneratorService service = new CodeGeneratorService();

    @Test
    void wrapsSubmissionsForEveryLanguage() {
        Problem problem = problem();

        Map<String, String> markers = Map.of(
                "java", "public class Main {",
                "python", "def _main():",
                "cpp", "#include <bits/stdc++.h>",
                "c", "#include <stdio.h>",
                "javascript", "(function () {",
                "typescript", "function _canon(value: number): string",
                "go", "func main() {",
                "rust", "fn main() {");

        assertEquals(8, markers.size());
        for (Map.Entry<String, String> entry : markers.entrySet()) {
            String source = service.generateExecutableCode(
                    "// my solution", problem, entry.getKey());

            assertTrue(source.contains("// my solution"),
                    entry.getKey() + " dropped the user code");
            assertTrue(source.contains(entry.getValue()),
                    entry.getKey() + " generated the wrong harness");
            assertTrue(source.contains("PROGRESS|"),
                    entry.getKey() + " lacks the arena progress marker");
            assertTrue(source.contains("ALL_TESTS_PASSED"),
                    entry.getKey() + " lacks the success marker");
        }
    }

    @Test
    void missingLanguageDefaultsToJava() {
        Problem problem = problem();

        assertEquals(
                service.generateExecutableCode("// my solution", problem, "java"),
                service.generateExecutableCode("// my solution", problem));
    }

    @Test
    void languageIdsAreCaseInsensitiveAndAcceptAliases() {
        Problem problem = problem();

        assertEquals(
                service.generateExecutableCode("", problem, "python"),
                service.generateExecutableCode("", problem, "PY"));
        assertEquals(
                service.generateExecutableCode("", problem, "cpp"),
                service.generateExecutableCode("", problem, "C++"));
    }

    @Test
    void unknownLanguageIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem(), "kotlin"));

        assertTrue(error.getMessage().contains("Supported languages"));
    }

    @Test
    void missingMethodMetadataIsRejected() {
        Problem problem = problem();
        problem.setMethodName(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "python"));
    }

    @Test
    void missingTestCasesAreRejected() {
        Problem problem = problem();
        problem.setTestCases(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "java"));
    }

    @Test
    void unsupportedParameterTypesAreRejected() {
        Problem problem = problem();
        problem.setParameterTypes(List.of("long[]"));

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "rust"));
    }

    @Test
    void unsupportedReturnTypesAreRejected() {
        Problem problem = problem();
        problem.setReturnType("Map");

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "go"));
    }

    private static Problem problem() {
        Problem problem = new Problem();
        problem.setMethodName("solve");
        problem.setReturnType("int[]");
        problem.setParameterTypes(List.of("int[]", "int"));
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitKb(262144);

        Problem.TestCase testCase = new Problem.TestCase();
        testCase.setInputs(List.of("[2,7,11,15]", "9"));
        testCase.setExpectedOutput("[0,1]");
        testCase.setHidden(false);
        problem.setTestCases(List.of(testCase));
        return problem;
    }
}
