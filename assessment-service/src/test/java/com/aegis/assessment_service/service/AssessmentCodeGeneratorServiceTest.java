package com.aegis.assessment_service.service;

import com.aegis.assessment_service.entitiy.CompanyProblem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentCodeGeneratorServiceTest {

    private final AssessmentCodeGeneratorService service = new AssessmentCodeGeneratorService();

    @Test
    void wrapsSubmissionsForEveryLanguageWithPartialCreditProtocol() {
        CompanyProblem problem = problem();

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
            assertTrue(source.contains("ASSESSMENT_RESULT|"),
                    entry.getKey() + " lacks the assessment result marker");
            assertFalse(source.contains("PROGRESS|"),
                    entry.getKey() + " must not use the arena protocol");
        }
    }

    @Test
    void missingLanguageDefaultsToJava() {
        CompanyProblem problem = problem();

        assertEquals(
                service.generateExecutableCode("// my solution", problem, "java"),
                service.generateExecutableCode("// my solution", problem));
    }

    @Test
    void unknownLanguageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem(), "scala"));
    }

    @Test
    void unsupportedParameterTypesAreRejected() {
        CompanyProblem problem = problem();
        problem.setParameterTypes(List.of("boolean[]"));

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "c"));
    }

    @Test
    void missingTestCasesAreRejected() {
        CompanyProblem problem = problem();
        problem.setTestCases(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.generateExecutableCode("", problem, "javascript"));
    }

    private static CompanyProblem problem() {
        CompanyProblem problem = new CompanyProblem();
        problem.setCompanyId("company-1");
        problem.setMethodName("solve");
        problem.setReturnType("int[]");
        problem.setParameterTypes(List.of("int[]", "int"));
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitKb(262144);

        CompanyProblem.TestCase testCase = new CompanyProblem.TestCase();
        testCase.setInputs(List.of("[2,7,11,15]", "9"));
        testCase.setExpectedOutput("[0,1]");
        testCase.setHidden(false);
        problem.setTestCases(List.of(testCase));
        return problem;
    }
}
