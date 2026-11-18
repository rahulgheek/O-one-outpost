package com.aegis.judge.harness;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterCodeTest {

    private static final String METHOD = "solve";
    private static final String RETURN_TYPE = "int";
    private static final List<String> PARAMETERS = List.of("int[]", "int");

    @Test
    void resolvesEverySupportedLanguage() {
        Map<String, String> resolved = StarterCode.forProblem(
                null, null, METHOD, RETURN_TYPE, PARAMETERS);

        assertEquals(8, resolved.size());
        assertEquals(HarnessLanguage.supportedIds().split(", ").length, resolved.size());
        for (HarnessLanguage language : HarnessLanguage.values()) {
            assertTrue(resolved.containsKey(language.id()), language.id() + " missing");
            assertFalse(resolved.get(language.id()).isBlank(), language.id() + " blank");
            assertTrue(resolved.get(language.id()).contains(METHOD),
                    language.id() + " does not mention the method");
        }
    }

    @Test
    void explicitOverrideWinsOverEverythingElse() {
        Map<String, String> resolved = StarterCode.forProblem(
                Map.of("python", "# custom python"),
                "// legacy java",
                METHOD, RETURN_TYPE, PARAMETERS);

        assertEquals("# custom python", resolved.get("python"));
        assertEquals("// legacy java", resolved.get("java"));
    }

    @Test
    void legacyJavaStarterIsUsedForJavaOnly() {
        Map<String, String> resolved = StarterCode.forProblem(
                null, "class Solution { }", METHOD, RETURN_TYPE, PARAMETERS);

        assertEquals("class Solution { }", resolved.get("java"));
        assertFalse(resolved.get("python").contains("class Solution { }"));
    }

    @Test
    void blankOverridesFallBackToGeneratedStubs() {
        Map<String, String> resolved = StarterCode.forProblem(
                Map.of("go", "   ", "java", ""),
                "  ",
                METHOD, RETURN_TYPE, PARAMETERS);

        assertTrue(resolved.get("go").contains("func solve("));
        assertTrue(resolved.get("java").contains("public int solve("));
    }

    @Test
    void malformedSignaturesStillProduceAnEditableTemplate() {
        Map<String, String> resolved = StarterCode.forProblem(
                null, null, "2bad", "int", PARAMETERS);

        assertEquals(8, resolved.size());
        for (String code : resolved.values()) {
            assertFalse(code.isBlank());
        }
        assertTrue(resolved.get("java").contains("class Solution"));
        assertTrue(resolved.get("python").contains("class Solution"));
    }
}
