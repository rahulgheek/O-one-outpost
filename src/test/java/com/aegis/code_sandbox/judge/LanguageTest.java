package com.aegis.code_sandbox.judge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {

    @Test
    void registryMatchesTheProductContract() {
        assertEquals(8, Language.all().size());
        assertEquals("java", Language.JAVA.id());
        assertEquals("python", Language.PYTHON.id());
        assertEquals("cpp", Language.CPP.id());
        assertEquals("c", Language.C.id());
        assertEquals("javascript", Language.JAVASCRIPT.id());
        assertEquals("typescript", Language.TYPESCRIPT.id());
        assertEquals("go", Language.GO.id());
        assertEquals("rust", Language.RUST.id());

        assertEquals("java, python, cpp, c, javascript, typescript, go, rust",
                Language.supportedIds());
    }

    @Test
    void compilerIdsMatchTheProviderCatalogue() {
        assertEquals("openjdk-25", Language.JAVA.defaultCompiler());
        assertEquals("python-3.14", Language.PYTHON.defaultCompiler());
        assertEquals("g++-15", Language.CPP.defaultCompiler());
        assertEquals("gcc-15", Language.C.defaultCompiler());
        assertEquals("nodejs-24", Language.JAVASCRIPT.defaultCompiler());
        assertEquals("typescript-deno", Language.TYPESCRIPT.defaultCompiler());
        assertEquals("go-1.26", Language.GO.defaultCompiler());
        assertEquals("rust-1.93", Language.RUST.defaultCompiler());
    }

    @Test
    void onlyJavaIsAvailableOnTheDockerBackend() {
        for (Language language : Language.values()) {
            assertEquals(language == Language.JAVA, language.dockerSupported(),
                    language.id() + " has the wrong Docker capability flag");
        }
    }

    @Test
    void missingValueMeansJavaForBackwardsCompatibility() {
        assertEquals(Language.JAVA, Language.DEFAULT);
        assertEquals(Language.JAVA, Language.resolve(null));
        assertEquals(Language.JAVA, Language.resolve("  "));
        assertEquals(Language.JAVA, Language.resolve("JAVA"));
    }

    @Test
    void commonAliasesResolve() {
        assertEquals(Language.PYTHON, Language.resolve("py"));
        assertEquals(Language.PYTHON, Language.resolve("python3"));
        assertEquals(Language.CPP, Language.resolve("C++"));
        assertEquals(Language.C, Language.resolve("gcc"));
        assertEquals(Language.JAVASCRIPT, Language.resolve("node"));
        assertEquals(Language.TYPESCRIPT, Language.resolve("ts"));
        assertEquals(Language.GO, Language.resolve("golang"));
        assertEquals(Language.RUST, Language.resolve("rs"));
    }

    @Test
    void unknownLanguageIsRejectedWithTheSupportedList() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Language.resolve("kotlin"));

        assertTrue(error.getMessage().contains("kotlin"));
        assertTrue(error.getMessage().contains("java"));
        assertTrue(error.getMessage().contains("rust"));
        assertTrue(Language.find("kotlin").isEmpty());
    }

    @Test
    void compilerDiagnosticsAreClassifiedPerLanguage() {
        Map<Language, String> diagnostics = Map.of(
                Language.JAVA, "Main.java:9: error: cannot find symbol",
                Language.PYTHON, "  File \"main.py\", line 1\nSyntaxError: invalid syntax",
                Language.CPP, "main.cpp:3:5: error: 'x' was not declared in this scope",
                Language.C, "main.c:3:5: error: expected ';'",
                Language.GO, "code.go:5:2: undefined: foo",
                Language.RUST, "error[E0425]: cannot find value `x` in this scope",
                Language.JAVASCRIPT, "code.js:3\nthrow new Error('boom');",
                Language.TYPESCRIPT, "error: Expected ';' but found '}'");

        diagnostics.forEach((language, diagnostic) ->
                assertTrue(language.looksLikeCompilationError(diagnostic),
                        language.id() + " failed to recognise a compiler diagnostic"));
    }

    @Test
    void runtimeFailuresAreNotMistakenForCompilationErrors() {
        List<String> runtimeFailures = List.of(
                "Exception in thread \"main\" java.lang.NullPointerException",
                "Traceback (most recent call last):\nValueError: boom",
                "terminate called after throwing an instance of 'std::runtime_error'",
                "Segmentation fault",
                "panic: runtime error: index out of range",
                "thread 'main' panicked at src/main.rs:3:5",
                "Error: boom",
                "Error: boom\n    at main (file:///code.js:4:11)");

        for (Language language : Language.values()) {
            for (String failure : runtimeFailures) {
                assertFalse(language.looksLikeCompilationError(failure),
                        language.id() + " misclassified '" + failure + "' as a compile error");
            }
        }
    }

    @Test
    void memoryExhaustionIsClassifiedPerLanguage() {
        Map<Language, String> memoryFailures = Map.of(
                Language.JAVA, "java.lang.OutOfMemoryError: Java heap space",
                Language.PYTHON, "MemoryError",
                Language.CPP, "std::bad_alloc",
                Language.C, "out of memory",
                Language.GO, "runtime: out of memory",
                Language.RUST, "memory allocation of 1024 bytes failed",
                Language.JAVASCRIPT, "JavaScript heap out of memory",
                Language.TYPESCRIPT, "Array buffer allocation failed");

        memoryFailures.forEach((language, failure) -> {
            assertTrue(language.mentionsMemoryLimit(failure),
                    language.id() + " missed a memory failure");
            assertFalse(language.mentionsMemoryLimit("Segmentation fault"),
                    language.id() + " false positive");
        });
    }
}
