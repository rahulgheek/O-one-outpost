package com.aegis.judge.harness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessGeneratorTest {

    private static final HarnessSpec.Signature MIXED = new HarnessSpec.Signature(
            "describe", "String",
            List.of("int", "long", "double", "boolean", "String", "int[]"));
    private static final HarnessSpec.Spec MIXED_SPEC = new HarnessSpec.Spec(MIXED, List.of(
            new HarnessSpec.TestCase(
                    List.of("1", "2", "3.5", "true", "\"hi there\"", "[1,2]"),
                    "1|2|3.5|true|hithere|[1,2]")));

    private static final HarnessSpec.Signature ARRAY_RETURN = new HarnessSpec.Signature(
            "solve", "int[]", List.of("int[]", "int"));

    @Test
    void everyLanguageProducesArenaProtocol() {
        String solution = "// contestant solution";
        for (HarnessLanguage language : HarnessLanguage.values()) {
            String source = HarnessGenerator.generate(language, solution, MIXED_SPEC,
                    HarnessGenerator.Protocol.ARENA);

            assertTrue(source.contains(solution), language.id() + " dropped the user code");
            assertEquals(1, occurrences(source, "PROGRESS|"),
                    language.id() + " must emit PROGRESS exactly once");
            assertTrue(source.contains("ALL_TESTS_PASSED"), language.id() + " lacks the success marker");
            assertFalse(source.contains("ASSESSMENT_RESULT"), language.id() + " mixed protocols");
        }
    }

    @Test
    void everyLanguageProducesAssessmentProtocol() {
        for (HarnessLanguage language : HarnessLanguage.values()) {
            String source = HarnessGenerator.generate(language, "// contestant solution", MIXED_SPEC,
                    HarnessGenerator.Protocol.ASSESSMENT);

            assertTrue(source.contains("ASSESSMENT_RESULT|"),
                    language.id() + " lacks the assessment marker");
            assertFalse(source.contains("PROGRESS|"), language.id() + " mixed protocols");
        }
    }

    @Test
    void harnessesCallTheDeclaredMethod() {
        for (HarnessLanguage language : HarnessLanguage.values()) {
            String source = HarnessGenerator.generate(language, "", MIXED_SPEC,
                    HarnessGenerator.Protocol.ARENA);

            assertTrue(source.contains("describe"), language.id() + " never calls the method");
        }
    }

    @Test
    void cUsesReturnSizeForArrayResults() {
        HarnessSpec.Spec spec = new HarnessSpec.Spec(ARRAY_RETURN, List.of(
                new HarnessSpec.TestCase(List.of("[2,7,11,15]", "9"), "[0,1]")));

        String source = HarnessGenerator.generate(HarnessLanguage.C, "", spec,
                HarnessGenerator.Protocol.ARENA);

        assertTrue(source.contains("int returnSize = 0;"));
        assertTrue(source.contains("&returnSize"));
        assertTrue(source.contains("judge_fmt_int_array(actual, returnSize)"));
    }

    @Test
    void expectedDoublesAreCanonicalisedInsideTheHarness() {
        HarnessSpec.Signature signature = new HarnessSpec.Signature("half", "double", List.of("double"));
        HarnessSpec.Spec spec = new HarnessSpec.Spec(signature, List.of(
                new HarnessSpec.TestCase(List.of("5.000000"), "2.500000")));

        for (HarnessLanguage language : HarnessLanguage.values()) {
            String source = HarnessGenerator.generate(language, "", spec,
                    HarnessGenerator.Protocol.ARENA);
            assertTrue(source.contains("\"2.5\""),
                    language.id() + " did not canonicalise the expected double");
        }
    }

    @Test
    void starterCodeMatchesEachLanguage() {
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.JAVA, MIXED)
                .contains("public String describe(int arg0, long arg1, double arg2, boolean arg3, "
                        + "String arg4, int[] arg5)"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.PYTHON, MIXED)
                .contains("def describe(self, arg0, arg1, arg2, arg3, arg4, arg5):"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.CPP, MIXED)
                .contains("string describe(int arg0, long long arg1"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.C, MIXED)
                .contains("char* describe(int arg0, long long arg1"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.JAVASCRIPT, MIXED)
                .contains("describe(arg0, arg1"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.TYPESCRIPT, MIXED)
                .contains("describe(arg0: number, arg1: number"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.GO, MIXED)
                .contains("func describe(arg0 int, arg1 int64"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.RUST, MIXED)
                .contains("pub fn describe(arg0: i32, arg1: i64"));
    }

    @Test
    void arrayReturningStarterCodeIsValidPerLanguage() {
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.C, ARRAY_RETURN)
                .contains("*returnSize = 0;"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.C, ARRAY_RETURN)
                .contains("int* solve(int* arg0, int arg0Size, int arg1, int* returnSize)"));
        assertTrue(HarnessGenerator.starterCode(HarnessLanguage.RUST, ARRAY_RETURN)
                .contains("-> Vec<i32>"));
    }

    @Test
    void zeroParameterMethodsAreSupported() {
        HarnessSpec.Signature signature = new HarnessSpec.Signature("now", "long", List.of());
        HarnessSpec.Spec spec = new HarnessSpec.Spec(signature, List.of(
                new HarnessSpec.TestCase(List.of(), "0")));

        for (HarnessLanguage language : HarnessLanguage.values()) {
            String source = HarnessGenerator.generate(language, "", spec,
                    HarnessGenerator.Protocol.ARENA);
            assertTrue(source.contains("now"), language.id() + " could not render a zero-arg method");
        }
    }

    @Test
    void unsupportedParameterArraysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSpec.Signature("join", "String", List.of("String[]")));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSpec.Signature("sum", "long", List.of("long[]")));
    }

    @Test
    void identifiersAndTypesAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSpec.Signature("2bad", "int", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSpec.Signature("ok", "BigDecimal", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSpec.Signature("ok", "int", List.of(" ")));
    }

    @Test
    void literalParsingAndCanonicalisation() {
        assertEquals(42L, HarnessSpec.parseLiteral(" 42 ", HarnessSpec.Kind.INT));
        assertEquals(3.5, HarnessSpec.parseLiteral("3.50", HarnessSpec.Kind.DOUBLE));
        assertEquals(Boolean.TRUE, HarnessSpec.parseLiteral("TRUE", HarnessSpec.Kind.BOOLEAN));
        assertEquals("hi\nthere", HarnessSpec.parseLiteral("\"hi\\nthere\"", HarnessSpec.Kind.STRING));
        assertEquals(List.of(1L, 2L), HarnessSpec.parseLiteral("[1, 2]", HarnessSpec.Kind.INT_ARRAY));
        assertEquals(List.of(1L, 2L), HarnessSpec.parseLiteral("[1 2]", HarnessSpec.Kind.INT_ARRAY));
        assertEquals(List.of("a b", "c"), HarnessSpec.parseLiteral("[\"a b\", \"c\"]",
                HarnessSpec.Kind.STRING_ARRAY));

        assertEquals("2", HarnessSpec.formatDouble(2.0));
        assertEquals("2.5", HarnessSpec.formatDouble(2.500000));
        assertEquals("0", HarnessSpec.formatDouble(-0.0));
        assertEquals("2", HarnessSpec.expectedFor(HarnessSpec.Kind.DOUBLE, "2.000000"));
        assertEquals("[1,2.5]", HarnessSpec.expectedFor(HarnessSpec.Kind.DOUBLE_ARRAY,
                "[1.0, 2.500000]"));
        assertEquals("[1,2]", HarnessSpec.expectedFor(HarnessSpec.Kind.INT_ARRAY, "[1, 2]"));
    }

    @Test
    void stringLiteralsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", HarnessSpec.quoted("a\"b\\c\nd"));
        assertEquals("\"\\u0001\"", HarnessSpec.quoted("\u0001"));
    }

    @Test
    void invalidLiteralsAreReported() {
        assertThrows(IllegalArgumentException.class,
                () -> HarnessSpec.parseLiteral("not-a-number", HarnessSpec.Kind.INT));
        assertThrows(IllegalArgumentException.class,
                () -> HarnessSpec.parseLiteral("1,2", HarnessSpec.Kind.INT_ARRAY));
        assertThrows(IllegalArgumentException.class,
                () -> HarnessSpec.parseLiteral("yes", HarnessSpec.Kind.BOOLEAN));
    }

    @Test
    void languageAliasesResolveToTheSameJudgeIds() {
        assertEquals(HarnessLanguage.JAVA, HarnessLanguage.resolve(null));
        assertEquals(HarnessLanguage.JAVA, HarnessLanguage.resolve(" "));
        assertEquals(HarnessLanguage.PYTHON, HarnessLanguage.resolve("py"));
        assertEquals(HarnessLanguage.CPP, HarnessLanguage.resolve("c++"));
        assertEquals(HarnessLanguage.JAVASCRIPT, HarnessLanguage.resolve("js"));
        assertEquals(HarnessLanguage.TYPESCRIPT, HarnessLanguage.resolve("deno"));
        assertEquals(HarnessLanguage.GO, HarnessLanguage.resolve("golang"));
        assertEquals(HarnessLanguage.RUST, HarnessLanguage.resolve("rs"));
        assertThrows(IllegalArgumentException.class, () -> HarnessLanguage.resolve("kotlin"));
    }

    @Test
    void everyLanguageIsRegisteredExactlyOnce() {
        assertEquals(8, HarnessLanguage.values().length);
        assertEquals("java, python, cpp, c, javascript, typescript, go, rust",
                HarnessLanguage.supportedIds());
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
